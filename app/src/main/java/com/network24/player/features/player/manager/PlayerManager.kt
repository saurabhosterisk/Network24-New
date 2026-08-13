package com.network24.player.features.player.manager


import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

import com.network24.player.core.net.StreamDataSourceFactory
import com.network24.player.features.live.models.LiveChannel
import com.network24.player.features.player.state.PlayerState

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import java.lang.ref.WeakReference



@OptIn(UnstableApi::class)
object PlayerManager {



    private var exoPlayer: ExoPlayer? = null


    private var currentUrl: String? = null


    private var lastStreamUrl: String? = null


    private var currentPlayerView: PlayerView? = null

    private var playbackSessionId = 0

    private var streamGeneration = 0


    /**
     * WeakReference prevents Activity memory leak
     */
    private var ownerActivityRef:
            WeakReference<Activity>? = null





    private var lifecycleCallbacksRegistered =
        false




    private var preservePlaybackThroughFullscreenReturn =
        false





    // Diagnostics

    private var rebufferCount = 0

    private var bufferingStartedAtMs = 0L

    private var totalBufferingMs = 0L

    private var hasStartedPlaying = false

    private var bufferingSessionActive = false

    private var playbackActuallyStarted = false

    private var lastError:
            PlaybackException? = null


    enum class StreamErrorType {
        NONE,
        NETWORK,
        SOURCE,
        UNKNOWN
    }

    private var streamErrorType =
        StreamErrorType.NONE




    private var lastPlaybackState =
        Player.STATE_IDLE






    // Recovery

    private var liveRecoveryJob:
            Job? = null


    private var liveRecoveryStartedAtMs =
        0L


    private var liveRecoveryAttempt =
        0

    private var recoveryStatusListener:
            ((Int) -> Unit)? = null



    private val recoveryFailedListeners =
        mutableSetOf<() -> Unit>()





    private val playerScope =
        CoroutineScope(
            SupervisorJob()
                    +
                    Dispatchers.Main.immediate
        )





    private const val LIVE_RECOVERY_WINDOW_MS =
        30000L





    private val loadControl =

        DefaultLoadControl.Builder()

            .setBufferDurationsMs(
                20000,
                60000,
                3000,
                6000
            )

            .build()







    private fun ensureActivityLifecycleCallbacks(
        context: Context
    ) {


        if (
            lifecycleCallbacksRegistered
        ) return




        val application =
            context.applicationContext
                    as? Application
                ?: return





        application.registerActivityLifecycleCallbacks(

            object :
                Application.ActivityLifecycleCallbacks {



                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: android.os.Bundle?
                ) = Unit






                override fun onActivityStarted(
                    activity: Activity
                ) = Unit






                override fun onActivityResumed(
                    activity: Activity
                ) {


                    if (

                        activity.javaClass.name.endsWith(
                            "features.live.activity.EpgChannelListActivity"
                        )

                        &&

                        !currentUrl.isNullOrBlank()

                    ) {


                        runCatching {


                            attachFromEpgReflection(
                                activity
                            )

                        }
                    }
                }






                override fun onActivityPaused(
                    activity: Activity
                ) = Unit






                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: android.os.Bundle
                ) = Unit






                override fun onActivityStopped(
                    activity: Activity
                ) {


                    val owner =
                        ownerActivityRef
                            ?.get()



                    if (
                        owner === activity
                    ) {

                        exoPlayer?.pause()

                        ownerActivityRef =
                            null
                    }
                }






                override fun onActivityDestroyed(
                    activity: Activity
                ) = Unit
            }
        )



        lifecycleCallbacksRegistered =
            true
    }

    private fun attachFromEpgReflection(
        activity: Activity
    ) {


        try {


            val bindingField =
                activity.javaClass
                    .getDeclaredField(
                        "binding"
                    )
                    .apply {
                        isAccessible = true
                    }



            val binding =
                bindingField.get(
                    activity
                )



            val playerField =
                binding.javaClass
                    .getDeclaredField(
                        "playerView"
                    )
                    .apply {
                        isAccessible = true
                    }




            val playerView =
                playerField.get(
                    binding
                )
                        as? PlayerView
                    ?: return




            attach(
                activity,
                playerView
            )



        } catch (_: Exception) {


        }
    }









    fun getPlayer(
        context: Context
    ): ExoPlayer {


        if (
            exoPlayer == null
        ) {


            exoPlayer =

                ExoPlayer.Builder(

                    context.applicationContext,

                    StreamDataSourceFactory
                        .createRenderersFactory(
                            context
                        )

                )

                    .setMediaSourceFactory(
                        StreamDataSourceFactory
                            .createMediaSourceFactory()
                    )

                    .setLoadControl(
                        loadControl
                    )

                    .build()

                    .apply {



                        playWhenReady =
                            true





                        addListener(

                            object : Player.Listener {



                                override fun onPlaybackStateChanged(
                                    playbackState: Int
                                ) {

                                    val wasBuffering =
                                        lastPlaybackState == Player.STATE_BUFFERING


                                    /*
                                       Count buffering only after
                                       playback has started once
                                    */
                                    if (
                                        playbackState == Player.STATE_BUFFERING &&
                                        !wasBuffering &&
                                        playbackActuallyStarted
                                    ) {

                                        rebufferCount++

                                        bufferingStartedAtMs =
                                            System.currentTimeMillis()

                                        bufferingSessionActive = true
                                    }


                                    /*
                                       Buffering finished
                                    */
                                    if (
                                        wasBuffering &&
                                        playbackState != Player.STATE_BUFFERING &&
                                        bufferingSessionActive
                                    ) {


                                        val duration =
                                            System.currentTimeMillis()
                                        -
                                        bufferingStartedAtMs


                                        if (
                                            duration > 0L &&
                                            duration <= 300000L
                                        ) {

                                            totalBufferingMs += duration
                                        }


                                        bufferingStartedAtMs = 0L

                                        bufferingSessionActive = false
                                    }



                                    if (
                                        playbackState == Player.STATE_READY &&
                                        exoPlayer?.isPlaying == true
                                    ) {

                                        hasStartedPlaying = true
                                        playbackActuallyStarted = true
                                    }


                                    lastPlaybackState =
                                        playbackState
                                }






                                override fun onPlayerError(
                                    error: PlaybackException
                                ) {

                                    val errorSession = playbackSessionId

                                    lastError =
                                        error


                                    streamErrorType =
                                        when(error.errorCode) {

                                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                                                StreamErrorType.NETWORK


                                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                                            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                                                StreamErrorType.SOURCE


                                            else ->
                                                StreamErrorType.UNKNOWN
                                        }


                                    playerScope.launch {

                                        delay(500)

                                        if (errorSession != playbackSessionId) {
                                            return@launch
                                        }

                                        scheduleLiveChannelRecoveryIfNeeded()

                                    }
                                }
                            }
                        )
                    }


        }



        return exoPlayer!!
    }



    fun attach(
        context: Context,
        playerView: PlayerView
    ) {


        ensureActivityLifecycleCallbacks(
            context
        )



        if (
            context is Activity
        ) {


            ownerActivityRef =
                WeakReference(
                    context
                )
        }





        val player =
            getPlayer(
                context
            )



        if (
            currentPlayerView !== playerView
        ) {


            currentPlayerView?.player =
                null



            playerView.player =
                player



            currentPlayerView =
                playerView
        }
    }









    fun detach(
        playerView: PlayerView
    ) {


        if (
            currentPlayerView === playerView
        ) {


            playerView.player =
                null



            currentPlayerView =
                null
        }
    }









    fun moveTo(
        context: Context,
        playerView: PlayerView
    ) {


        attach(
            context,
            playerView
        )
    }









    fun play(
        context: Context,
        playerView: PlayerView,
        streamUrl: String
    ) {

        playbackSessionId++


        cancelLiveRecovery()





        val player =
            getPlayer(
                context
            )



        attach(
            context,
            playerView
        )





        if (
            currentUrl != streamUrl
        ) {

            streamGeneration++

            resetDiagnostics()



            player.stop()



            player.clearMediaItems()



            currentUrl =
                streamUrl



            lastStreamUrl =
                streamUrl





            player.setMediaItem(

                MediaItem.fromUri(
                    streamUrl
                )
            )



            player.prepare()



            player.play()
        }
        else {


            player.play()
        }
    }









    fun retryCurrent() {

        playbackSessionId++
        cancelLiveRecovery()



        val player =
            exoPlayer
                ?: return





        if (
            currentUrl.isNullOrBlank()
        ) return





        player.stop()



        player.clearMediaItems()



        player.setMediaItem(

            MediaItem.fromUri(
                currentUrl!!
            )
        )



        player.prepare()



        player.play()
    }









    fun stop() {


        cancelLiveRecovery()



        exoPlayer?.stop()



        exoPlayer?.clearMediaItems()



        currentUrl =
            null
    }









    fun pause() {


        exoPlayer?.pause()
    }









    fun resume() {


        exoPlayer?.play()
    }









    fun isPlaying(): Boolean {


        return exoPlayer?.isPlaying
            ?: false
    }









    fun getExoPlayerOrNull():

            ExoPlayer? {


        return exoPlayer
    }



    private fun scheduleLiveChannelRecoveryIfNeeded() {

        val recoverySession = playbackSessionId

        val activity =
            ownerActivityRef
                ?.get()
                ?: return



        if (
            currentUrl.isNullOrBlank()
            ||
            exoPlayer == null
        ) return


        val elapsed =
            System.currentTimeMillis() -
                    liveRecoveryStartedAtMs

        if (
            elapsed >= LIVE_RECOVERY_WINDOW_MS
        ) {

            if (
                recoverySession != playbackSessionId
            ) {
                return
            }


            recoveryFailedListeners.forEach { listener ->
                listener.invoke()
            }

            cancelLiveRecovery()

            return
        }




        if (
            liveRecoveryJob?.isActive == true
        ) return





        val delayTime =
            when(liveRecoveryAttempt) {

                0 -> 3000L

                1 -> 5000L

                else -> 7000L
            }





        liveRecoveryAttempt++


        recoveryStatusListener?.invoke(
            liveRecoveryAttempt
        )


        val failedUrl =
            currentUrl



        liveRecoveryJob =
            playerScope.launch {


                delay(delayTime)


                if (
                    recoverySession != playbackSessionId
                ) {
                    return@launch
                }


                if (
                    failedUrl == null
                ) {
                    return@launch
                }



                android.util.Log.d(
                    "N24_RECOVERY",
                    "Retry attempt $liveRecoveryAttempt"
                )



                exoPlayer?.apply {


                    stop()


                    clearMediaItems()



                    setMediaItem(
                        MediaItem.fromUri(
                            failedUrl
                        )
                    )


                    prepare()


                    play()
                }



                scheduleLiveChannelRecoveryIfNeeded()
            }
    }








    private fun cancelLiveRecovery() {


        liveRecoveryJob
            ?.cancel()



        liveRecoveryJob =
            null



        liveRecoveryStartedAtMs =
            0L



        liveRecoveryAttempt =
            0
    }









    private fun resetDiagnostics() {


        rebufferCount =
            0



        bufferingStartedAtMs =
            0L



        totalBufferingMs =
            0L



        lastError =
            null

        streamErrorType = StreamErrorType.NONE

        lastPlaybackState =
            Player.STATE_IDLE

        hasStartedPlaying = false

        bufferingSessionActive = false

        playbackActuallyStarted = false

    }









    fun release() {


        cancelLiveRecovery()



        if (
            !currentUrl.isNullOrBlank()
        ) {


            lastStreamUrl =
                currentUrl
        }






        currentPlayerView?.player =
            null



        currentPlayerView =
            null






        exoPlayer?.stop()



        exoPlayer?.clearMediaItems()



        exoPlayer?.release()



        exoPlayer =
            null






        currentUrl =
            null



        resetDiagnostics()



        ownerActivityRef =
            null
    }



    fun setRecoveryFailedListener(
        listener: (() -> Unit)?
    ) {
        recoveryFailedListeners.clear()

        if(listener != null) {
            recoveryFailedListeners.add(listener)
        }
    }

    fun setRecoveryStatusListener(
        listener: ((Int) -> Unit)?
    ) {
        recoveryStatusListener = listener
    }







    fun getCurrentUrl(): String? {


        return currentUrl
    }









    fun getCurrentUrlOrEmpty(): String {


        return currentUrl
            ?: ""
    }









    fun getRebufferCount(): Int {


        return rebufferCount
    }


    fun hasEverStartedPlayback(): Boolean {
        return playbackActuallyStarted
    }


    fun isPlaybackStarted(): Boolean {

        return playbackActuallyStarted
    }





    fun getTotalBufferingMs(): Long {

        return totalBufferingMs
            .coerceIn(
                0L,
                3600000L
            )
    }









    fun getLastError(): PlaybackException? {


        return lastError
    }


    fun getStreamErrorType(): StreamErrorType {
        return streamErrorType
    }








    fun getLastErrorMessage(): String {


        return lastError
            ?.message
            ?: ""
    }









    fun preservePlaybackForFullscreenReturn() {


        preservePlaybackThroughFullscreenReturn =
            true
    }









    fun shouldPreservePlayback(): Boolean {


        return preservePlaybackThroughFullscreenReturn
    }









    fun clearFullscreenPreserveFlag() {


        preservePlaybackThroughFullscreenReturn =
            false
    }









    fun getLastStreamUrl(): String? {


        return lastStreamUrl
    }









    fun isRecoveryRunning(): Boolean {


        return liveRecoveryJob
            ?.isActive
            ?: false
    }


    fun getRecoveryAttempt(): Int {
        return liveRecoveryAttempt
    }








    private fun releasePlayerOnly() {


        currentPlayerView?.player =
            null



        currentPlayerView =
            null



        exoPlayer?.stop()



        exoPlayer?.clearMediaItems()



        exoPlayer?.release()



        exoPlayer =
            null
    }

}

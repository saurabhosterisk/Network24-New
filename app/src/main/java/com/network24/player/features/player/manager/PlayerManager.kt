package com.network24.player.features.player.manager

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.network24.player.core.net.StreamDataSourceFactory

@OptIn(UnstableApi::class)
object PlayerManager {

    private var exoPlayer: ExoPlayer? = null
    private var currentUrl: String? = null
    private var lastStreamUrl: String? = null
    private var currentPlayerView: PlayerView? = null
    private var ownerActivity: Activity? = null
    private var lifecycleCallbacksRegistered = false

    private var rebufferCount = 0
    private var bufferingStartedAtMs = 0L
    private var totalBufferingMs = 0L
    private var lastError: PlaybackException? = null
    private var lastPlaybackState = Player.STATE_IDLE

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(20_000, 60_000, 3_000, 6_000)
        .build()

    private fun ensureActivityLifecycleCallbacks(context: Context) {
        if (lifecycleCallbacksRegistered) return
        val application = context.applicationContext as? Application ?: return

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) {
                if (ownerActivity === activity) {
                    release()
                    ownerActivity = null
                }
            }
        })
        lifecycleCallbacksRegistered = true
    }

    fun getPlayer(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            val player = ExoPlayer.Builder(
                context.applicationContext,
                StreamDataSourceFactory.createRenderersFactory(context)
            )
                .setLoadControl(loadControl)
                .setMediaSourceFactory(StreamDataSourceFactory.createMediaSourceFactory())
                .build()
                .apply {
                    playWhenReady = true
                    trackSelectionParameters = trackSelectionParameters
                        .buildUpon()
                        .setSelectUndeterminedTextLanguage(true)
                        .build()

                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            val wasBuffering = lastPlaybackState == Player.STATE_BUFFERING
                            if (playbackState == Player.STATE_BUFFERING && !wasBuffering) {
                                rebufferCount++
                                bufferingStartedAtMs = System.currentTimeMillis()
                            } else if (wasBuffering && playbackState != Player.STATE_BUFFERING) {
                                if (bufferingStartedAtMs > 0L) {
                                    totalBufferingMs += System.currentTimeMillis() - bufferingStartedAtMs
                                    bufferingStartedAtMs = 0L
                                }
                            }
                            lastPlaybackState = playbackState
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            lastError = error
                            error.printStackTrace()
                        }
                    })
                }
            exoPlayer = player
        }
        return exoPlayer!!
    }

    fun attach(context: Context, playerView: PlayerView) {
        ensureActivityLifecycleCallbacks(context)
        if (context is Activity) ownerActivity = context

        val player = getPlayer(context)
        if (currentPlayerView !== playerView) {
            currentPlayerView?.player = null
            playerView.player = player
            currentPlayerView = playerView
        }

        // If the previous hosting activity left the screen and released the
        // player, reconnect the same channel when the activity comes back.
        if (currentUrl == null && !lastStreamUrl.isNullOrBlank()) {
            currentUrl = lastStreamUrl
            resetDiagnostics()
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(MediaItem.fromUri(lastStreamUrl!!))
            player.prepare()
            player.play()
        }
    }

    fun detach(playerView: PlayerView) {
        if (currentPlayerView === playerView) {
            playerView.player = null
            currentPlayerView = null
        }
    }

    fun play(context: Context, playerView: PlayerView, streamUrl: String) {
        if (currentUrl != null && currentUrl != streamUrl) {
            releaseCurrentStreamForSwitch()
        }

        val player = getPlayer(context)
        attach(context, playerView)

        if (currentUrl == streamUrl) {
            lastStreamUrl = streamUrl
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
            player.play()
            return
        }

        currentUrl = streamUrl
        lastStreamUrl = streamUrl
        resetDiagnostics()
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.prepare()
        player.play()
    }

    private fun releaseCurrentStreamForSwitch() {
        currentPlayerView?.player = null
        currentPlayerView = null
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        exoPlayer?.release()
        exoPlayer = null
        currentUrl = null
        lastStreamUrl = null
        resetDiagnostics()
    }

    fun retryCurrent() {
        val player = exoPlayer ?: return
        if (currentUrl.isNullOrBlank()) return
        player.prepare()
        player.play()
    }

    fun pause() { exoPlayer?.pause() }
    fun resume() { exoPlayer?.play() }

    fun stop() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        currentUrl = null
    }

    fun release() {
        // Keep the last URL only so a screen that comes back can reconnect.
        // The actual ExoPlayer, media items and HTTP/HLS connection are released.
        if (!currentUrl.isNullOrBlank()) lastStreamUrl = currentUrl
        currentPlayerView?.player = null
        currentPlayerView = null
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        exoPlayer?.release()
        exoPlayer = null
        currentUrl = null
        resetDiagnostics()
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying ?: false
    fun getCurrentUrl(): String? = currentUrl

    fun moveTo(context: Context, playerView: PlayerView) {
        attach(context, playerView)
    }

    fun getExoPlayerOrNull(): ExoPlayer? = exoPlayer
    fun getCurrentUrlOrEmpty(): String = currentUrl ?: ""
    fun getRebufferCount(): Int = rebufferCount

    fun getTotalBufferingMs(): Long {
        return if (bufferingStartedAtMs > 0L) {
            totalBufferingMs + (System.currentTimeMillis() - bufferingStartedAtMs)
        } else {
            totalBufferingMs
        }
    }

    fun getLastError(): PlaybackException? = lastError

    private fun resetDiagnostics() {
        rebufferCount = 0
        bufferingStartedAtMs = 0L
        totalBufferingMs = 0L
        lastError = null
        lastPlaybackState = Player.STATE_IDLE
    }
}

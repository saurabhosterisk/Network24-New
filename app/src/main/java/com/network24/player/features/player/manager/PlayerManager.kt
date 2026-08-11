package com.network24.player.features.player.manager

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.network24.player.core.net.StreamDataSourceFactory
import com.network24.player.features.live.models.LiveChannel
import com.network24.player.features.player.activity.PlayerActivity
import com.network24.player.features.player.state.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
object PlayerManager {

    private var exoPlayer: ExoPlayer? = null
    private var currentUrl: String? = null
    private var lastStreamUrl: String? = null
    private var currentPlayerView: PlayerView? = null
    private var ownerActivity: Activity? = null
    private var lifecycleCallbacksRegistered = false
    private var preservePlaybackThroughFullscreenReturn = false

    private var rebufferCount = 0
    private var bufferingStartedAtMs = 0L
    private var totalBufferingMs = 0L
    private var lastError: PlaybackException? = null
    private var lastPlaybackState = Player.STATE_IDLE

    private var liveRecoveryJob: Job? = null
    private var liveRecoveryStartedAtMs = 0L
    private var liveRecoveryAttempt = 0
    private const val LIVE_RECOVERY_WINDOW_MS = 30_000L

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
                if (activity.javaClass.name.endsWith("features.player.activity.PlayerActivity") && ownerActivity !== activity) {
                    restoreEpgHostFocus(ownerActivity)
                    return
                }
                if (ownerActivity === activity) {
                    cancelLiveRecovery()
                    release()
                    ownerActivity = null
                }
            }
        })
        lifecycleCallbacksRegistered = true
    }

    private fun restoreEpgHostFocus(host: Activity?) {
        val epgHost = host ?: return
        if (!epgHost.javaClass.name.endsWith("features.live.activity.EpgChannelListActivity")) return
        epgHost.window.decorView.postDelayed({
            runCatching {
                val restore = epgHost.javaClass.getDeclaredMethod("restorePendingFocus").apply { isAccessible = true }
                restore.invoke(epgHost)
            }
        }, 120L)
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
                            scheduleLiveChannelRecoveryIfNeeded()
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
        // Live With EPG uses the same preview player. A second OK/click on the
        // already-playing channel means "open fullscreen", matching ChannelListActivity.
        if (currentUrl == streamUrl && context is Activity && context.javaClass.name.endsWith("features.live.activity.EpgChannelListActivity")) {
            openEpgFullscreen(context, streamUrl)
            return
        }

        cancelLiveRecovery()

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

    private fun openEpgFullscreen(context: Activity, streamUrl: String) {
        try {
            val host = context
            val channelsField = host.javaClass.getDeclaredField("channels").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val channels = channelsField.get(host) as? List<LiveChannel> ?: return

            val streamId = streamUrl.substringAfterLast('/').substringBefore('.').toIntOrNull() ?: return
            val position = channels.indexOfFirst { it.stream_id == streamId }
            if (position < 0) return

            PlayerState.channels.clear()
            PlayerState.channels.addAll(channels)
            PlayerState.currentPosition = position

            runCatching {
                host.javaClass.getDeclaredField("pendingFocusChannelId").apply { isAccessible = true }.set(host, streamId)
            }

            preservePlaybackForFullscreenReturn()
            host.startActivity(Intent(host, PlayerActivity::class.java))
        } catch (_: Exception) {
            // If reflection cannot resolve the EPG host, keep normal preview playback.
        }
    }

    private fun releaseCurrentStreamForSwitch() {
        cancelLiveRecovery()
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
        cancelLiveRecovery()
        val player = exoPlayer ?: return
        if (currentUrl.isNullOrBlank()) return
        player.prepare()
        player.play()
    }

    fun pause() {
        if (preservePlaybackThroughFullscreenReturn) {
            preservePlaybackThroughFullscreenReturn = false
            return
        }
        exoPlayer?.pause()
    }

    fun preservePlaybackForFullscreenReturn() {
        preservePlaybackThroughFullscreenReturn = true
    }

    fun resume() { exoPlayer?.play() }

    fun stop() {
        cancelLiveRecovery()
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        currentUrl = null
    }

    fun release() {
        cancelLiveRecovery()
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

    private fun scheduleLiveChannelRecoveryIfNeeded() {
        val activity = ownerActivity ?: return
        if (!activity.javaClass.name.endsWith("features.live.activity.ChannelListActivity")) return
        if (currentUrl.isNullOrBlank() || exoPlayer == null) return

        if (liveRecoveryStartedAtMs == 0L) {
            liveRecoveryStartedAtMs = System.currentTimeMillis()
            liveRecoveryAttempt = 0
        }

        val elapsed = System.currentTimeMillis() - liveRecoveryStartedAtMs
        if (elapsed >= LIVE_RECOVERY_WINDOW_MS) return

        liveRecoveryAttempt++
        val remaining = LIVE_RECOVERY_WINDOW_MS - elapsed
        val retryDelay = when (liveRecoveryAttempt) {
            1 -> 2_000L
            2 -> 3_000L
            3 -> 5_000L
            else -> 7_000L
        }.coerceAtMost(remaining)

        liveRecoveryJob?.cancel()
        val failedPlayer = exoPlayer
        val failedUrl = currentUrl
        liveRecoveryJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            delay(retryDelay)

            if (ownerActivity !== activity) return@launch
            if (exoPlayer !== failedPlayer) return@launch
            if (currentUrl != failedUrl) return@launch
            if (currentUrl.isNullOrBlank()) return@launch

            val nowElapsed = System.currentTimeMillis() - liveRecoveryStartedAtMs
            if (nowElapsed >= LIVE_RECOVERY_WINDOW_MS) return@launch

            try {
                val player = exoPlayer ?: return@launch
                player.prepare()
                player.play()
            } catch (_: Exception) {
                // A subsequent ExoPlayer error will schedule the next recovery attempt.
            }
        }
    }

    private fun cancelLiveRecovery() {
        liveRecoveryJob?.cancel()
        liveRecoveryJob = null
        liveRecoveryStartedAtMs = 0L
        liveRecoveryAttempt = 0
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

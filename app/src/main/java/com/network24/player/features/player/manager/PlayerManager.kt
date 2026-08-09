package com.network24.player.features.player.manager

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.datasource.DataSource
import com.network24.player.core.net.CountingDataSource

@OptIn(UnstableApi::class)
object PlayerManager {

    private var exoPlayer: ExoPlayer? = null
    private var currentUrl: String? = null
    private var currentPlayerView: PlayerView? = null

    // Session diagnostics. These are reset whenever a new stream starts.
    private var rebufferCount = 0
    private var bufferingStartedAtMs = 0L
    private var totalBufferingMs = 0L
    private var lastError: PlaybackException? = null
    private var lastPlaybackState = Player.STATE_IDLE

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            15000,
            50000,
            1000,
            2000
        ).build()

    fun getPlayer(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("N24PlayerPlayer")
                .setAllowCrossProtocolRedirects(true)

            val countingFactory = DataSource.Factory {
                CountingDataSource(httpFactory.createDataSource())
            }

            val mediaSourceFactory = DefaultMediaSourceFactory(countingFactory)
            val renderersFactory = DefaultRenderersFactory(context.applicationContext).apply {
                setEnableDecoderFallback(true)
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            }

            exoPlayer = ExoPlayer.Builder(
                context.applicationContext,
                renderersFactory
            )
                .setLoadControl(loadControl)
                .setMediaSourceFactory(mediaSourceFactory)
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
        }
        return exoPlayer!!
    }

    fun attach(context: Context, playerView: PlayerView) {
        val player = getPlayer(context)
        if (currentPlayerView === playerView) return
        currentPlayerView?.player = null
        playerView.player = player
        currentPlayerView = playerView
    }

    fun detach(playerView: PlayerView) {
        if (currentPlayerView === playerView) {
            playerView.player = null
            currentPlayerView = null
        }
    }

    fun play(context: Context, playerView: PlayerView, streamUrl: String) {
        val player = getPlayer(context)
        attach(context, playerView)

        if (currentUrl == streamUrl) {
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
            player.play()
            return
        }

        currentUrl = streamUrl
        resetDiagnostics()
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.prepare()
        player.play()
    }

    fun pause() { exoPlayer?.pause() }
    fun resume() { exoPlayer?.play() }

    fun stop() {
        exoPlayer?.stop()
        currentUrl = null
    }

    fun release() {
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

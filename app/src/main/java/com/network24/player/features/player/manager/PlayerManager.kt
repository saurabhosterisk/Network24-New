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

    // 1. Ek powerful LoadControl banayein Live IPTV ke liye (FAST ZAPPING MODE)
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            15000, // Min Buffer: 15 seconds (Memory kam use karega, app fast rahegi)
            50000, // Max Buffer: 50 seconds tak pre-load karega (Stability ke liye)
            1000,  // 🔥 FAST START: Sirf 1 second ka data milte hi channel chalu kar dega!
            2000   // REBUFFER: Agar net atka, toh wapas chalne ke liye sirf 2 sec wait karega
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

            // CRITICAL FIX FOR S25 EDGE & NEW PHONES (Black Screen Issue)
            val renderersFactory = DefaultRenderersFactory(context.applicationContext).apply {
                setEnableDecoderFallback(true)
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            }

            exoPlayer = ExoPlayer.Builder(
                context.applicationContext,
                renderersFactory
            )
                .setLoadControl(loadControl) // 🔥 FIX 1: Is line se Anti-Buffering apply hoga
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    playWhenReady = true

                    // 🔥 FIX 2: Yahan se buffering karne wali purani line hata di gayi hai
                    trackSelectionParameters = trackSelectionParameters
                        .buildUpon()
                        // .setIgnoredTextSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_FORCED) => Subtitle ko Ek line me show karne ke liye but Buffering badha deta hai
                        .setSelectUndeterminedTextLanguage(true)
                        .build()

                    addListener(object : Player.Listener {
                        override fun onPlayerError(
                            error: PlaybackException
                        ) {
                            error.printStackTrace()
                        }
                    })
                }
        }
        return exoPlayer!!
    }

    fun attach(
        context: Context,
        playerView: PlayerView
    ) {
        val player = getPlayer(context)
        if (currentPlayerView === playerView)
            return
        currentPlayerView?.player = null
        playerView.player = player
        currentPlayerView = playerView
    }

    fun detach(
        playerView: PlayerView
    ) {
        if (currentPlayerView === playerView) {
            playerView.player = null
            currentPlayerView = null
        }
    }

    fun play(
        context: Context,
        playerView: PlayerView,
        streamUrl: String
    ) {
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
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.prepare()
        player.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun stop() {
        exoPlayer?.stop()
        currentUrl = null
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        currentUrl = null
    }

    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying ?: false
    }

    fun getCurrentUrl(): String? {
        return currentUrl
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

    fun getExoPlayerOrNull(): ExoPlayer? = exoPlayer

    fun getCurrentUrlOrEmpty(): String = currentUrl ?: ""


}
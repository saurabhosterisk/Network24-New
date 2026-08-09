package com.network24.player.features.player.multiview

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
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

@OptIn(UnstableApi::class)
class MultiPlayerManager(
    private val context: Context,
    private val listener: Listener? = null
) {
    interface Listener {
        fun onLoading(slot: Int)
        fun onReady(slot: Int)
        fun onError(slot: Int, message: String)
    }

    private val players = arrayOfNulls<ExoPlayer>(4)
    private val urls = arrayOfNulls<String>(4)
    private val reducedProfile = BooleanArray(4)

    fun attach(slot: Int, playerView: PlayerView) {
        require(slot in 0..3)
        val player = players[slot] ?: createPlayer(slot).also { players[slot] = it }
        playerView.player = player
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
    }

    private fun createPlayer(slot: Int): ExoPlayer {
        // IMPORTANT: every ExoPlayer gets its own LoadControl instance.
        // Media3 requires a LoadControl to belong to a single playback thread.
        // Sharing one LoadControl between four players causes the second player
        // to fail when it is attached/started.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(3000, 12000, 500, 1000)
            .build()

        // The XC server requires the exact same User-Agent as the normal player.
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("N24PlayerPlayer")
            .setAllowCrossProtocolRedirects(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)
        val renderersFactory = DefaultRenderersFactory(context.applicationContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        return ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
                volume = 0f
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setMaxVideoSize(1280, 720)
                    .build()

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> listener?.onLoading(slot)
                            Player.STATE_READY -> listener?.onReady(slot)
                            Player.STATE_ENDED -> listener?.onError(slot, "Stream ended")
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val cause = error.cause?.message ?: error.message ?: "Playback error"
                        val decoderError = cause.contains("decoder", true) ||
                            cause.contains("codec", true) ||
                            cause.contains("MediaCodec", true) ||
                            cause.contains("surface", true)

                        if (decoderError && !reducedProfile[slot]) {
                            reducedProfile[slot] = true
                            trackSelectionParameters = trackSelectionParameters.buildUpon()
                                .setMaxVideoSize(854, 480)
                                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                                .build()
                            listener?.onLoading(slot)
                            prepare()
                            playWhenReady = true
                            play()
                        } else {
                            listener?.onError(slot, cause.take(120))
                        }
                    }
                })
            }
    }

    fun play(slot: Int, url: String) {
        require(slot in 0..3)
        val player = players[slot] ?: createPlayer(slot).also { players[slot] = it }
        if (urls[slot] == url && player.playbackState != Player.STATE_IDLE) {
            player.playWhenReady = true
            player.play()
            return
        }

        urls[slot] = url
        reducedProfile[slot] = false
        listener?.onLoading(slot)
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
        player.play()
    }

    fun setAudioFocus(slot: Int) {
        for (i in 0..3) {
            players[i]?.apply {
                val focused = i == slot
                volume = if (focused) 1f else 0f
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !focused)
                    .build()
            }
        }
    }

    fun clear(slot: Int) {
        require(slot in 0..3)
        players[slot]?.stop()
        players[slot]?.clearMediaItems()
        urls[slot] = null
        reducedProfile[slot] = false
    }

    fun getPlayer(slot: Int): ExoPlayer? = if (slot in 0..3) players[slot] else null

    fun release() {
        for (i in 0..3) {
            players[i]?.release()
            players[i] = null
            urls[i] = null
            reducedProfile[i] = false
        }
    }
}

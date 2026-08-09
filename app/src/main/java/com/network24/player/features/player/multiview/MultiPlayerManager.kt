package com.network24.player.features.player.multiview

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
class MultiPlayerManager(private val context: Context) {
    private val players = arrayOfNulls<ExoPlayer>(4)
    private val urls = arrayOfNulls<String>(4)

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(3000, 12000, 500, 1000)
        .build()

    fun attach(slot: Int, playerView: PlayerView) {
        require(slot in 0..3)
        val player = players[slot] ?: createPlayer().also { players[slot] = it }
        playerView.player = player
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
    }

    private fun createPlayer(): ExoPlayer {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("N24PlayerMultiView")
            .setAllowCrossProtocolRedirects(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)
        val renderersFactory = DefaultRenderersFactory(context.applicationContext)
            .setEnableDecoderFallback(true)

        return ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
                volume = 0f
                // Do not impose a resolution ceiling. XC providers may expose
                // only 720p/1080p renditions. A hard cap can leave no selectable
                // video track and result in a black tile.
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
            }
    }

    fun play(slot: Int, url: String) {
        require(slot in 0..3)
        val player = players[slot] ?: createPlayer().also { players[slot] = it }

        if (urls[slot] == url && player.playbackState != Player.STATE_IDLE) {
            player.playWhenReady = true
            player.play()
            return
        }

        urls[slot] = url
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
    }

    fun getPlayer(slot: Int): ExoPlayer? = if (slot in 0..3) players[slot] else null

    fun release() {
        for (i in 0..3) {
            players[i]?.release()
            players[i] = null
            urls[i] = null
        }
    }
}

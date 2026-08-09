package com.network24.player.features.player.multiview

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
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
        .setBufferDurationsMs(5000, 15000, 500, 1000)
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
                // Do not cap at 480p: some providers expose only 720p/1080p.
                // A hard 480p ceiling can leave no selectable video track and
                // produce a black surface. 720p is a safer 4-up compromise.
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setMaxVideoSize(1280, 720)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
            }
    }

    fun play(slot: Int, url: String) {
        require(slot in 0..3)
        val player = players[slot] ?: createPlayer().also { players[slot] = it }
        if (urls[slot] == url && player.playbackState != androidx.media3.common.Player.STATE_IDLE) {
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
                volume = if (i == slot) 1f else 0f
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, i != slot)
                    .setMaxVideoSize(1280, 720)
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

    fun release() {
        for (i in 0..3) {
            players[i]?.release()
            players[i] = null
            urls[i] = null
        }
    }
}

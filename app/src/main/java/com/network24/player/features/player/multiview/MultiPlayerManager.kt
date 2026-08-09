package com.network24.player.features.player.multiview

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.network24.player.core.net.StreamDataSourceFactory

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
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 50000, 1000, 2000)
            .build()

        return ExoPlayer.Builder(
            context.applicationContext,
            StreamDataSourceFactory.createRenderersFactory(context)
        )
            .setLoadControl(loadControl)
            .setMediaSourceFactory(StreamDataSourceFactory.createMediaSourceFactory())
            .build()
            .apply {
                playWhenReady = true
                volume = 0f
                // Keep the audio renderer enabled so a focused window can turn
                // audio on immediately without requiring a new media prepare.
                // Volume is used to keep the three non-focused windows silent.
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setSelectUndeterminedTextLanguage(true)
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
                        val httpError = findHttpError(error)
                        if (httpError != null) {
                            listener?.onError(
                                slot,
                                "HTTP ${httpError.first} from ${httpError.second}"
                            )
                            return
                        }

                        val cause = error.cause?.message ?: error.message ?: "Playback error"
                        val decoderError = cause.contains("decoder", true) ||
                            cause.contains("codec", true) ||
                            cause.contains("MediaCodec", true) ||
                            cause.contains("surface", true)

                        if (decoderError && !reducedProfile[slot]) {
                            reducedProfile[slot] = true
                            trackSelectionParameters = trackSelectionParameters
                                .buildUpon()
                                .setMaxVideoSize(1280, 720)
                                .setSelectUndeterminedTextLanguage(true)
                                .build()
                            listener?.onLoading(slot)
                            prepare()
                            playWhenReady = true
                            play()
                        } else {
                            listener?.onError(
                                slot,
                                "${error.errorCodeName}: ${cause.take(220)}"
                            )
                        }
                    }
                })
            }
    }

    private fun findHttpError(error: PlaybackException): Pair<Int, String>? {
        var current: Throwable? = error
        while (current != null) {
            if (current is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                val uri = current.dataSpec.uri
                return current.responseCode to "${uri.host}${uri.path}"
            }
            current = current.cause
        }
        return null
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
        require(slot in 0..3)
        for (i in 0..3) {
            players[i]?.let { player ->
                player.volume = if (i == slot) 1f else 0f
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

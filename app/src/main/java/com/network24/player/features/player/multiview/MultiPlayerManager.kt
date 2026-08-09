package com.network24.player.features.player.multiview

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
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
    private val decoderRecoveryAttempts = IntArray(4)

    fun attach(slot: Int, playerView: PlayerView) {
        require(slot in 0..3)
        val player = players[slot] ?: createPlayer(slot).also { players[slot] = it }
        playerView.player = player
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
    }

    private fun createPlayer(slot: Int): ExoPlayer {
        // Each player owns its own LoadControl. Sharing one between players can
        // cause Media3 playback-thread errors on Android TV/Fire TV.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(8000, 24000, 1000, 2000)
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
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> listener?.onLoading(slot)
                            Player.STATE_READY -> {
                                decoderRecoveryAttempts[slot] = 0
                                listener?.onReady(slot)
                            }
                            Player.STATE_ENDED -> listener?.onError(slot, "Stream ended")
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val httpError = findHttpError(error)
                        if (httpError != null) {
                            // HTTP rejection is a server/request problem. Do not
                            // repeatedly recreate the decoder for a 403/404.
                            listener?.onError(
                                slot,
                                "HTTP ${httpError.first} from ${httpError.second}"
                            )
                            return
                        }

                        val cause = fullErrorMessage(error)
                        val decoderError = isDecoderError(error, cause)

                        if (decoderError && decoderRecoveryAttempts[slot] == 0) {
                            decoderRecoveryAttempts[slot] = 1
                            reducedProfile[slot] = true

                            // Do not force 480p: some XC providers do not publish
                            // a 480p rendition. 720p is a safer multi-view target.
                            trackSelectionParameters = trackSelectionParameters
                                .buildUpon()
                                .setMaxVideoSize(1280, 720)
                                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                                .build()

                            listener?.onLoading(slot)
                            stop()
                            clearMediaItems()
                            urls[slot]?.let { url ->
                                setMediaItem(MediaItem.fromUri(url))
                                prepare()
                                playWhenReady = true
                                play()
                            }
                            return
                        }

                        listener?.onError(
                            slot,
                            "${error.errorCodeName}: ${cause.take(220)}"
                        )
                    }
                })
            }
    }

    private fun isDecoderError(error: PlaybackException, message: String): Boolean {
        if (error.errorCodeName.contains("DECODER", true)) return true
        return message.contains("decoder", true) ||
            message.contains("codec", true) ||
            message.contains("MediaCodec", true) ||
            message.contains("DecoderInitializationException", true) ||
            message.contains("Decoder failed", true)
    }

    private fun fullErrorMessage(error: PlaybackException): String {
        val messages = mutableListOf<String>()
        var current: Throwable? = error
        while (current != null && messages.size < 5) {
            current.message?.takeIf { it.isNotBlank() }?.let { messages += it }
            current = current.cause
        }
        return messages.distinct().joinToString(" | ").ifBlank { "Playback error" }
    }

    private fun findHttpError(error: PlaybackException): Pair<Int, String>? {
        var current: Throwable? = error
        while (current != null) {
            if (current is HttpDataSource.InvalidResponseCodeException) {
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
        decoderRecoveryAttempts[slot] = 0
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
            players[i]?.volume = if (i == slot) 1f else 0f
        }
    }

    fun clear(slot: Int) {
        require(slot in 0..3)
        players[slot]?.stop()
        players[slot]?.clearMediaItems()
        urls[slot] = null
        reducedProfile[slot] = false
        decoderRecoveryAttempts[slot] = 0
    }

    fun getPlayer(slot: Int): ExoPlayer? = if (slot in 0..3) players[slot] else null

    fun release() {
        for (i in 0..3) {
            players[i]?.release()
            players[i] = null
            urls[i] = null
            reducedProfile[i] = false
            decoderRecoveryAttempts[i] = 0
        }
    }
}

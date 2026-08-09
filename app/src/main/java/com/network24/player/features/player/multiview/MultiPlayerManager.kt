package com.network24.player.features.player.multiview

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.network24.player.core.net.CountingDataSource

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
        // Keep each player completely independent. In particular, each player
        // owns its own LoadControl because Media3 does not allow a LoadControl
        // to be shared by players running on different playback threads.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000,
                50000,
                1000,
                2000
            )
            .build()

        // This is intentionally the same HTTP/DataSource chain as PlayerManager.
        // The XC server allows playback only for this exact User-Agent.
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

        return ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
                volume = 0f

                // Do not impose a resolution ceiling here. The normal player
                // does not impose one either, and some XC providers expose only
                // 720p/1080p variants. Audio is disabled initially because only
                // the focused MultiView window should consume audio.
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
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
                        val cause = error.cause?.message ?: error.message ?: "Playback error"
                        val decoderError = cause.contains("decoder", true) ||
                            cause.contains("codec", true) ||
                            cause.contains("MediaCodec", true) ||
                            cause.contains("surface", true)

                        if (httpError != null) {
                            // Do not retry HTTP 403 as a decoder problem. A 403 is
                            // an upstream server rejection and retrying the same
                            // request only creates more rejected connections.
                            listener?.onError(
                                slot,
                                "HTTP ${httpError.first} from ${httpError.second}"
                            )
                            return
                        }

                        if (decoderError && !reducedProfile[slot]) {
                            reducedProfile[slot] = true
                            trackSelectionParameters = trackSelectionParameters
                                .buildUpon()
                                .setMaxVideoSize(854, 480)
                                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                                .build()
                            listener?.onLoading(slot)
                            prepare()
                            playWhenReady = true
                            play()
                        } else {
                            listener?.onError(
                                slot,
                                "${error.errorCodeName}: ${cause.take(100)}"
                            )
                        }
                    }
                })
            }
    }

    /**
     * Returns HTTP response code and sanitized request URL when Media3 exposes
     * an InvalidResponseCodeException in the cause chain. Credentials are never
     * included in the UI; only the host/path is shown.
     */
    private fun findHttpError(error: PlaybackException): Pair<Int, String>? {
        var current: Throwable? = error
        while (current != null) {
            if (current is DefaultHttpDataSource.InvalidResponseCodeException) {
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
        for (i in 0..3) {
            players[i]?.apply {
                val focused = i == slot
                volume = if (focused) 1f else 0f
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
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

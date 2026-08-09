package com.network24.player.core.net

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Single source of truth for stream HTTP/media-source configuration.
 * Normal playback and MultiView use the same request configuration.
 */
@OptIn(UnstableApi::class)
object StreamDataSourceFactory {
    const val USER_AGENT = "N24PlayerPlayer"

    fun createDataSourceFactory(): DataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)

        return DataSource.Factory {
            CountingDataSource(httpFactory.createDataSource())
        }
    }

    fun createMediaSourceFactory(): DefaultMediaSourceFactory {
        return DefaultMediaSourceFactory(createDataSourceFactory())
    }

    /** Normal player: keep the standard Media3 renderer selection. */
    fun createRenderersFactory(context: Context): DefaultRenderersFactory {
        return DefaultRenderersFactory(context.applicationContext).apply {
            setEnableDecoderFallback(true)
        }
    }

    /**
     * MultiView: prefer the bundled Media3 FFmpeg extension renderer.
     * Supported codecs are therefore decoded in software instead of using the
     * device MediaCodec decoder first. If a stream codec is not supported by
     * FFmpeg, Media3 can still fall back to its normal decoder path.
     */
    fun createSoftwareRenderersFactory(context: Context): DefaultRenderersFactory {
        return DefaultRenderersFactory(context.applicationContext).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }
    }
}

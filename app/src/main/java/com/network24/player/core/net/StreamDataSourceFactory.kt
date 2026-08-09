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
 *
 * MultiView prefers the Media3 FFmpeg extension renderer so supported video
 * codecs are decoded in software. Normal playback keeps the same factory
 * entry point but is not otherwise forced to software decoding.
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

    fun createRenderersFactory(context: Context): DefaultRenderersFactory {
        return DefaultRenderersFactory(context.applicationContext).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }
    }
}

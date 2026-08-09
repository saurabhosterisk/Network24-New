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
 * Normal playback and MultiView must use the same request configuration so
 * HLS playlist, variant and segment requests behave consistently.
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

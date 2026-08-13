package com.network24.player.features.live.history

import android.content.Context
import com.network24.player.core.database.repository.LiveHistoryRepository
import com.network24.player.features.live.models.LiveChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Records live channels without tying watch history to an Activity lifecycle. */
object LiveWatchHistory {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun record(context: Context, channel: LiveChannel) {
        scope.launch {
            LiveHistoryRepository(context).record(channel)
        }
    }
}

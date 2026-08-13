package com.network24.player.features.live.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.network24.player.R
import com.network24.player.core.base.BaseActivity
import com.network24.player.core.database.repository.LiveHistoryRepository
import com.network24.player.databinding.ActivityRecentlyWatchedBinding
import com.network24.player.features.live.adapter.ChannelAdapter
import com.network24.player.features.live.models.LiveChannel
import com.network24.player.features.player.activity.PlayerActivity
import com.network24.player.features.player.state.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecentlyWatchedActivity : BaseActivity() {

    private lateinit var binding: ActivityRecentlyWatchedBinding
    private lateinit var historyRepository: LiveHistoryRepository
    private val channels = mutableListOf<LiveChannel>()
    private lateinit var adapter: ChannelAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecentlyWatchedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        historyRepository = LiveHistoryRepository(this)
        adapter = ChannelAdapter(
            channels = channels,
            onFocused = { _, _ -> },
            onClicked = { _, position -> openChannel(position) }
        )
        binding.rvRecentChannels.layoutManager = LinearLayoutManager(this)
        binding.rvRecentChannels.adapter = adapter
        binding.btnBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        loadRecentlyWatched()
    }

    private fun loadRecentlyWatched() {
        lifecycleScope.launch {
            binding.progressLoading.visibility = View.VISIBLE
            val recentChannels = withContext(Dispatchers.IO) {
                historyRepository.getRecentlyWatched()
            }

            binding.progressLoading.visibility = View.GONE
            adapter.updateData(recentChannels)
            binding.txtEmpty.visibility = if (recentChannels.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    private fun openChannel(position: Int) {
        PlayerState.channels.clear()
        PlayerState.channels.addAll(channels)
        PlayerState.currentPosition = position

        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_PLAY_SELECTED_CHANNEL, true)
        )
    }
}

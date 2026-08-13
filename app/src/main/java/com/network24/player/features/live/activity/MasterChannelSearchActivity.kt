package com.network24.player.features.live.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.network24.player.core.base.BaseActivity
import com.network24.player.core.database.entity.MasterChannelSearchResult
import com.network24.player.databinding.ActivityMasterChannelSearchBinding
import com.network24.player.features.live.adapter.MasterChannelSearchAdapter
import com.network24.player.features.live.repository.LiveRepository
import com.network24.player.features.player.activity.PlayerActivity
import com.network24.player.features.player.state.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MasterChannelSearchActivity : BaseActivity() {

    private lateinit var binding: ActivityMasterChannelSearchBinding
    private lateinit var repository: LiveRepository
    private lateinit var adapter: MasterChannelSearchAdapter

    private var searchJob: Job? = null
    private var searchResults: List<MasterChannelSearchResult> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMasterChannelSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = LiveRepository(this)
        adapter = MasterChannelSearchAdapter(::playSearchResult)
        binding.rvMasterResults.layoutManager = LinearLayoutManager(this)
        binding.rvMasterResults.adapter = adapter
        binding.rvMasterResults.setHasFixedSize(true)

        binding.btnBack.setOnClickListener { finish() }
        binding.edtMasterSearch.addTextChangedListener { text ->
            scheduleSearch(text?.toString().orEmpty())
        }
        binding.edtMasterSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                scheduleSearch(binding.edtMasterSearch.text?.toString().orEmpty(), immediate = true)
                true
            } else {
                false
            }
        }

        binding.edtMasterSearch.requestFocus()
    }

    private fun scheduleSearch(rawQuery: String, immediate: Boolean = false) {
        val query = rawQuery.trim()
        searchJob?.cancel()

        if (query.isBlank()) {
            searchResults = emptyList()
            adapter.submitResults(emptyList())
            binding.progressSearch.visibility = View.GONE
            binding.txtSearchStatus.text = "Search every live channel without choosing a category."
            return
        }

        searchJob = lifecycleScope.launch {
            if (!immediate) delay(250)
            searchChannels(query)
        }
    }

    private suspend fun searchChannels(query: String) {
        binding.progressSearch.visibility = View.VISIBLE
        binding.txtSearchStatus.text = "Searching all live channels..."

        val result = withContext(Dispatchers.IO) {
            runCatching { repository.searchAllLiveChannels(query) }
        }

        if (binding.edtMasterSearch.text?.toString()?.trim() != query) return

        binding.progressSearch.visibility = View.GONE
        result.onFailure { error ->
            searchResults = emptyList()
            adapter.submitResults(emptyList())
            binding.txtSearchStatus.text = error.message ?: "Unable to search channels."
            return
        }

        searchResults = result.getOrDefault(emptyList())
        adapter.submitResults(searchResults)
        binding.txtSearchStatus.text = when (searchResults.size) {
            0 -> "No live channels match '$query'."
            1 -> "1 live channel found for '$query'. Select it to play."
            else -> "${searchResults.size} live channels found for '$query'. Select one to play."
        }
    }

    private fun playSearchResult(selected: MasterChannelSearchResult) {
        val playbackChannels = searchResults
            .map { it.toLiveChannel() }
            .distinctBy { it.stream_id }
        val position = playbackChannels.indexOfFirst {
            it.stream_id == selected.streamId
        }
        if (position < 0) return

        PlayerState.channels.clear()
        PlayerState.channels.addAll(playbackChannels)
        PlayerState.currentPosition = position

        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_PLAY_SELECTED_CHANNEL, true)
        )
    }

    override fun onDestroy() {
        searchJob?.cancel()
        super.onDestroy()
    }
}

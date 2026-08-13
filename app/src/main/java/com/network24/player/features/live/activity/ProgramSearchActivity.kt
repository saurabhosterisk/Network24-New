package com.network24.player.features.live.activity

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.network24.player.R
import com.network24.player.core.base.BaseActivity
import com.network24.player.core.database.DatabaseProvider
import com.network24.player.core.database.mapper.toLiveChannel
import com.network24.player.core.sync.SyncManager
import com.network24.player.core.sync.SyncResult
import com.network24.player.databinding.ActivityProgramSearchBinding
import com.network24.player.features.live.adapter.ProgramSearchAdapter
import com.network24.player.features.live.models.ProgramSearchResult
import com.network24.player.features.player.activity.PlayerActivity
import com.network24.player.features.player.state.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProgramSearchActivity : BaseActivity() {

    private lateinit var binding: ActivityProgramSearchBinding
    private lateinit var adapter: ProgramSearchAdapter

    private var searchJob: Job? = null
    private var guideAvailabilityChecked = false
    private var searchResults: List<ProgramSearchResult> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgramSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ProgramSearchAdapter(::playSearchResult)
        binding.rvProgramResults.layoutManager = LinearLayoutManager(this)
        binding.rvProgramResults.adapter = adapter
        binding.rvProgramResults.setHasFixedSize(true)

        binding.btnBack.setOnClickListener { finish() }

        binding.edtProgramSearch.addTextChangedListener { text ->
            scheduleSearch(text?.toString().orEmpty())
        }
        binding.edtProgramSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                scheduleSearch(binding.edtProgramSearch.text?.toString().orEmpty(), immediate = true)
                true
            } else {
                false
            }
        }

        binding.edtProgramSearch.requestFocus()
    }

    private fun scheduleSearch(rawQuery: String, immediate: Boolean = false) {
        val query = rawQuery.trim()
        searchJob?.cancel()

        if (query.isBlank()) {
            searchResults = emptyList()
            adapter.submitResults(emptyList())
            binding.progressSearch.visibility = android.view.View.GONE
            binding.txtSearchStatus.text = "Enter a programme title to search the TV guide."
            return
        }

        searchJob = lifecycleScope.launch {
            if (!immediate) delay(250)
            searchPrograms(query)
        }
    }

    private suspend fun searchPrograms(query: String) {
        binding.progressSearch.visibility = android.view.View.VISIBLE
        binding.txtSearchStatus.text = "Searching the TV guide…"

        val load = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val windowEnd = now + SEARCH_WINDOW_MS
            val db = DatabaseProvider.get(this@ProgramSearchActivity)
            val epgDao = db.epgDao()

            if (!guideAvailabilityChecked) {
                guideAvailabilityChecked = true
                if (epgDao.countProgramsInWindow(now, windowEnd) == 0) {
                    val syncResult = SyncManager(this@ProgramSearchActivity).syncFullEpg(force = true)
                    if (syncResult is SyncResult.Error) {
                        return@withContext SearchLoad(error = syncResult.message)
                    }
                }
            }

            val channelsByEpgId = db.channelDao()
                .getAll()
                .map { it.toLiveChannel() }
                .mapNotNull { channel ->
                    channel.epg_channel_id
                        ?.takeIf(String::isNotBlank)
                        ?.let { it to channel }
                }
                .groupBy({ it.first }, { it.second })

            val results = epgDao.searchProgramsInWindow(query, now, windowEnd)
                .mapNotNull { program ->
                    program.epgChannelId
                        ?.let { channelsByEpgId[it] }
                        ?.firstOrNull()
                        ?.let { channel -> ProgramSearchResult(channel, program) }
                }
                .distinctBy { it.channel.stream_id }

            SearchLoad(results = results)
        }

        if (binding.edtProgramSearch.text?.toString()?.trim() != query) return

        binding.progressSearch.visibility = android.view.View.GONE
        if (load.error != null) {
            searchResults = emptyList()
            adapter.submitResults(emptyList())
            binding.txtSearchStatus.text = load.error
            return
        }

        searchResults = load.results
        adapter.submitResults(searchResults)
        binding.txtSearchStatus.text = when (searchResults.size) {
            0 -> "No programmes matching “$query” are on now or start within the next hour."
            1 -> "1 channel found for “$query”. Select it to play."
            else -> "${searchResults.size} channels found for “$query”. Select one to play."
        }
    }

    private fun playSearchResult(result: ProgramSearchResult) {
        val playbackChannels = searchResults
            .map { it.channel }
            .distinctBy { it.stream_id }
        val position = playbackChannels.indexOfFirst {
            it.stream_id == result.channel.stream_id
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

    private data class SearchLoad(
        val results: List<ProgramSearchResult> = emptyList(),
        val error: String? = null
    )

    private companion object {
        const val SEARCH_WINDOW_MS = 60L * 60L * 1000L
    }
}

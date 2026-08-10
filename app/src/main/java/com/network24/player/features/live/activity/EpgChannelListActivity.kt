package com.network24.player.features.live.activity

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.network24.player.R
import com.network24.player.core.base.BaseActivity
import com.network24.player.core.database.DatabaseProvider
import com.network24.player.core.preferences.PreferenceManager
import com.network24.player.databinding.ActivityEpgChannelListBinding
import com.network24.player.features.live.adapter.EpgChannelAdapter
import com.network24.player.features.live.models.LiveChannel
import com.network24.player.features.live.repository.LiveRepository
import com.network24.player.features.player.manager.PlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EpgChannelListActivity : BaseActivity() {

    private lateinit var binding: ActivityEpgChannelListBinding
    private lateinit var repository: LiveRepository
    private lateinit var prefs: PreferenceManager
    private lateinit var adapter: EpgChannelAdapter
    private val channels = mutableListOf<LiveChannel>()
    private lateinit var categoryId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEpgChannelListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferenceManager(this)
        repository = LiveRepository(this)
        categoryId = intent.getStringExtra("category_id") ?: ""
        binding.txtCategoryName.text = intent.getStringExtra("category_name") ?: "LIVE WITH EPG"
        binding.btnBack.setOnClickListener { finish() }

        binding.rvChannels.layoutManager = LinearLayoutManager(this)
        adapter = EpgChannelAdapter(
            channels = channels,
            onFocused = { channel, _ -> showEpg(channel) },
            onClicked = { channel, _ -> playChannel(channel) }
        )
        binding.rvChannels.adapter = adapter
        PlayerManager.attach(this, binding.playerView)

        loadChannels()
    }

    private suspend fun getChannelsForEpg(): List<LiveChannel> {
        var result = repository.getChannels(
            server = prefs.getServer(),
            username = prefs.getUsername(),
            password = prefs.getPassword(),
            categoryId = categoryId,
            forceRefresh = false
        )

        // The normal Live TV path can already have the category cached. If this
        // dedicated screen gets an empty category, refresh once before giving up.
        if (result.isEmpty()) {
            result = repository.getChannels(
                server = prefs.getServer(),
                username = prefs.getUsername(),
                password = prefs.getPassword(),
                categoryId = categoryId,
                forceRefresh = true
            )
        }

        // Final safety net: use the complete local channel list and filter it by
        // category. This avoids an empty EPG screen when the category index/cache
        // is temporarily out of sync with the channel table.
        if (result.isEmpty()) {
            val all = repository.getChannels(
                server = prefs.getServer(),
                username = prefs.getUsername(),
                password = prefs.getPassword(),
                categoryId = "all",
                forceRefresh = false
            )
            result = all.filter { it.category_id == categoryId }
        }

        return result
    }

    private fun loadChannels() {
        binding.txtEpgStatus.text = "Loading channels…"
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { getChannelsForEpg() }
                channels.clear()
                channels.addAll(result)
                adapter.updateData(channels)

                if (channels.isNotEmpty()) {
                    binding.txtEpgStatus.text = "${channels.size} channels"
                    binding.rvChannels.post {
                        binding.rvChannels.scrollToPosition(0)
                        binding.rvChannels.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                    }
                } else {
                    binding.txtEpgStatus.text = "No channels available in this category"
                }
            } catch (e: Exception) {
                binding.txtEpgStatus.text = e.message ?: "Unable to load channels"
            }
        }
    }

    private fun playChannel(channel: LiveChannel) {
        val streamId = channel.stream_id ?: return
        val server = prefs.getServer().trim().trimEnd('/')
        val url = "$server/live/${prefs.getUsername()}/${prefs.getPassword()}/$streamId.m3u8"
        PlayerManager.play(this, binding.playerView, url)
        binding.txtPlayerChannel.text = channel.name ?: ""
        showEpg(channel)
    }

    private fun showEpg(channel: LiveChannel) {
        val epgId = channel.epg_channel_id ?: channel.stream_id?.toString()
        binding.txtChannelTitle.text = channel.name ?: "Unknown Channel"
        binding.txtEpgStatus.text = "Loading EPG…"
        if (epgId.isNullOrBlank()) {
            binding.txtEpgStatus.text = "No EPG channel ID available"
            binding.epgContainer.removeAllViews()
            return
        }

        lifecycleScope.launch {
            try {
                val programs = withContext(Dispatchers.IO) {
                    DatabaseProvider.get(this@EpgChannelListActivity)
                        .epgDao()
                        .getByEpgChannelId(epgId)
                }
                val now = System.currentTimeMillis()
                val visible = programs.filter {
                    (it.startTimestamp ?: 0L) > 0L && (it.stopTimestamp ?: 0L) > now
                }.sortedBy { it.startTimestamp }

                val current = visible.firstOrNull {
                    val start = it.startTimestamp ?: 0L
                    val stop = it.stopTimestamp ?: 0L
                    start <= now && stop > now
                }
                val next = visible.firstOrNull { it !== current && (it.startTimestamp ?: 0L) > now }
                val upcoming = visible.filter { it !== current && it !== next }.take(12)

                binding.epgContainer.removeAllViews()
                if (current != null) addProgram("NOW", current.title ?: "No Program Info", current.startTimestamp, current.stopTimestamp, true)
                if (next != null) addProgram("NEXT", next.title ?: "", next.startTimestamp, next.stopTimestamp, false)
                upcoming.forEach { addProgram("UPCOMING", it.title ?: "", it.startTimestamp, it.stopTimestamp, false) }

                binding.txtEpgStatus.text = if (visible.isEmpty()) "No EPG available for this channel" else "${visible.size} EPG entries available"
            } catch (_: Exception) {
                binding.txtEpgStatus.text = "EPG unavailable"
                binding.epgContainer.removeAllViews()
            }
        }
    }

    private fun addProgram(label: String, title: String, start: Long?, stop: Long?, highlight: Boolean) {
        val card = TextView(this).apply {
            text = "$label\n$title\n${formatTime(start)} - ${formatTime(stop)}"
            setTextColor(Color.WHITE)
            textSize = if (highlight) 16f else 14f
            setPadding(14, 12, 14, 12)
            setBackgroundResource(R.drawable.bg_epg_item)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }
        binding.epgContainer.addView(card)
    }

    private fun formatTime(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0L) return ""
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}

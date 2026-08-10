package com.network24.player.features.live.activity

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.network24.player.R
import com.network24.player.core.base.BaseActivity
import com.network24.player.core.preferences.PreferenceManager
import com.network24.player.databinding.ActivityEpgChannelListBinding
import com.network24.player.features.live.adapter.EpgChannelAdapter
import com.network24.player.features.live.models.LiveChannel
import com.network24.player.features.live.repository.LiveRepository
import com.network24.player.features.player.manager.PlayerManager
import kotlinx.coroutines.launch
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
    private var categoryName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEpgChannelListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceManager(this)
        repository = LiveRepository(this)

        // These are the exact same extras used by LiveCategoryActivity when it
        // opens ChannelListActivity. Do not resolve the category a second time.
        categoryId = intent.getStringExtra("category_id")?.trim().orEmpty()
        categoryName = intent.getStringExtra("category_name")?.trim().orEmpty()
        binding.txtCategoryName.text = categoryName.ifBlank { "LIVE WITH EPG" }
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

    /**
     * IMPORTANT: Live With EPG deliberately uses the exact same repository
     * call as normal Live TV. ChannelListActivity does not query Room directly;
     * it calls LiveRepository.getChannels(categoryId). Keeping one source of
     * truth prevents the two screens from disagreeing about channel data.
     */
    private fun loadChannels() {
        binding.txtEpgStatus.text = "Loading channels…"

        lifecycleScope.launch {
            try {
                if (categoryId.isBlank()) {
                    binding.txtEpgStatus.text = "Invalid category"
                    return@launch
                }

                var result = repository.getChannels(
                    server = prefs.getServer(),
                    username = prefs.getUsername(),
                    password = prefs.getPassword(),
                    categoryId = categoryId,
                    forceRefresh = false
                )

                // This is the same recovery used by Live TV when its category
                // has no local data. It is deliberately only a fallback.
                if (result.isEmpty()) {
                    result = repository.getChannels(
                        server = prefs.getServer(),
                        username = prefs.getUsername(),
                        password = prefs.getPassword(),
                        categoryId = categoryId,
                        forceRefresh = true
                    )
                }

                channels.clear()
                channels.addAll(result)
                adapter.updateData(channels)

                if (channels.isEmpty()) {
                    binding.txtEpgStatus.text = "No channels available in this category"
                    return@launch
                }

                binding.txtEpgStatus.text = "${channels.size} channels"
                binding.rvChannels.post {
                    binding.rvChannels.scrollToPosition(0)
                    binding.rvChannels.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
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
                val (nowEpg, nextEpg) = repository.getNowNextEpg(epgId)

                binding.epgContainer.removeAllViews()
                if (nowEpg != null) {
                    addProgram("NOW", nowEpg.title ?: "No Program Info", nowEpg.startTimestamp, nowEpg.stopTimestamp, true)
                }
                if (nextEpg != null) {
                    addProgram("NEXT", nextEpg.title ?: "", nextEpg.startTimestamp, nextEpg.stopTimestamp, false)
                }

                binding.txtEpgStatus.text = when {
                    nowEpg != null && nextEpg != null -> "Current & next program"
                    nowEpg != null -> "Current program"
                    nextEpg != null -> "Next program"
                    else -> "No EPG available for this channel"
                }
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

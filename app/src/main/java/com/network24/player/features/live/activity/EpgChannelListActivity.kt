package com.network24.player.features.live.activity

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.network24.player.R
import com.network24.player.core.base.BaseActivity
import com.network24.player.core.database.entity.EpgEntity
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
    private var selectedChannel: LiveChannel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEpgChannelListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceManager(this)
        repository = LiveRepository(this)

        categoryId = intent.getStringExtra("category_id")?.trim().orEmpty()
        categoryName = intent.getStringExtra("category_name")?.trim().orEmpty()
        binding.txtCategoryName.text = categoryName.ifBlank { "LIVE WITH EPG" }
        binding.btnBack.setOnClickListener { finish() }

        binding.rvChannels.layoutManager = LinearLayoutManager(this)
        adapter = EpgChannelAdapter(
            channels = channels,
            onFocused = { channel, _ ->
                // Focus changes only the highlighted channel. EPG remains tied
                // to the channel that is actually playing.
                updateHighlightedChannel(channel)
            },
            onClicked = { channel, _ -> playChannel(channel) }
        )
        binding.rvChannels.adapter = adapter
        PlayerManager.attach(this, binding.playerView)

        loadChannels()
    }

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
        selectedChannel = channel
        binding.txtPlayerChannel.text = channel.name ?: ""
        loadFullEpg(channel, forceRefresh = false)
    }

    private fun updateHighlightedChannel(channel: LiveChannel) {
        // Keep the left-side navigation responsive without replacing the guide
        // for the channel currently playing.
        if (selectedChannel == null) {
            binding.txtChannelTitle.text = channel.name ?: "Select a channel"
        }
    }

    private fun loadFullEpg(channel: LiveChannel, forceRefresh: Boolean) {
        val epgId = channel.epg_channel_id ?: channel.stream_id?.toString()
        binding.txtChannelTitle.text = channel.name ?: "Unknown Channel"
        binding.txtEpgStatus.text = "Loading 3-day EPG…"
        binding.epgContainer.removeAllViews()

        if (epgId.isNullOrBlank()) {
            binding.txtEpgStatus.text = "No EPG channel ID available"
            return
        }

        lifecycleScope.launch {
            try {
                val listings = repository.getFullEpg(
                    epgChannelId = epgId,
                    days = 3,
                    forceRefresh = forceRefresh
                )
                renderFullEpg(listings)
            } catch (_: Exception) {
                binding.txtEpgStatus.text = "EPG unavailable"
                binding.epgContainer.removeAllViews()
            }
        }
    }

    private fun renderFullEpg(listings: List<EpgEntity>) {
        binding.epgContainer.removeAllViews()

        if (listings.isEmpty()) {
            binding.txtEpgStatus.text = "No EPG available for this channel"
            return
        }

        val dayFormat = SimpleDateFormat("EEEE, dd MMM", Locale.getDefault())
        val today = dayFormat.format(Date())
        val tomorrow = dayFormat.format(Date(System.currentTimeMillis() + 24L * 60L * 60L * 1000L))
        val dayAfter = dayFormat.format(Date(System.currentTimeMillis() + 2L * 24L * 60L * 60L * 1000L))

        var currentDay: String? = null
        var count = 0

        listings.sortedBy { it.startTimestamp ?: Long.MAX_VALUE }.forEach { program ->
            val start = program.startTimestamp ?: return@forEach
            val dayLabel = dayFormat.format(Date(start))

            if (dayLabel != currentDay) {
                currentDay = dayLabel
                val friendly = when (dayLabel) {
                    today -> "TODAY"
                    tomorrow -> "TOMORROW"
                    dayAfter -> "DAY 3"
                    else -> dayLabel.uppercase(Locale.getDefault())
                }
                addDayHeader(friendly, dayLabel)
            }

            val now = System.currentTimeMillis()
            val isNow = (program.startTimestamp ?: Long.MAX_VALUE) <= now &&
                    (program.stopTimestamp ?: Long.MIN_VALUE) > now
            addProgram(program, isNow)
            count++
        }

        binding.txtEpgStatus.text = "$count programs • 3-day guide"
    }

    private fun addDayHeader(label: String, dateText: String) {
        val header = TextView(this).apply {
            text = "$label  •  $dateText"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14, 12, 14, 10)
            setBackgroundResource(R.drawable.bg_epg_item)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10
                bottomMargin = 6
            }
        }
        binding.epgContainer.addView(header)
    }

    private fun addProgram(program: EpgEntity, isNow: Boolean) {
        val title = program.title?.takeIf { it.isNotBlank() } ?: "No Program Info"
        val description = program.description?.takeIf { it.isNotBlank() }
        val start = formatDateTime(program.startTimestamp)
        val stop = formatDateTime(program.stopTimestamp)

        val text = buildString {
            append(if (isNow) "NOW  •  " else "")
            append(title)
            append("\n")
            append(start)
            if (stop.isNotBlank()) append(" - ").append(stop)
            if (!description.isNullOrBlank()) {
                append("\n")
                append(description)
            }
        }

        val card = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = if (isNow) 16f else 14f
            setPadding(14, 12, 14, 12)
            setBackgroundResource(R.drawable.bg_epg_item)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 6
            }
        }
        binding.epgContainer.addView(card)
    }

    private fun formatDateTime(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0L) return ""
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}

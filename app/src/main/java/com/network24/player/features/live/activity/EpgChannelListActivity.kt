package com.network24.player.features.live.activity

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import android.widget.HorizontalScrollView
import androidx.lifecycle.lifecycleScope
import coil.load
import com.network24.player.R
import com.network24.player.core.base.BaseActivity
import com.network24.player.core.database.DatabaseProvider
import com.network24.player.core.database.entity.EpgEntity
import com.network24.player.core.preferences.PreferenceManager
import com.network24.player.core.sync.SyncManager
import com.network24.player.databinding.ActivityEpgChannelListBinding
import com.network24.player.features.live.models.LiveChannel
import com.network24.player.features.live.repository.LiveRepository
import com.network24.player.features.player.manager.PlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EpgChannelListActivity : BaseActivity() {
    private lateinit var binding: ActivityEpgChannelListBinding
    private lateinit var repository: LiveRepository
    private lateinit var prefs: PreferenceManager
    private lateinit var categoryId: String
    private var categoryName = ""
    private val channels = mutableListOf<LiveChannel>()
    private val epgByChannel = mutableMapOf<String, List<EpgEntity>>()
    private var selectedChannel: LiveChannel? = null
    private var channelWidthDp = 220
    private val minuteWidthDp = 9.0f
    private val rowHeightDp = 64
    private val headerHeightDp = 38
    private var timelineStart = 0L
    private var timelineEnd = 0L
    private var syncingVertical = false
    private var syncingHorizontal = false
    private var lastHorizontalX = 0

    private val nowHandler = Handler(Looper.getMainLooper())
    private val nowLineRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && channels.isNotEmpty()) renderGrid(preserveScroll = true)
            nowHandler.postDelayed(this, 60_000L)
        }
    }

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
        PlayerManager.attach(this, binding.playerView)
        setupChannelColumnWidth()
        setupStickyScrolling()
        loadChannels()
    }

    private fun setupChannelColumnWidth() {
        binding.epgArea.post {
            val density = resources.displayMetrics.density
            val playerWidthPx = (binding.topCard.width - dp(16)).coerceAtLeast(0)
            val targetPx = (playerWidthPx * 0.30f).toInt()
            channelWidthDp = (targetPx / density).toInt().coerceAtLeast(220)

            binding.stickyDate.layoutParams = binding.stickyDate.layoutParams.apply { width = targetPx }
            binding.epgHeaderScroll.layoutParams = binding.epgHeaderScroll.layoutParams.apply {
                width = (binding.epgArea.width - targetPx).coerceAtLeast(0)
                marginStart = targetPx
            }
            binding.channelVerticalScroll.layoutParams = binding.channelVerticalScroll.layoutParams.apply {
                width = targetPx
            }
            binding.channelVerticalScroll.getChildAt(0)?.layoutParams?.width = targetPx
            binding.epgHorizontalScroll.layoutParams = binding.epgHorizontalScroll.layoutParams.apply {
                marginStart = targetPx
            }
            binding.epgArea.requestLayout()
            if (channels.isNotEmpty()) renderGrid(preserveScroll = true)
        }
    }

    private fun setupStickyScrolling() {
        binding.epgHorizontalScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            if (!syncingHorizontal) {
                syncingHorizontal = true
                binding.epgHeaderScroll.scrollTo(scrollX, 0)
                syncingHorizontal = false
            }
            if (scrollX != lastHorizontalX) {
                lastHorizontalX = scrollX
                updateStickyDate(scrollX)
            }
        }
        binding.epgHeaderScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            if (!syncingHorizontal) {
                syncingHorizontal = true
                binding.epgHorizontalScroll.scrollTo(scrollX, 0)
                syncingHorizontal = false
            }
            if (scrollX != lastHorizontalX) {
                lastHorizontalX = scrollX
                updateStickyDate(scrollX)
            }
        }
        binding.epgVerticalScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (!syncingVertical) {
                syncingVertical = true
                binding.channelVerticalScroll.scrollTo(0, scrollY)
                syncingVertical = false
            }
        }
        binding.channelVerticalScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (!syncingVertical) {
                syncingVertical = true
                binding.epgVerticalScroll.scrollTo(0, scrollY)
                syncingVertical = false
            }
        }
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
                    prefs.getServer(), prefs.getUsername(), prefs.getPassword(), categoryId, false
                )
                if (result.isEmpty()) {
                    result = repository.getChannels(
                        prefs.getServer(), prefs.getUsername(), prefs.getPassword(), categoryId, true
                    )
                }
                channels.clear()
                channels.addAll(result)
                if (channels.isEmpty()) {
                    binding.txtEpgStatus.text = "No channels available in this category"
                    return@launch
                }
                loadGuideData()
                nowHandler.postDelayed(nowLineRunnable, 60_000L)
            } catch (e: Exception) {
                binding.txtEpgStatus.text = e.message ?: "Unable to load channels"
            }
        }
    }

    private fun loadGuideData() {
        binding.txtEpgStatus.text = "Loading 2-day guide…"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ids = channels.mapNotNull { it.epg_channel_id?.takeIf(String::isNotBlank) }.distinct()
                val now = System.currentTimeMillis()
                val end = startOfDay(2)
                val db = DatabaseProvider.get(this@EpgChannelListActivity)
                var listings = if (ids.isEmpty()) emptyList() else db.epgDao().getByEpgChannelIds(ids, now, end)
                if (ids.isNotEmpty() && listings.isEmpty()) {
                    val syncResult = SyncManager(this@EpgChannelListActivity).syncFullEpg(force = true)
                    if (syncResult !is com.network24.player.core.sync.SyncResult.Error) {
                        listings = db.epgDao().getByEpgChannelIds(ids, now, end)
                    }
                }
                epgByChannel.clear()
                epgByChannel.putAll(listings.groupBy { it.epgChannelId.orEmpty() })
                withContext(Dispatchers.Main) { renderGrid(false) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.txtEpgStatus.text = e.message ?: "EPG unavailable"
                    renderGrid(false)
                }
            }
        }
    }

    private fun renderGrid(preserveScroll: Boolean) {
        val savedX = if (preserveScroll) binding.epgHorizontalScroll.scrollX else 0
        val savedY = if (preserveScroll) binding.epgVerticalScroll.scrollY else 0
        timelineStart = floorToHalfHour(System.currentTimeMillis())
        timelineEnd = startOfDay(2)
        if (timelineEnd <= timelineStart) timelineEnd = timelineStart + 2L * 24L * 60L * 60L * 1000L
        binding.epgHeaderContainer.removeAllViews()
        binding.channelContainer.removeAllViews()
        binding.epgRowsContainer.removeAllViews()
        renderTimelineHeader()
        channels.forEachIndexed { index, channel ->
            addStickyChannel(channel, index)
            addEpgRow(channel, index)
        }
        binding.txtEpgStatus.text = "${channels.size} channels • 2-day guide"
        if (selectedChannel == null && channels.isNotEmpty()) updateTopInfo(channels.first())
        updateStickyDate(savedX)
        binding.epgArea.post {
            binding.epgHorizontalScroll.scrollTo(savedX.coerceAtLeast(0), 0)
            binding.epgHeaderScroll.scrollTo(savedX.coerceAtLeast(0), 0)
            binding.epgVerticalScroll.scrollTo(0, savedY.coerceAtLeast(0))
            binding.channelVerticalScroll.scrollTo(0, savedY.coerceAtLeast(0))
            updateStickyDate(binding.epgHorizontalScroll.scrollX)
        }
    }

    private fun renderTimelineHeader() {
        val totalMinutes = ((timelineEnd - timelineStart) / 60_000L).coerceAtLeast(30L)
        val timeline = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val time = Calendar.getInstance().apply { timeInMillis = timelineStart }
        var elapsed = 0L
        while (elapsed < totalMinutes) {
            val label = TextView(this).apply {
                text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(time.time)
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(190, 195, 255))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(4), 0, dp(4), 0)
                background = roundedBackground(false, false)
            }
            timeline.addView(label, LinearLayout.LayoutParams((30L * minuteWidthDp).toInt(), dp(headerHeightDp)))
            time.add(Calendar.MINUTE, 30)
            elapsed += 30L
        }
        binding.epgHeaderContainer.addView(timeline)
    }

    private fun addStickyChannel(channel: LiveChannel, index: Int) {
        val panel = createChannelPanel(channel, index)
        binding.channelContainer.addView(panel, LinearLayout.LayoutParams(dp(channelWidthDp), dp(rowHeightDp)))
    }

    private fun addEpgRow(channel: LiveChannel, index: Int) {
        val timeline = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val programs = epgByChannel[channel.epg_channel_id.orEmpty()].orEmpty().filter {
            (it.stopTimestamp ?: 0L) > timelineStart && (it.startTimestamp ?: Long.MAX_VALUE) < timelineEnd
        }.sortedBy { it.startTimestamp ?: Long.MAX_VALUE }
        if (programs.isEmpty()) addNoInformationBlock(timeline, timelineEnd - timelineStart) else {
            var cursor = timelineStart
            programs.forEach { program ->
                val start = (program.startTimestamp ?: cursor).coerceIn(timelineStart, timelineEnd)
                val stop = (program.stopTimestamp ?: (start + 30L * 60L * 1000L)).coerceIn(timelineStart, timelineEnd)
                if (start > cursor) { addEmptyBlock(timeline, start - cursor); cursor = start }
                if (stop > start) { addProgramBlock(timeline, channel, program, stop - start); cursor = stop }
            }
            if (cursor < timelineEnd) addEmptyBlock(timeline, timelineEnd - cursor)
        }
        val rowFrame = FrameLayout(this)
        rowFrame.addView(timeline, FrameLayout.LayoutParams(-2, dp(rowHeightDp)))
        if (isTodayTimeline()) addNowLine(rowFrame, timelineStart, rowHeightDp)
        binding.epgRowsContainer.addView(rowFrame, LinearLayout.LayoutParams(-2, dp(rowHeightDp)))
    }

    private fun createChannelPanel(channel: LiveChannel, index: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = channelBackground(channel, false)
            setOnFocusChangeListener { v, hasFocus -> v.background = channelBackground(channel, hasFocus); if (hasFocus) updateTopInfo(channel) }
            setOnClickListener { playChannel(channel) }

            val logo = ImageView(this@EpgChannelListActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                load(channel.stream_icon) { placeholder(R.drawable.app_logo); error(R.drawable.app_logo) }
            }
            addView(logo, LinearLayout.LayoutParams(dp(62), dp(52)).apply { marginEnd = dp(10) })

            val name = TextView(this@EpgChannelListActivity).apply {
                text = channel.name ?: "Unknown CHANNEL"
                setTextColor(Color.WHITE)
                textSize = 15f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            addView(name, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun addNoInformationBlock(parent: LinearLayout, durationMs: Long) {
        val minutes = (durationMs / 60_000L).coerceAtLeast(5L)
        val cardWidth = (minutes * minuteWidthDp).toInt().coerceAtLeast(dp(120))
        val card = TextView(this).apply {
            text = "No Information"
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(12), dp(4), dp(12), dp(4))
            maxLines = 1
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            background = roundedBackground(false, false)
        }
        parent.addView(card, LinearLayout.LayoutParams(cardWidth, dp(rowHeightDp)).apply { marginEnd = dp(2) })
    }

    private fun addEmptyBlock(parent: LinearLayout, durationMs: Long) {
        val minutes = (durationMs / 60_000L).coerceAtLeast(5L)
        parent.addView(View(this), LinearLayout.LayoutParams((minutes * minuteWidthDp).toInt().coerceAtLeast(dp(18)), dp(rowHeightDp)))
    }

    private fun addProgramBlock(parent: LinearLayout, channel: LiveChannel, program: EpgEntity, durationMs: Long) {
        val now = System.currentTimeMillis()
        val start = program.startTimestamp ?: Long.MAX_VALUE
        val stop = program.stopTimestamp ?: Long.MIN_VALUE
        val isNow = start <= now && stop > now
        val title = program.title?.takeIf { it.isNotBlank() } ?: "No Program Info"
        val minutes = (durationMs / 60_000L).coerceAtLeast(5L)
        val cardWidth = (minutes * minuteWidthDp).toInt().coerceAtLeast(dp(55))
        val card = FrameLayout(this).apply {
            isFocusable = true
            isClickable = true
            background = programBackground(channel, program, isNow, false)
            setOnFocusChangeListener { v, hasFocus -> v.background = programBackground(channel, program, isNow, hasFocus); if (hasFocus) updateTopInfo(channel, program) }
            setOnClickListener { playChannel(channel, program) }
        }
        val text = TextView(this).apply {
            this.text = title
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            textSize = if (isNow) 15f else 14f
            setPadding(dp(10), dp(4), dp(10), dp(7))
            maxLines = 1
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        card.addView(text, FrameLayout.LayoutParams(-1, -1))
        if (isNow && stop > start) {
            val progress = ((now - start).toFloat() / (stop - start).toFloat()).coerceIn(0f, 1f)
            val progressWidth = (cardWidth * progress).toInt().coerceAtLeast(dp(3))
            val progressLine = View(this).apply { setBackgroundColor(Color.WHITE) }
            card.addView(progressLine, FrameLayout.LayoutParams(progressWidth, dp(3), Gravity.BOTTOM or Gravity.START))
        }
        parent.addView(card, LinearLayout.LayoutParams(cardWidth, dp(rowHeightDp)).apply { marginEnd = dp(2) })
    }

    private fun addNowLine(parent: FrameLayout, start: Long, heightDp: Int) {
        val now = System.currentTimeMillis()
        if (now < start || now >= timelineEnd) return
        val minutes = ((now - start).coerceAtLeast(0L) / 60_000L).toFloat()
        val left = (minutes * minuteWidthDp).toInt()
        val line = View(this).apply { setBackgroundColor(Color.rgb(255, 152, 0)) }
        val params = FrameLayout.LayoutParams(dp(2), dp(heightDp), Gravity.TOP or Gravity.START)
        params.leftMargin = left
        parent.addView(line, params)
    }

    private fun updateStickyDate(scrollX: Int) {
        val safeMinuteWidth = minuteWidthDp.coerceAtLeast(1f)
        val minutesFromStart = scrollX / safeMinuteWidth
        val timestamp = timelineStart + (minutesFromStart * 60_000L).toLong()
        val date = Date(timestamp.coerceAtMost(timelineEnd - 1L))
        binding.stickyDate.text = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(date).uppercase(Locale.getDefault())
    }

    private fun isTodayTimeline(): Boolean = timelineStart <= System.currentTimeMillis() && timelineEnd > System.currentTimeMillis()

    private fun playChannel(channel: LiveChannel, program: EpgEntity? = null) {
        val streamId = channel.stream_id ?: return
        val server = prefs.getServer().trim().trimEnd('/')
        val url = "$server/live/${prefs.getUsername()}/${prefs.getPassword()}/$streamId.m3u8"
        PlayerManager.play(this, binding.playerView, url)
        selectedChannel = channel
        updateTopInfo(channel, program)
        renderGrid(preserveScroll = true)
    }

    private fun updateTopInfo(channel: LiveChannel, program: EpgEntity? = null) {
        binding.txtPlayerChannel.text = channel.name ?: "Unknown Channel"
        val now = System.currentTimeMillis()
        val current = program ?: epgByChannel[channel.epg_channel_id.orEmpty()].orEmpty().firstOrNull {
            (it.startTimestamp ?: Long.MAX_VALUE) <= now && (it.stopTimestamp ?: Long.MIN_VALUE) > now
        }
        binding.txtChannelTitle.text = current?.title ?: "No current program"
        binding.txtDescription.text = current?.description.orEmpty()
        binding.txtEpgStatus.text = "EPG guide"
    }

    private fun channelBackground(channel: LiveChannel, focused: Boolean): GradientDrawable {
        val selected = selectedChannel?.stream_id != null && selectedChannel?.stream_id == channel.stream_id
        return roundedBackground(focused || selected, selected)
    }

    private fun programBackground(channel: LiveChannel, program: EpgEntity, isNow: Boolean, focused: Boolean): GradientDrawable {
        val selected = selectedChannel?.stream_id != null && selectedChannel?.stream_id == channel.stream_id
        if (isNow) return GradientDrawable().apply {
            cornerRadius = dp(4).toFloat()
            setColor(Color.rgb(255, 136, 0))
            setStroke(dp(if (focused || selected) 3 else 2), if (focused || selected) Color.WHITE else Color.rgb(255, 193, 7))
        }
        return roundedBackground(focused || selected, selected)
    }

    private fun roundedBackground(active: Boolean, strong: Boolean): GradientDrawable {
        val bg = if (strong) Color.rgb(42, 34, 88) else if (active) Color.rgb(34, 32, 68) else Color.rgb(18, 18, 45)
        val stroke = if (strong) Color.rgb(255, 193, 7) else if (active) Color.rgb(120, 130, 255) else Color.rgb(55, 58, 100)
        return GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(bg); setStroke(dp(if (strong) 3 else 2), stroke) }
    }

    private fun startOfDay(offset: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_YEAR, offset)
    }.timeInMillis

    private fun floorToHalfHour(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0); cal.set(Calendar.MINUTE, if (cal.get(Calendar.MINUTE) < 30) 0 else 30)
        return cal.timeInMillis
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        nowHandler.removeCallbacks(nowLineRunnable)
        super.onDestroy()
    }
}

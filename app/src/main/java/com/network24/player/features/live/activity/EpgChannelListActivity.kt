package com.network24.player.features.live.activity

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
    private var selectedDay = 0
    private val channelWidthDp = 220
    private val minuteWidthDp = 3.0f

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
        loadChannels()
    }

    private fun loadChannels() {
        binding.txtEpgStatus.text = "Loading channels…"
        lifecycleScope.launch {
            try {
                if (categoryId.isBlank()) { binding.txtEpgStatus.text = "Invalid category"; return@launch }
                var result = repository.getChannels(prefs.getServer(), prefs.getUsername(), prefs.getPassword(), categoryId, false)
                if (result.isEmpty()) result = repository.getChannels(prefs.getServer(), prefs.getUsername(), prefs.getPassword(), categoryId, true)
                channels.clear(); channels.addAll(result)
                if (channels.isEmpty()) { binding.txtEpgStatus.text = "No channels available in this category"; return@launch }
                buildDateSelector(); loadGuideData()
            } catch (e: Exception) { binding.txtEpgStatus.text = e.message ?: "Unable to load channels" }
        }
    }

    private fun loadGuideData() {
        binding.txtEpgStatus.text = "Loading 3-day guide…"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ids = channels.mapNotNull { it.epg_channel_id?.takeIf(String::isNotBlank) }.distinct()
                if (ids.isEmpty()) { withContext(Dispatchers.Main) { renderGrid() }; return@launch }
                val db = DatabaseProvider.get(this@EpgChannelListActivity)
                val now = System.currentTimeMillis(); val end = now + 3L * 24L * 60L * 60L * 1000L
                var listings = db.epgDao().getByEpgChannelIds(ids, now, end)
                if (listings.isEmpty()) {
                    val syncResult = SyncManager(this@EpgChannelListActivity).syncFullEpg(force = true)
                    if (syncResult !is com.network24.player.core.sync.SyncResult.Error) listings = db.epgDao().getByEpgChannelIds(ids, now, end)
                }
                epgByChannel.clear(); epgByChannel.putAll(listings.groupBy { it.epgChannelId.orEmpty() })
                withContext(Dispatchers.Main) { renderGrid() }
            } catch (e: Exception) { withContext(Dispatchers.Main) { binding.txtEpgStatus.text = e.message ?: "EPG unavailable"; renderGrid() } }
        }
    }

    private fun buildDateSelector() {
        binding.dateSelector.removeAllViews()
        listOf("TODAY", "TOMORROW", "DAY 3").forEachIndexed { index, label ->
            val button = TextView(this).apply {
                text = label; gravity = Gravity.CENTER; isFocusable = true; isClickable = true; setTextColor(Color.WHITE); textSize = 14f
                setPadding(dp(28), 0, dp(28), 0); background = roundedBackground(index == selectedDay, false)
                setOnFocusChangeListener { v, hasFocus -> v.background = roundedBackground(hasFocus || index == selectedDay, false) }
                setOnClickListener { selectedDay = index; buildDateSelector(); renderGrid() }
            }
            binding.dateSelector.addView(button, LinearLayout.LayoutParams(dp(150), dp(42)).apply { marginEnd = dp(6) })
        }
    }

    private fun renderGrid() {
        binding.gridContainer.removeAllViews()
        val dayStart = startOfDay(selectedDay); val dayEnd = dayStart + 24L * 60L * 60L * 1000L
        val timelineStart = if (selectedDay == 0) System.currentTimeMillis() else dayStart
        addTimelineHeader(timelineStart, dayStart, dayEnd)
        channels.forEachIndexed { index, channel -> addChannelRow(channel, index, timelineStart, dayEnd) }
        binding.txtEpgStatus.text = "${channels.size} channels • 3-day guide"
        if (selectedChannel == null && channels.isNotEmpty()) updateTopInfo(channels.first())
    }

    private fun addTimelineHeader(timelineStart: Long, dayStart: Long, dayEnd: Long) {
        val row = horizontalRow(); row.addView(channelHeader("CHANNELS"))
        val timeline = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val time = Calendar.getInstance().apply { timeInMillis = if (selectedDay == 0) roundUpToNextHalfHour(timelineStart) else dayStart }
        val totalMinutes = ((dayEnd - timelineStart) / 60000L).coerceAtLeast(30L); val slots = (totalMinutes / 30L + 2L).toInt().coerceAtMost(50)
        for (slot in 0 until slots) {
            if (time.timeInMillis >= dayEnd) break
            val label = TextView(this).apply { text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(time.time); gravity = Gravity.CENTER; setTextColor(Color.rgb(190,195,255)); textSize = 13f; setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(dp(4),0,dp(4),0) }
            timeline.addView(label, LinearLayout.LayoutParams(dp(90), dp(38))); time.add(Calendar.MINUTE, 30)
        }
        row.addView(timeline); binding.gridContainer.addView(row)
    }

    private fun addChannelRow(channel: LiveChannel, index: Int, timelineStart: Long, dayEnd: Long) {
        val row = horizontalRow()
        val channelPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true; setPadding(dp(8),dp(4),dp(8),dp(4)); background = channelBackground(channel,false)
            setOnFocusChangeListener { v, hasFocus -> v.background = channelBackground(channel,hasFocus); if (hasFocus) updateTopInfo(channel) }
            setOnClickListener { playChannel(channel) }
        }
        val number = TextView(this).apply { text = "${index+1}"; gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize=14f; setTypeface(typeface,android.graphics.Typeface.BOLD) }
        channelPanel.addView(number, LinearLayout.LayoutParams(dp(28),-1))
        val logo = ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_INSIDE; load(channel.stream_icon){placeholder(R.drawable.app_logo);error(R.drawable.app_logo)} }
        channelPanel.addView(logo, LinearLayout.LayoutParams(dp(62),dp(52)).apply{marginEnd=dp(6)})
        val name = TextView(this).apply { text=channel.name ?: "Unknown CHANNEL"; setTextColor(Color.WHITE); textSize=15f; maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END }
        channelPanel.addView(name, LinearLayout.LayoutParams(0,-1,1f)); row.addView(channelPanel,LinearLayout.LayoutParams(dp(channelWidthDp),dp(64)))

        val timeline = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val programs = epgByChannel[channel.epg_channel_id.orEmpty()].orEmpty().filter{(it.stopTimestamp?:0L)>timelineStart && (it.startTimestamp?:Long.MAX_VALUE)<dayEnd}.sortedBy{it.startTimestamp?:Long.MAX_VALUE}
        var cursor=timelineStart
        programs.forEach { program ->
            val start=(program.startTimestamp?:cursor).coerceIn(timelineStart,dayEnd); val stop=(program.stopTimestamp?:start+30*60*1000L).coerceIn(timelineStart,dayEnd)
            if(start>cursor){addEmptyBlock(timeline,start-cursor);cursor=start}; if(stop>start){addProgramBlock(timeline,channel,program,stop-start);cursor=stop}
        }
        if(cursor<dayEnd)addEmptyBlock(timeline,dayEnd-cursor); row.addView(timeline); binding.gridContainer.addView(row)
    }

    private fun addEmptyBlock(parent: LinearLayout,durationMs:Long){val minutes=(durationMs/60000L).coerceAtLeast(5L);parent.addView(View(this),LinearLayout.LayoutParams((minutes*minuteWidthDp).toInt().coerceAtLeast(dp(18)),dp(64)))}

    private fun addProgramBlock(parent:LinearLayout,channel:LiveChannel,program:EpgEntity,durationMs:Long){
        val now=System.currentTimeMillis();val isNow=(program.startTimestamp?:Long.MAX_VALUE)<=now&&(program.stopTimestamp?:Long.MIN_VALUE)>now;val title=program.title?.takeIf{it.isNotBlank()}?:"No Program Info"
        val card=TextView(this).apply{
            text=if(isNow)"NOW\n$title"else title;gravity=Gravity.CENTER_VERTICAL;setTextColor(Color.WHITE);textSize=if(isNow)15f else 14f;setPadding(dp(10),dp(4),dp(10),dp(4));background=programBackground(channel,program,isNow,false);isFocusable=true;isClickable=true
            setOnFocusChangeListener{v,hasFocus->v.background=programBackground(channel,program,isNow,hasFocus);if(hasFocus)updateTopInfo(channel,program)};setOnClickListener{playChannel(channel,program)}
        }
        val minutes=(durationMs/60000L).coerceAtLeast(5L);parent.addView(card,LinearLayout.LayoutParams((minutes*minuteWidthDp).toInt().coerceAtLeast(dp(55)),dp(64)).apply{marginEnd=dp(2)})
    }

    private fun channelHeader(text:String):TextView=TextView(this).apply{this.text=text;gravity=Gravity.CENTER;setTextColor(Color.rgb(190,195,255));textSize=14f;setTypeface(typeface,android.graphics.Typeface.BOLD);background=roundedBackground(false,false)}.also{it.layoutParams=LinearLayout.LayoutParams(dp(channelWidthDp),dp(38))}
    private fun horizontalRow()=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}

    private fun playChannel(channel:LiveChannel,program:EpgEntity?=null){val streamId=channel.stream_id?:return;val server=prefs.getServer().trim().trimEnd('/');val url="$server/live/${prefs.getUsername()}/${prefs.getPassword()}/$streamId.m3u8";PlayerManager.play(this,binding.playerView,url);selectedChannel=channel;updateTopInfo(channel,program);refreshGridSelection()}

    private fun updateTopInfo(channel:LiveChannel,program:EpgEntity?=null){
        binding.txtPlayerChannel.text=channel.name?:"Unknown Channel";val now=System.currentTimeMillis();val current=program?:epgByChannel[channel.epg_channel_id.orEmpty()].orEmpty().firstOrNull{(it.startTimestamp?:Long.MAX_VALUE)<=now&&(it.stopTimestamp?:Long.MIN_VALUE)>now};binding.txtChannelTitle.text=current?.title?:"No current program";binding.txtDescription.text=current?.description.orEmpty();binding.txtEpgStatus.text="EPG guide"
    }

    private fun refreshGridSelection(){renderGrid()}
    private fun channelBackground(channel:LiveChannel,focused:Boolean):GradientDrawable{val selected=selectedChannel?.stream_id!=null&&selectedChannel?.stream_id==channel.stream_id;return roundedBackground(focused||selected,selected)}
    private fun programBackground(channel:LiveChannel,program:EpgEntity,isNow:Boolean,focused:Boolean):GradientDrawable{val selected=selectedChannel?.stream_id!=null&&selectedChannel?.stream_id==channel.stream_id;return roundedBackground(focused||isNow||selected,isNow||selected)}
    private fun roundedBackground(active:Boolean,strong:Boolean):GradientDrawable{val bg=if(strong)Color.rgb(42,34,88)else if(active)Color.rgb(34,32,68)else Color.rgb(18,18,45);val stroke=if(strong)Color.rgb(255,193,7)else if(active)Color.rgb(120,130,255)else Color.rgb(55,58,100);return GradientDrawable().apply{cornerRadius=dp(6).toFloat();setColor(bg);setStroke(dp(if(strong)3 else 2),stroke)}}
    private fun startOfDay(offset:Int):Long=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0);add(Calendar.DAY_OF_YEAR,offset)}.timeInMillis
    private fun roundUpToNextHalfHour(timestamp:Long):Long{val cal=Calendar.getInstance().apply{timeInMillis=timestamp};cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);val minute=cal.get(Calendar.MINUTE);cal.set(Calendar.MINUTE,if(minute%30==0)minute else minute+(30-minute%30));return cal.timeInMillis}
    private fun dp(value:Int):Int=(value*resources.displayMetrics.density).toInt()
}

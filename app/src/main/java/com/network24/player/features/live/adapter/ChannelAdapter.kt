package com.network24.player.features.live.adapter

import android.annotation.SuppressLint
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.network24.player.R
import com.network24.player.core.database.DatabaseProvider
import com.network24.player.databinding.ItemChannelBinding
import com.network24.player.features.live.models.LiveChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class ChannelAdapter(
    private val channels: MutableList<LiveChannel>,
    // 🔥 NAYA: Favorites ID list ko track karne ke liye
    private var favoriteIds: Set<String> = emptySet(),
    private val onFocused: (LiveChannel, Int) -> Unit,
    private val onClicked: (LiveChannel, Int) -> Unit,
    private val onLongClicked: ((LiveChannel, Int) -> Unit)? = null
) : RecyclerView.Adapter<ChannelAdapter.ChannelVH>() {

    private var playingPosition = RecyclerView.NO_POSITION
    private val epgScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var epgJob: Job? = null
    private var epgChannelId: String? = null

    inner class ChannelVH(
        val binding: ItemChannelBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ChannelVH {
        return ChannelVH(
            ItemChannelBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount() = channels.size

    override fun onBindViewHolder(
        holder: ChannelVH,
        position: Int
    ) {
        val channel = channels[position]
        holder.binding.txtName.text = channel.name

        holder.binding.imgLogo.load(
            channel.stream_icon
        ) {
            placeholder(R.drawable.app_logo)
            error(R.drawable.app_logo)
            crossfade(true)
        }

        //------------------------------------
        // Playing Icon
        //------------------------------------
        holder.binding.imgPlaying.visibility =
            if (position == playingPosition)
                View.VISIBLE
            else
                View.GONE

        //------------------------------------
        // 🔥 Favorite Heart Icon Logic
        //------------------------------------
        val isFavorite = favoriteIds.contains(channel.stream_id.toString())
        holder.binding.imgFavorite.visibility =
            if (isFavorite) View.VISIBLE else View.GONE

        //------------------------------------
        // Focus Indicator + EPG Preview
        //------------------------------------
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnFocusChangeListener

                // Fire Stick / Android TV: update the EPG immediately when the
                // user moves to a channel. No OK/select click is required.
                loadSideEpg(holder.itemView, channel)
                onFocused(channel, pos)
            }
        }

        //------------------------------------
        // Click and Long Click
        //------------------------------------
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION)
                return@setOnClickListener
            onClicked(
                channel,
                pos
            )
        }

        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onLongClicked?.invoke(channel, pos)
            }
            true
        }
    }

    /**
     * Shows the upcoming schedule for the focused channel in the EPG card.
     * This intentionally runs on focus rather than click so Fire Stick users
     * can browse channels and see the guide without selecting/playing them.
     */
    private fun loadSideEpg(itemView: View, channel: LiveChannel) {
        val epgId = channel.epg_channel_id ?: channel.stream_id?.toString() ?: return
        if (epgId == epgChannelId) return

        epgChannelId = epgId
        epgJob?.cancel()

        epgJob = epgScope.launch {
            try {
                val root = itemView.rootView
                val card = root.findViewById<ViewGroup>(R.id.cardEpg) ?: return@launch
                val programs = DatabaseProvider.get(itemView.context)
                    .epgDao()
                    .getByEpgChannelId(epgId)

                val now = System.currentTimeMillis()
                val visible = programs
                    .filter {
                        it.startTimestamp != null &&
                            it.stopTimestamp != null &&
                            it.stopTimestamp!! > now
                    }
                    .sortedBy { it.startTimestamp }
                    .take(6)

                val content = LinearLayout(itemView.context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(
                        dp(itemView, 12),
                        dp(itemView, 8),
                        dp(itemView, 12),
                        dp(itemView, 8)
                    )
                }

                val header = TextView(itemView.context).apply {
                    text = "TV GUIDE"
                    setTextColor(Color.rgb(100, 181, 246))
                    textSize = 10f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                content.addView(header)

                if (visible.isEmpty()) {
                    content.addView(TextView(itemView.context).apply {
                        text = "EPG unavailable"
                        setTextColor(Color.LTGRAY)
                        textSize = 12f
                        setPadding(0, dp(itemView, 6), 0, 0)
                    })
                } else {
                    visible.forEachIndexed { index, program ->
                        val row = LinearLayout(itemView.context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(
                                0,
                                dp(itemView, 3),
                                0,
                                dp(itemView, 3)
                            )
                        }

                        val time = TextView(itemView.context).apply {
                            text = "${formatTime(program.startTimestamp)} - ${formatTime(program.stopTimestamp)}"
                            setTextColor(Color.LTGRAY)
                            textSize = 10f
                        }

                        val title = TextView(itemView.context).apply {
                            text = program.title ?: "No Program Info"
                            val current = index == 0 && program.startTimestamp!! <= now
                            setTextColor(if (current) Color.WHITE else Color.LTGRAY)
                            textSize = if (current) 13f else 12f
                            setTypeface(
                                typeface,
                                if (current) android.graphics.Typeface.BOLD
                                else android.graphics.Typeface.NORMAL
                            )
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                        }

                        row.addView(
                            time,
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                0.42f
                            )
                        )
                        row.addView(
                            title,
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                0.58f
                            )
                        )
                        content.addView(row)
                    }
                }

                card.removeAllViews()
                card.addView(
                    content,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            } catch (_: Exception) {
                // Keep the existing EPG card intact if loading fails.
            }
        }
    }

    private fun formatTime(timeMs: Long?): String {
        if (timeMs == null || timeMs == 0L) return ""
        return try {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(timeMs)
        } catch (_: Exception) {
            ""
        }
    }

    private fun dp(view: View, value: Int): Int =
        (value * view.resources.displayMetrics.density).toInt()

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(
        list: List<LiveChannel>
    ) {
        channels.clear()
        channels.addAll(list)
        notifyDataSetChanged()
    }

    // Update only the channel rows whose favorite state actually changed.
    // Do not call notifyDataSetChanged() here: doing so can cause RecyclerView
    // on Fire TV/Fire Stick to lose its current DPAD focus and return to the top.
    fun updateFavorites(newFavIds: Set<String>) {
        val oldFavIds = favoriteIds
        favoriteIds = newFavIds

        for (index in channels.indices) {
            val streamId = channels[index].stream_id?.toString() ?: continue
            val wasFavorite = oldFavIds.contains(streamId)
            val isFavorite = newFavIds.contains(streamId)
            if (wasFavorite != isFavorite) {
                notifyItemChanged(index)
            }
        }
    }

    fun setPlaying(
        position: Int
    ) {
        val old = playingPosition
        playingPosition = position
        if (old != RecyclerView.NO_POSITION)
            notifyItemChanged(old)
        if (position != RecyclerView.NO_POSITION)
            notifyItemChanged(position)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        epgJob?.cancel()
        epgScope.cancel()
        super.onDetachedFromRecyclerView(recyclerView)
    }
}

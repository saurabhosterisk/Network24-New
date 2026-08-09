package com.network24.player.features.live.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private var favoriteIds: Set<String> = emptySet(),
    private val onFocused: (LiveChannel, Int) -> Unit,
    private val onClicked: (LiveChannel, Int) -> Unit,
    private val onLongClicked: ((LiveChannel, Int) -> Unit)? = null
) : RecyclerView.Adapter<ChannelAdapter.ChannelVH>() {

    private var playingPosition = RecyclerView.NO_POSITION
    private val epgScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var epgJob: Job? = null
    private var epgChannelId: String? = null

    inner class ChannelVH(val binding: ItemChannelBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelVH {
        return ChannelVH(
            ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun getItemCount() = channels.size

    override fun onBindViewHolder(holder: ChannelVH, position: Int) {
        val channel = channels[position]
        holder.binding.txtName.text = channel.name

        holder.binding.imgLogo.load(channel.stream_icon) {
            placeholder(R.drawable.app_logo)
            error(R.drawable.app_logo)
            crossfade(true)
        }

        holder.binding.imgPlaying.visibility =
            if (position == playingPosition) View.VISIBLE else View.GONE

        val isFavorite = favoriteIds.contains(channel.stream_id.toString())
        holder.binding.imgFavorite.visibility =
            if (isFavorite) View.VISIBLE else View.GONE

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (!hasFocus) return@setOnFocusChangeListener

            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnFocusChangeListener

            // Fire Stick / Android TV: update EPG immediately when the channel
            // receives DPAD focus. No OK/Select press is required.
            loadSideEpg(view, channel)
            onFocused(channel, pos)
        }

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            onClicked(channel, pos)
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
     * Updates the existing EPG TextViews on focus.
     * The original compact EPG layout stays untouched and no extra TV GUIDE
     * heading/card is created.
     */
    private fun loadSideEpg(itemView: View, channel: LiveChannel) {
        val epgId = channel.epg_channel_id ?: channel.stream_id?.toString() ?: return
        if (epgId == epgChannelId) return

        epgChannelId = epgId
        epgJob?.cancel()

        epgJob = epgScope.launch {
            try {
                val root = itemView.rootView
                val nowTitle = root.findViewById<TextView>(R.id.txtNowTitle) ?: return@launch
                val nowTime = root.findViewById<TextView>(R.id.txtNowTime) ?: return@launch
                val nextTitle = root.findViewById<TextView>(R.id.txtNextTitle) ?: return@launch
                val nextTime = root.findViewById<TextView>(R.id.txtNextTime) ?: return@launch
                val overlayProgram = root.findViewById<TextView>(R.id.txtOverlayProgram)

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

                val current = visible.firstOrNull { program ->
                    val start = program.startTimestamp ?: 0L
                    val stop = program.stopTimestamp ?: 0L
                    start <= now && stop > now
                } ?: visible.firstOrNull()

                val next = visible.firstOrNull { program ->
                    program !== current && (program.startTimestamp ?: 0L) > now
                }

                if (current != null) {
                    nowTitle.text = current.title ?: "No Program Info"
                    nowTime.text = "${formatTime(current.startTimestamp)} - ${formatTime(current.stopTimestamp)}"
                    overlayProgram?.text = current.title ?: ""
                } else {
                    nowTitle.text = "No EPG"
                    nowTime.text = ""
                    overlayProgram?.text = ""
                }

                if (next != null) {
                    nextTitle.text = next.title ?: ""
                    nextTime.text = "${formatTime(next.startTimestamp)} - ${formatTime(next.stopTimestamp)}"
                } else {
                    nextTitle.text = ""
                    nextTime.text = ""
                }
            } catch (_: Exception) {
                // Keep the current EPG content if loading fails.
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

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(list: List<LiveChannel>) {
        channels.clear()
        channels.addAll(list)
        epgChannelId = null
        notifyDataSetChanged()
    }

    // Update only rows whose favorite state changed so Fire Stick DPAD focus is preserved.
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

    fun setPlaying(position: Int) {
        val old = playingPosition
        playingPosition = position
        if (old != RecyclerView.NO_POSITION) notifyItemChanged(old)
        if (position != RecyclerView.NO_POSITION) notifyItemChanged(position)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        epgJob?.cancel()
        epgScope.cancel()
        super.onDetachedFromRecyclerView(recyclerView)
    }
}

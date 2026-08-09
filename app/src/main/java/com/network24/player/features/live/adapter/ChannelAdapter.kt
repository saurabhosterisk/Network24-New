package com.network24.player.features.live.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.network24.player.R
import com.network24.player.databinding.ItemChannelBinding
import com.network24.player.features.live.models.LiveChannel

class ChannelAdapter(
    private val channels: MutableList<LiveChannel>,
    // 🔥 NAYA: Favorites ID list ko track karne ke liye
    private var favoriteIds: Set<String> = emptySet(),
    private val onFocused: (LiveChannel, Int) -> Unit,
    private val onClicked: (LiveChannel, Int) -> Unit,
    private val onLongClicked: ((LiveChannel, Int) -> Unit)? = null
) : RecyclerView.Adapter<ChannelAdapter.ChannelVH>() {

    private var playingPosition = RecyclerView.NO_POSITION

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
        // Focus Indicator
        //------------------------------------
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnFocusChangeListener
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
}

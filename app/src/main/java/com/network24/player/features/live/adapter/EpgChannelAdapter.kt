package com.network24.player.features.live.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.network24.player.R
import com.network24.player.databinding.ItemEpgChannelBinding
import com.network24.player.features.live.models.LiveChannel

class EpgChannelAdapter(
    private val channels: MutableList<LiveChannel>,
    private val onFocused: (LiveChannel, Int) -> Unit,
    private val onClicked: (LiveChannel, Int) -> Unit
) : RecyclerView.Adapter<EpgChannelAdapter.VH>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class VH(val binding: ItemEpgChannelBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemEpgChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = channels.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val channel = channels[position]
        holder.binding.txtName.text = channel.name ?: "Unknown Channel"
        holder.binding.imgLogo.load(channel.stream_icon) {
            placeholder(R.drawable.app_logo)
            error(R.drawable.app_logo)
            crossfade(true)
        }
        holder.binding.imgPlaying.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE

        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) return@setOnFocusChangeListener
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onFocused(channel, pos)
        }

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                selectedPosition = pos
                notifyDataSetChanged()
                onClicked(channel, pos)
            }
        }
    }

    fun updateData(list: List<LiveChannel>) {
        // EpgChannelListActivity owns the same MutableList instance that is passed
        // into this adapter. Copy the incoming list BEFORE clearing the adapter's
        // list; otherwise updateData(channels) would clear the source list itself
        // and then add zero items back, leaving the RecyclerView empty.
        val snapshot = list.toList()
        channels.clear()
        channels.addAll(snapshot)
        selectedPosition = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }

    fun setSelected(position: Int) {
        val old = selectedPosition
        selectedPosition = position
        if (old != RecyclerView.NO_POSITION) notifyItemChanged(old)
        if (position != RecyclerView.NO_POSITION) notifyItemChanged(position)
    }
}

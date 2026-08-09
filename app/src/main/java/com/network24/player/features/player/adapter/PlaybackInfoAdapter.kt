package com.network24.player.features.player.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.network24.player.databinding.ItemPlaybackInfoBinding
import com.network24.player.features.player.models.PlaybackInfoItem

class PlaybackInfoAdapter(
    private val items: MutableList<PlaybackInfoItem>
) : RecyclerView.Adapter<PlaybackInfoAdapter.InfoViewHolder>() {

    inner class InfoViewHolder(
        private val binding: ItemPlaybackInfoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PlaybackInfoItem) {
            binding.txtTitle.text = item.title
            binding.txtValue.text = item.value
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InfoViewHolder {

        val binding = ItemPlaybackInfoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return InfoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InfoViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateValue(position: Int, value: String) {

        if (position !in items.indices) return

        if (items[position].value == value) return

        items[position].value = value
        notifyItemChanged(position)
    }

    fun updateAll(newItems: List<PlaybackInfoItem>) {

        newItems.forEachIndexed { index, item ->

            if (index >= items.size) return@forEachIndexed

            if (items[index].value != item.value) {

                items[index].value = item.value
                notifyItemChanged(index)

            }
        }
    }
}
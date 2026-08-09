package com.network24.player.features.live.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.network24.player.databinding.ItemChannelDrawerBinding
import com.network24.player.features.live.models.LiveChannel

class ChannelDrawerAdapter(
    private val channels: List<LiveChannel>,
    private val onClick: (Int) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<ChannelDrawerAdapter.ViewHolder>() {

    var selectedPosition = 0
        private set

    inner class ViewHolder(
        val binding: ItemChannelDrawerBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val binding = ItemChannelDrawerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ViewHolder(binding)
    }

    override fun getItemCount() = channels.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val channel = channels[position]

        holder.binding.txtChannel.text = channel.name.orEmpty()

        holder.binding.imgLogo.load(channel.stream_icon)

        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = true

        holder.itemView.isSelected =
            position == selectedPosition

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->

            if (hasFocus) {

                if (selectedPosition != holder.bindingAdapterPosition) {

                    val old = selectedPosition

                    selectedPosition = holder.bindingAdapterPosition

                    notifyItemChanged(old)
                    notifyItemChanged(selectedPosition)
                }

                onSelectionChanged(selectedPosition)
            }

            view.isSelected =
                hasFocus || holder.bindingAdapterPosition == selectedPosition
        }

        holder.itemView.setOnClickListener {

            onClick(holder.bindingAdapterPosition)
        }
    }

    fun setSelected(position: Int) {

        if (position == selectedPosition) return

        val old = selectedPosition

        selectedPosition = position

        notifyItemChanged(old)
        notifyItemChanged(position)
    }
}
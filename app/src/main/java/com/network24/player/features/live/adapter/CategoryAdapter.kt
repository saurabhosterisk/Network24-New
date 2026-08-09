package com.network24.player.features.live.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.network24.player.databinding.ItemCategoryBinding
import com.network24.player.features.live.models.LiveCategory

class CategoryAdapter(
    private val listener: (LiveCategory) -> Unit,
    private val onLongClick: (LiveCategory) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    private val list = mutableListOf<LiveCategory>()

    inner class ViewHolder(val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.binding.txtCategory.text = item.category_name

        holder.itemView.setOnClickListener { listener(item) }

        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }

        // (optional) focus selector same as favorites
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                holder.binding.cardCategory.strokeWidth = 3
                holder.itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
            } else {
                holder.binding.cardCategory.strokeWidth = 0
                holder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
        }
    }

    fun updateList(newList: List<LiveCategory>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }
}

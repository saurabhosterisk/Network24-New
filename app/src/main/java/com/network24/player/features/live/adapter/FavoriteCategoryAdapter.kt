package com.network24.player.features.live.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.network24.player.databinding.ItemFavoriteCategoryBinding
import com.network24.player.features.live.models.LiveCategory

class FavoriteCategoryAdapter(
    private val columns: Int,
    private val listener: (LiveCategory) -> Unit,
    private val onLongClick: (LiveCategory) -> Unit
) : RecyclerView.Adapter<FavoriteCategoryAdapter.ViewHolder>() {

    private val list = mutableListOf<LiveCategory>()

    inner class ViewHolder(val binding: ItemFavoriteCategoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFavoriteCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        // --- Dynamic width to match grid column width ---
        val displayMetrics = parent.context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        // Activity XML: rvFavorite marginStart+End = 16dp + 16dp, paddingLeft+Right = 4dp + 4dp => 40dp
        val outerSpacePx = (40f * displayMetrics.density).toInt()

        // Item XML: card margin = 8dp (left) + 8dp (right) => 16dp
        val cardMarginPx = (16f * displayMetrics.density).toInt()

        val availableWidth = screenWidth - outerSpacePx
        val spanWidth = availableWidth / columns

        // Important: width WITHOUT margins, to match GridLayoutManager sizing
        binding.root.layoutParams.width = spanWidth - cardMarginPx
        // -----------------------------------------------

        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]

        holder.binding.txtCategoryName.text = item.category_name

        // Normal click
        holder.itemView.setOnClickListener { listener(item) }

        // Long press -> remove from favorites
        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }

        // Focus selector animation
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                holder.binding.cardFavorite.strokeWidth = 3
                holder.itemView.animate()
                    .scaleX(1.05f)
                    .scaleY(1.05f)
                    .setDuration(120)
                    .start()
            } else {
                holder.binding.cardFavorite.strokeWidth = 0
                holder.itemView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .start()
            }
        }
    }

    fun updateList(newList: List<LiveCategory>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }
}
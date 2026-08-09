package com.network24.player.features.chat.adapter

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MentionAdapter(
    private val onSelected: (String) -> Unit
) : RecyclerView.Adapter<MentionAdapter.VH>() {

    private val users = mutableListOf<String>()

    fun submit(list: List<String>) {
        users.clear()
        users.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            minHeight = 48
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 10, 16, 10)
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT
            isFocusable = true
            isClickable = true
        }
        return VH(view, onSelected)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    class VH(
        private val view: TextView,
        private val onSelected: (String) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        fun bind(username: String) {
            view.text = "@$username"
            view.setOnClickListener { onSelected(username) }
        }
    }
}

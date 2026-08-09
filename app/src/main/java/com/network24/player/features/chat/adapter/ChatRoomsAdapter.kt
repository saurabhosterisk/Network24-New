package com.network24.player.features.chat.adapter

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.network24.player.R
import com.network24.player.features.chat.model.ChatRoom

class ChatRoomsAdapter(
    private val onClick: (ChatRoom) -> Unit
) : RecyclerView.Adapter<ChatRoomsAdapter.VH>() {

    private val items = mutableListOf<ChatRoom>()
    private val unread = mutableSetOf<String>()

    // NAYA: Selected room ko track karne ke liye
    private var selectedRoomId: String? = null


    fun setSelectedRoom(roomId: String) {
        val previousSelectedId = selectedRoomId
        selectedRoomId = roomId

        // 1. Purane selected room ka index dhundo aur usko refresh karo (highlight hatane ke liye)
        if (previousSelectedId != null) {
            val previousIndex = items.indexOfFirst { it.id == previousSelectedId }
            if (previousIndex != -1) {
                notifyItemChanged(previousIndex)
            }
        }

        // 2. Naye selected room ka index dhundo aur usko refresh karo (highlight lagane ke liye)
        val newIndex = items.indexOfFirst { it.id == roomId }
        if (newIndex != -1) {
            notifyItemChanged(newIndex)
        }
    }
    fun submit(list: List<ChatRoom>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun setUnread(roomId: String, isUnread: Boolean) {
        val wasUnread = unread.contains(roomId)

        // Agar state change hui hai, tabhi update karo
        if (wasUnread != isUnread) {
            if (isUnread) unread.add(roomId) else unread.remove(roomId)

            val index = items.indexOfFirst { it.id == roomId }
            if (index != -1) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_room, parent, false)
        return VH(v, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val room = items[position]
        val isSelected = room.id == selectedRoomId // Check if this is the active room
        holder.bind(room, unread.contains(room.id), isSelected)
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View, val onClick: (ChatRoom) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val tvEmoji: TextView = itemView.findViewById(R.id.tvEmoji)
        private val tvRoomName: TextView = itemView.findViewById(R.id.tvRoomName)
        private val tvReadOnly: TextView = itemView.findViewById(R.id.tvReadOnly)
        private val badgeUnreadNew: TextView = itemView.findViewById(R.id.badgeUnreadNew)

        fun bind(room: ChatRoom, isUnread: Boolean, isSelected: Boolean) {
            tvEmoji.text = room.emoji
            tvRoomName.text = room.name
            tvReadOnly.visibility = if (room.readOnly) View.VISIBLE else View.GONE

            if (isUnread) {
                tvRoomName.setTypeface(null, Typeface.BOLD)
                tvRoomName.setTextColor(Color.WHITE)
                badgeUnreadNew.visibility = View.VISIBLE
            } else {
                tvRoomName.setTypeface(null, Typeface.NORMAL)
                tvRoomName.setTextColor(Color.parseColor("#B0BEC5"))
                badgeUnreadNew.visibility = View.GONE
            }

            // NAYA: Background highlight logic
            // Agar channel selected hai toh permanent highlight color denge
            val defaultBgColor = if (isSelected) Color.parseColor("#2A3655") else Color.TRANSPARENT
            itemView.setBackgroundColor(defaultBgColor)

            itemView.setOnClickListener { onClick(room) }

            // TV D-Pad Focus logic
            itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.setBackgroundColor(Color.parseColor("#1B2438")) // D-Pad focus color
                } else {
                    v.setBackgroundColor(defaultBgColor) // Focus hatne par wapas default/selected color
                }
            }
        }
    }


    fun getPositionOf(roomId: String?): Int {
        return items.indexOfFirst { it.id == roomId }
    }

}

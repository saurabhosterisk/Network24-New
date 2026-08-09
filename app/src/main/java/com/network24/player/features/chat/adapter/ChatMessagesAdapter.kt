package com.network24.player.features.chat.adapter

import android.graphics.Color
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.network24.player.R
import com.network24.player.features.chat.repo.ChatMessage
import java.text.SimpleDateFormat
import java.util.Locale

class ChatMessagesAdapter(
    private val mySenderId: String,
    private val onReply: (ChatMessage) -> Unit = {},
    private val onMessageMenu: (ChatMessage) -> Unit = {},
    private val onReaction: (ChatMessage, String) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<ChatMessagesAdapter.VH>() {
    private val items = mutableListOf<ChatMessage>()
    fun submit(list: List<ChatMessage>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
    override fun getItemViewType(position: Int): Int = if (items[position].senderId == mySenderId) VIEW_RIGHT else VIEW_LEFT
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == VIEW_RIGHT) R.layout.item_chat_message_right else R.layout.item_chat_message_left
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false), onReply, onMessageMenu, onReaction)
    }
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position], mySenderId)
    override fun getItemCount(): Int = items.size

    class VH(itemView: View, private val onReply: (ChatMessage) -> Unit, private val onMessageMenu: (ChatMessage) -> Unit, private val onReaction: (ChatMessage, String) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val tvSender: TextView = itemView.findViewById(R.id.tvSender)
        private val tvText: TextView = itemView.findViewById(R.id.tvText)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvReplyPreview: TextView = itemView.findViewById(R.id.tvReplyPreview)
        private val tvUserIcon: TextView? = itemView.findViewById<View?>(R.id.tvUserIcon) as? TextView
        private val reactions = LinearLayout(itemView.context).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE; setPadding(0, 4, 0, 0) }
        private lateinit var current: ChatMessage

        init {
            (tvText.parent as? ViewGroup)?.addView(reactions)
            tvText.setOnClickListener { onReply(current) }
            tvText.setOnLongClickListener { onMessageMenu(current); true }
        }

        fun bind(m: ChatMessage, mySenderId: String) {
            current = m
            val isMine = m.senderId.isNotBlank() && m.senderId == mySenderId
            tvSender.text = if (isMine) "You" else m.senderName.ifBlank { "Unknown" }
            tvText.text = if (m.deleted) "🚫 This message was deleted" else m.text
            tvText.setTextColor(if (m.deleted) Color.parseColor("#78909C") else Color.WHITE)
            tvTime.text = formatLocalTime(m.ts) + if (m.edited && !m.deleted) "  (edited)" else ""
            tvUserIcon?.text = "👤"
            tvText.movementMethod = ScrollingMovementMethod.getInstance()
            if (!m.replyToMessageId.isNullOrBlank()) {
                tvReplyPreview.text = "↩ Reply to ${m.replyToSenderName?.ifBlank { "Unknown" } ?: "Unknown"}: ${m.replyToText.orEmpty().take(120)}"
                tvReplyPreview.visibility = View.VISIBLE
            } else tvReplyPreview.visibility = View.GONE

            reactions.removeAllViews()
            val ordered = listOf("👍", "❤️", "😂", "😮", "😢", "😡")
            ordered.filter { m.reactions[it]?.isNotEmpty() == true }.forEach { emoji ->
                val count = m.reactions[emoji]?.size ?: 0
                reactions.addView(reactionChip("$emoji $count") { onReaction(current, emoji) })
            }
            reactions.addView(reactionChip("+") { onReaction(current, "__picker__") })
            reactions.visibility = View.VISIBLE
        }

        private fun reactionChip(label: String, onClick: () -> Unit): TextView = TextView(itemView.context).apply {
            text = label; textSize = 12f; setTextColor(Color.WHITE); setBackgroundResource(R.drawable.bg_reaction_chip)
            setPadding(10, 4, 10, 4); setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = 6 }
        }

        private fun formatLocalTime(ts: Timestamp?): String {
            if (ts == null) return ""
            return SimpleDateFormat("dd/MM/yyyy, HH:mm", Locale.getDefault()).format(ts.toDate())
        }
    }
    companion object { private const val VIEW_LEFT = 0; private const val VIEW_RIGHT = 1 }
}

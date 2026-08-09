package com.network24.player.features.chat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.network24.player.R
import com.network24.player.features.chat.repo.ChatMessage
import java.text.SimpleDateFormat
import java.util.Locale
import android.text.method.ScrollingMovementMethod

class ChatMessagesAdapter(
    private val mySenderId: String,
    private val onReply: (ChatMessage) -> Unit = {},
    private val onMessageMenu: (ChatMessage) -> Unit = {}
) : RecyclerView.Adapter<ChatMessagesAdapter.VH>() {

    private val items = mutableListOf<ChatMessage>()

    fun submit(list: List<ChatMessage>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val sid = items[position].senderId
        return if (sid.isNotBlank() && sid == mySenderId) VIEW_RIGHT else VIEW_LEFT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == VIEW_RIGHT) {
            R.layout.item_chat_message_right
        } else {
            R.layout.item_chat_message_left
        }
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return VH(v, onReply, onMessageMenu)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], mySenderId)
    }

    override fun getItemCount(): Int = items.size

    class VH(
        itemView: View,
        private val onReply: (ChatMessage) -> Unit,
        private val onMessageMenu: (ChatMessage) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvSender: TextView = itemView.findViewById(R.id.tvSender)
        private val tvText: TextView = itemView.findViewById(R.id.tvText)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvReplyPreview: TextView = itemView.findViewById(R.id.tvReplyPreview)
        private val tvUserIcon: TextView? = itemView.findViewById<View?>(R.id.tvUserIcon) as? TextView

        fun bind(m: ChatMessage, mySenderId: String) {
            val isMine = m.senderId.isNotBlank() && m.senderId == mySenderId
            tvSender.text = if (isMine) "You" else m.senderName.ifBlank { "Unknown" }
            tvText.text = m.text
            tvTime.text = formatLocalTime(m.ts)
            tvUserIcon?.text = "👤"
            tvText.movementMethod = ScrollingMovementMethod.getInstance()

            if (!m.replyToMessageId.isNullOrBlank()) {
                val name = m.replyToSenderName?.ifBlank { "Unknown" } ?: "Unknown"
                val original = m.replyToText.orEmpty()
                tvReplyPreview.text = "↩ Reply to $name: ${original.take(120)}"
                tvReplyPreview.visibility = View.VISIBLE
            } else {
                tvReplyPreview.visibility = View.GONE
            }

            tvText.setOnClickListener {
                onReply(m)
            }
            tvText.setOnLongClickListener {
                onMessageMenu(m)
                true
            }
        }

        private fun formatLocalTime(ts: Timestamp?): String {
            if (ts == null) return ""
            val date = ts.toDate()
            val sdf = SimpleDateFormat("dd/MM/yyyy, HH:mm", Locale.getDefault())
            return sdf.format(date)
        }
    }

    companion object {
        private const val VIEW_LEFT = 0
        private const val VIEW_RIGHT = 1
    }
}

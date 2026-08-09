package com.network24.player.features.chat.activity

import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.network24.player.R
import com.network24.player.core.base.BaseActivity
import com.network24.player.core.preferences.PreferenceManager
import com.network24.player.databinding.ActivityChatHubBinding
import com.network24.player.features.chat.adapter.ChatMessagesAdapter
import com.network24.player.features.chat.adapter.ChatRoomsAdapter
import com.network24.player.features.chat.model.ChatRoom
import com.network24.player.features.chat.repo.ChatMessage
import com.network24.player.features.chat.repo.ChatRepository

class ChatHubActivity : BaseActivity() {
    private lateinit var binding: ActivityChatHubBinding
    private lateinit var prefs: PreferenceManager
    private lateinit var senderName: String
    private lateinit var senderId: String
    private val repo = ChatRepository()
    private var roomMessagesListener: ListenerRegistration? = null
    private lateinit var roomsAdapter: ChatRoomsAdapter
    private lateinit var messagesAdapter: ChatMessagesAdapter
    private val roomLastMsgListeners = mutableMapOf<String, ListenerRegistration>()
    private var selectedRoom: ChatRoom? = null
    private var replyToMessage: ChatMessage? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatHubBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferenceManager(this)
        senderName = (prefs.getUsername() ?: "guest").trim().ifEmpty { "guest" }
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "device"
        senderId = senderName.lowercase().replace(" ", "_") + "_" + deviceId.takeLast(6)

        roomsAdapter = ChatRoomsAdapter { room -> selectRoom(room) }
        binding.rvRooms.layoutManager = LinearLayoutManager(this)
        binding.rvRooms.adapter = roomsAdapter
        val rooms = defaultRooms()
        roomsAdapter.submit(rooms)

        messagesAdapter = ChatMessagesAdapter(senderId) { message -> beginReply(message) }
        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvMessages.adapter = messagesAdapter
        binding.btnSend!!.setOnClickListener { sendCurrent() }
        binding.btnCancelReply!!.setOnClickListener { clearReply() }

        startRoomUnreadWatchers(rooms)
        val lastId = prefs.getLastChatRoomId()
        val initial = rooms.firstOrNull { it.id == lastId } ?: rooms.first()
        selectRoom(initial)

        binding.rvMessages.nextFocusDownId = binding.etMessage!!.id
        binding.etMessage!!.nextFocusUpId = binding.rvMessages.id
        binding.btnSend!!.nextFocusUpId = binding.rvMessages.id
        binding.btnCancelReply!!.nextFocusUpId = binding.rvMessages.id
        focusRoom(initial)
    }

    private fun focusRoom(room: ChatRoom) {
        val index = roomsAdapter.getPositionOf(room.id)
        if (index != -1) {
            binding.rvRooms.post {
                binding.rvRooms.scrollToPosition(index)
                binding.rvRooms.post { binding.rvRooms.layoutManager?.findViewByPosition(index)?.requestFocus() }
            }
        }
    }

    private fun selectRoom(room: ChatRoom) {
        selectedRoom = room
        clearReply()
        binding.tvRoomTitle!!.text = "# ${room.id}"
        roomsAdapter.setSelectedRoom(room.id)
        prefs.setLastChatRoomId(room.id)
        val canSend = canSendToRoom(room.id, room.readOnly)
        binding.etMessage!!.isEnabled = canSend
        binding.btnSend!!.isEnabled = canSend
        binding.etMessage!!.hint = if (canSend) "Type a message (use @username to mention)" else "Read-only channel"
        binding.etMessage!!.visibility = if (canSend) View.VISIBLE else View.GONE
        binding.btnSend!!.visibility = if (canSend) View.VISIBLE else View.GONE
        binding.replyBar!!.visibility = if (canSend) binding.replyBar!!.visibility else View.GONE
        roomsAdapter.setUnread(room.id, false)

        roomMessagesListener?.remove()
        roomMessagesListener = repo.listenMessages(
            room.id,
            onUpdate = { list ->
                messagesAdapter.submit(list)
                if (list.isNotEmpty()) {
                    binding.rvMessages.scrollToPosition(list.size - 1)
                    prefs.setChatLastSeen(room.id, list.last().ts?.toDate()?.time ?: System.currentTimeMillis())
                } else prefs.setChatLastSeen(room.id, System.currentTimeMillis())
                roomsAdapter.setUnread(room.id, false)
            },
            onError = { Toast.makeText(this, "Listen failed: ${it.message}", Toast.LENGTH_SHORT).show() }
        )
    }

    private fun beginReply(message: ChatMessage) {
        val room = selectedRoom ?: return
        if (!canSendToRoom(room.id, room.readOnly)) return
        replyToMessage = message
        val name = message.senderName.ifBlank { "Unknown" }
        binding.tvReplyPreview!!.text = "↩ Replying to $name\n${message.text.take(160)}"
        binding.replyBar!!.visibility = View.VISIBLE
        binding.etMessage!!.requestFocus()
    }

    private fun clearReply() {
        replyToMessage = null
        if (::binding.isInitialized) {
            binding.replyBar!!.visibility = View.GONE
            binding.tvReplyPreview!!.text = ""
        }
    }

    private fun sendCurrent() {
        val room = selectedRoom ?: return
        if (!canSendToRoom(room.id, room.readOnly)) return
        val text = binding.etMessage!!.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            Toast.makeText(this, "Empty message", Toast.LENGTH_SHORT).show()
            return
        }
        val mentions = extractMentions(text)
        repo.sendMessage(
            roomId = room.id,
            text = text,
            senderId = senderId,
            senderName = senderName,
            replyTo = replyToMessage,
            mentions = mentions,
            onOk = { binding.etMessage!!.setText(""); clearReply() },
            onError = { Toast.makeText(this, "Send failed: ${it.message}", Toast.LENGTH_LONG).show() }
        )
    }

    private fun extractMentions(text: String): List<String> = Regex("(?<![A-Za-z0-9_])@([A-Za-z0-9_.-]{2,32})")
        .findAll(text).map { it.groupValues[1].lowercase() }.distinct().toList()

    private fun canSendToRoom(roomId: String, isReadOnly: Boolean): Boolean {
        if (!isReadOnly) return true
        return setOf("network24").contains(senderName.lowercase())
    }

    private fun startRoomUnreadWatchers(rooms: List<ChatRoom>) {
        roomLastMsgListeners.values.forEach { it.remove() }
        roomLastMsgListeners.clear()
        val db = FirebaseFirestore.getInstance()
        rooms.forEach { room ->
            roomLastMsgListeners[room.id] = db.collection("rooms").document(room.id).collection("messages")
                .orderBy("ts", Query.Direction.DESCENDING).limit(1)
                .addSnapshotListener { snap, err ->
                    if (err != null) return@addSnapshotListener
                    val doc = snap?.documents?.firstOrNull() ?: return@addSnapshotListener
                    val ts = doc.getTimestamp("ts")?.toDate()?.time ?: return@addSnapshotListener
                    val unread = selectedRoom?.id != room.id && ts > prefs.getChatLastSeen(room.id)
                    roomsAdapter.setUnread(room.id, unread)
                }
        }
    }

    private fun defaultRooms(): List<ChatRoom> = listOf(
        ChatRoom("announcements", "Announcements", "📢", 1, true),
        ChatRoom("pinned_posts", "Pinned Posts", "📌", 2, true),
        ChatRoom("channel_down", "Channel Down", "🚨", 3, true),
        ChatRoom("buffering_issues", "Buffering Issues", "⏳", 4, false),
        ChatRoom("questions_and_help", "Questions & Help", "❓", 5, false),
        ChatRoom("channel_requests", "Channel Requests", "📡", 6, false),
        ChatRoom("general_discussions", "General Discussions", "💬", 7, false),
        ChatRoom("live_events", "Live Events", "🏆", 8, true),
        ChatRoom("development_desk", "Development Desk", "💻", 9, false)
    ).sortedBy { it.order }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val readOnly = binding.etMessage!!.visibility == View.GONE
            val right = binding.rvMessages.hasFocus() || binding.etMessage!!.hasFocus() || binding.btnSend!!.hasFocus() || binding.btnCancelReply!!.hasFocus()
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (binding.rvRooms.hasFocus()) { if (messagesAdapter.itemCount > 0) focusLastVisibleMessage() else if (!readOnly) binding.etMessage!!.requestFocus(); return true }
                    if (binding.etMessage!!.hasFocus() || binding.btnCancelReply!!.hasFocus()) { binding.btnSend!!.requestFocus(); return true }
                    if (binding.btnSend!!.hasFocus()) return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (binding.btnSend!!.hasFocus() || binding.btnCancelReply!!.hasFocus()) { binding.etMessage!!.requestFocus(); return true }
                    if (right) { restoreRoomFocus(); return true }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (right && binding.rvMessages.hasFocus()) {
                        val child = binding.rvMessages.focusedChild
                        if (child != null) {
                            val pos = binding.rvMessages.getChildAdapterPosition(child)
                            val tv = child.findViewById<android.widget.TextView>(R.id.tvText)
                            if (tv != null && tv.hasFocus() && tv.canScrollVertically(1)) return super.dispatchKeyEvent(event)
                            if (pos == messagesAdapter.itemCount - 1) { if (!readOnly) binding.etMessage!!.requestFocus(); return true }
                        }
                    } else if (right && (binding.etMessage!!.hasFocus() || binding.btnSend!!.hasFocus() || binding.btnCancelReply!!.hasFocus())) return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (right && (binding.etMessage!!.hasFocus() || binding.btnSend!!.hasFocus() || binding.btnCancelReply!!.hasFocus())) { focusLastVisibleMessage(); return true }
                    if (right && binding.rvMessages.hasFocus()) {
                        val child = binding.rvMessages.focusedChild
                        if (child != null) {
                            val pos = binding.rvMessages.getChildAdapterPosition(child)
                            val tv = child.findViewById<android.widget.TextView>(R.id.tvText)
                            if (tv != null && tv.hasFocus() && tv.canScrollVertically(-1)) return super.dispatchKeyEvent(event)
                            if (pos == 0) return true
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun focusLastVisibleMessage() {
        if (messagesAdapter.itemCount == 0) return
        val lm = binding.rvMessages.layoutManager as LinearLayoutManager
        val pos = lm.findLastVisibleItemPosition()
        if (pos >= 0) lm.findViewByPosition(pos)?.findViewById<View>(R.id.tvText)?.requestFocus()
    }

    private fun restoreRoomFocus() {
        selectedRoom?.let { focusRoom(it) }
    }

    override fun onDestroy() {
        roomMessagesListener?.remove()
        roomLastMsgListeners.values.forEach { it.remove() }
        roomLastMsgListeners.clear()
        super.onDestroy()
    }
}

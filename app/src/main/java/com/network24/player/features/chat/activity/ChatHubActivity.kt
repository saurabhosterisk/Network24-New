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

        messagesAdapter = ChatMessagesAdapter(mySenderId = senderId)
        val lm = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvMessages.layoutManager = lm
        binding.rvMessages.adapter = messagesAdapter

        binding.btnSend.setOnClickListener { sendCurrent() }

        startRoomUnreadWatchers(rooms)

        val lastId = prefs.getLastChatRoomId()
        val initial = rooms.firstOrNull { it.id == lastId } ?: rooms.first()
        selectRoom(initial)

        binding.rvMessages.nextFocusDownId = binding.etMessage.id
        binding.etMessage.nextFocusUpId = binding.rvMessages.id
        binding.btnSend.nextFocusUpId = binding.rvMessages.id

        val initialIndex = roomsAdapter.getPositionOf(initial.id)
        if (initialIndex != -1) {
            binding.rvRooms.post {
                binding.rvRooms.scrollToPosition(initialIndex)
                binding.rvRooms.post {
                    val viewToFocus = binding.rvRooms.layoutManager?.findViewByPosition(initialIndex)
                    viewToFocus?.requestFocus()
                }
            }
        }
    }

    private fun selectRoom(room: ChatRoom) {
        selectedRoom = room
        binding.tvRoomTitle.text = "# ${room.id}"

        roomsAdapter.setSelectedRoom(room.id)
        prefs.setLastChatRoomId(room.id)

        val canSend = canSendToRoom(room.id, room.readOnly)
        binding.btnSend.isEnabled = canSend
        binding.etMessage.isEnabled = canSend
        binding.etMessage.hint = if (canSend) "Type a message" else "Read-only channel"

        if (canSend) {
            binding.etMessage.visibility = View.VISIBLE
            binding.btnSend.visibility = View.VISIBLE
            binding.etMessage.isEnabled = true
            binding.btnSend.isEnabled = true
            binding.etMessage.hint = "Type a message"
        } else {
            binding.etMessage.visibility = View.GONE
            binding.btnSend.visibility = View.GONE
        }

        roomsAdapter.setUnread(room.id, false)

        roomMessagesListener?.remove()
        roomMessagesListener = repo.listenMessages(
            roomId = room.id,
            onUpdate = { list ->
                messagesAdapter.submit(list)
                if (list.isNotEmpty()) {
                    binding.rvMessages.scrollToPosition(list.size - 1)

                    val lastTs = list.lastOrNull()?.ts?.toDate()?.time
                    if (lastTs != null) {
                        prefs.setChatLastSeen(room.id, lastTs)
                    } else {
                        prefs.setChatLastSeen(room.id, System.currentTimeMillis())
                    }
                } else {
                    prefs.setChatLastSeen(room.id, System.currentTimeMillis())
                }
                roomsAdapter.setUnread(room.id, false)
            },
            onError = { e ->
                Toast.makeText(this, "Listen failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun sendCurrent() {
        val room = selectedRoom ?: return
        val canSend = canSendToRoom(room.id, room.readOnly)

        if (!canSend) {
            Toast.makeText(this, "Read-only channel", Toast.LENGTH_SHORT).show()
            return
        }

        val text = binding.etMessage.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            Toast.makeText(this, "Empty message", Toast.LENGTH_SHORT).show()
            return
        }

        repo.sendMessage(
            roomId = room.id,
            text = text,
            senderId = senderId,
            senderName = senderName,
            onOk = {
                binding.etMessage.setText("")
            },
            onError = { e ->
                Toast.makeText(this, "Send failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun canSendToRoom(roomId: String, isReadOnly: Boolean): Boolean {
        if (!isReadOnly) return true
        val adminUsers = setOf("network24")
        return adminUsers.contains(senderName.lowercase())
    }

    private fun startRoomUnreadWatchers(rooms: List<ChatRoom>) {
        roomLastMsgListeners.values.forEach { it.remove() }
        roomLastMsgListeners.clear()
        val db = FirebaseFirestore.getInstance()
        rooms.forEach { room ->
            val reg = db.collection("rooms")
                .document(room.id)
                .collection("messages")
                .orderBy("ts", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { snap, err ->
                    if (err != null) return@addSnapshotListener
                    val doc = snap?.documents?.firstOrNull() ?: return@addSnapshotListener
                    val ts = doc.getTimestamp("ts")?.toDate()?.time ?: return@addSnapshotListener

                    val lastSeen = prefs.getChatLastSeen(room.id)
                    val isSelected = (selectedRoom?.id == room.id)
                    val unread = !isSelected && ts > lastSeen
                    roomsAdapter.setUnread(room.id, unread)
                }
            roomLastMsgListeners[room.id] = reg
        }
    }

    private fun defaultRooms(): List<ChatRoom> {
        return listOf(
            ChatRoom("announcements", "Announcements", "📢", 1, readOnly = true),
            ChatRoom("pinned_posts", "Pinned Posts", "📌", 2, readOnly = true),
            ChatRoom("channel_down", "Channel Down", "🚨", 3, readOnly = true),
            ChatRoom("buffering_issues", "Buffering Issues", "⏳", 4, readOnly = false),
            ChatRoom("questions_and_help", "Questions & Help", "❓", 5, readOnly = false),
            ChatRoom("channel_requests", "Channel Requests", "📡", 6, readOnly = false),
            ChatRoom("general_discussions", "General Discussions", "💬", 7, readOnly = false),
            ChatRoom("live_events", "Live Events", "🏆", 8, readOnly = true),
            ChatRoom("development_desk", "Development Desk", "💻", 9, readOnly = false)
        ).sortedBy { it.order }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isReadOnly = binding.etMessage.visibility == View.GONE
            val inRightPane = binding.rvMessages.hasFocus() || binding.etMessage.hasFocus() || binding.btnSend.hasFocus()
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (binding.rvRooms.hasFocus()) {
                        if (messagesAdapter.itemCount > 0) {
                            focusLastVisibleMessage()
                        } else if (!isReadOnly) {
                            binding.etMessage.requestFocus()
                        }
                        return true
                    }
                    if (binding.etMessage.hasFocus()) {
                        binding.btnSend.requestFocus()
                        return true
                    }
                    if (binding.btnSend.hasFocus()) return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (binding.btnSend.hasFocus()) {
                        binding.etMessage.requestFocus()
                        return true
                    }
                    if (inRightPane) {
                        restoreRoomFocus()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (inRightPane) {
                        if (binding.etMessage.hasFocus() || binding.btnSend.hasFocus()) return true
                        if (binding.rvMessages.hasFocus()) {
                            val focusedView = binding.rvMessages.focusedChild
                            if (focusedView != null) {
                                val pos = binding.rvMessages.getChildAdapterPosition(focusedView)
                                val tvText = focusedView.findViewById<android.widget.TextView>(R.id.tvText)
                                if (tvText != null && tvText.hasFocus() && tvText.canScrollVertically(1)) {
                                    return super.dispatchKeyEvent(event)
                                }
                                if (pos == messagesAdapter.itemCount - 1) {
                                    if (!isReadOnly) binding.etMessage.requestFocus()
                                    return true
                                }
                            }
                        }
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (inRightPane) {
                        if (binding.etMessage.hasFocus() || binding.btnSend.hasFocus()) {
                            focusLastVisibleMessage()
                            return true
                        }
                        if (binding.rvMessages.hasFocus()) {
                            val focusedView = binding.rvMessages.focusedChild
                            if (focusedView != null) {
                                val pos = binding.rvMessages.getChildAdapterPosition(focusedView)
                                val tvText = focusedView.findViewById<android.widget.TextView>(R.id.tvText)
                                if (tvText != null && tvText.hasFocus() && tvText.canScrollVertically(-1)) {
                                    return super.dispatchKeyEvent(event)
                                }
                                if (pos == 0) return true
                            }
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
        val targetPos = lm.findLastVisibleItemPosition()
        if (targetPos != -1) {
            val targetView = lm.findViewByPosition(targetPos)
            targetView?.findViewById<View>(R.id.tvText)?.requestFocus() ?: targetView?.requestFocus()
        }
    }

    private fun restoreRoomFocus() {
        val index = roomsAdapter.getPositionOf(selectedRoom?.id)
        if (index != -1) {
            binding.rvRooms.scrollToPosition(index)
            binding.rvRooms.post {
                binding.rvRooms.layoutManager?.findViewByPosition(index)?.requestFocus()
            }
        }
    }

    override fun onDestroy() {
        roomMessagesListener?.remove()
        roomMessagesListener = null
        roomLastMsgListeners.values.forEach { it.remove() }
        roomLastMsgListeners.clear()
        super.onDestroy()
    }
}

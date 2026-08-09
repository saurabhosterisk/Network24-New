package com.network24.player.features.chat.repo

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

data class ChatMessage(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val ts: Timestamp? = null,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToText: String? = null,
    val mentions: List<String> = emptyList(),
    val reactions: Map<String, List<String>> = emptyMap(),
    val edited: Boolean = false,
    val editedAt: Timestamp? = null,
    val deleted: Boolean = false,
    val deletedAt: Timestamp? = null
)

class ChatRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    fun listenMessages(roomId: String, limit: Long = 200, onUpdate: (List<ChatMessage>) -> Unit, onError: (Exception) -> Unit): ListenerRegistration =
        db.collection("rooms").document(roomId).collection("messages")
            .orderBy("ts", Query.Direction.ASCENDING).limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) { onError(err); return@addSnapshotListener }
                onUpdate(snap?.documents.orEmpty().mapNotNull { it.toChatMessageOrNull() })
            }

    fun sendMessage(roomId: String, text: String, senderId: String, senderName: String, replyTo: ChatMessage? = null, mentions: List<String> = emptyList(), onOk: () -> Unit, onError: (Exception) -> Unit) {
        val payload = hashMapOf<String, Any>("text" to text, "senderId" to senderId, "senderName" to senderName, "ts" to FieldValue.serverTimestamp())
        if (replyTo != null) { payload["replyToMessageId"] = replyTo.id; payload["replyToSenderName"] = replyTo.senderName; payload["replyToText"] = replyTo.text.take(180) }
        if (mentions.isNotEmpty()) payload["mentions"] = mentions
        db.collection("rooms").document(roomId).collection("messages").add(payload).addOnSuccessListener { onOk() }.addOnFailureListener { onError(it) }
    }

    fun editMessage(roomId: String, messageId: String, text: String, onOk: () -> Unit, onError: (Exception) -> Unit) {
        db.collection("rooms").document(roomId).collection("messages").document(messageId)
            .update(mapOf("text" to text, "edited" to true, "editedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { onOk() }.addOnFailureListener { onError(it) }
    }

    fun deleteMessage(roomId: String, messageId: String, onOk: () -> Unit, onError: (Exception) -> Unit) {
        db.collection("rooms").document(roomId).collection("messages").document(messageId)
            .update(mapOf("deleted" to true, "deletedAt" to FieldValue.serverTimestamp(), "text" to ""))
            .addOnSuccessListener { onOk() }.addOnFailureListener { onError(it) }
    }

    fun toggleReaction(roomId: String, messageId: String, emoji: String, userId: String, onOk: () -> Unit, onError: (Exception) -> Unit) {
        val ref = db.collection("rooms").document(roomId).collection("messages").document(messageId)
        db.runTransaction { transaction ->
            val snap = transaction.get(ref)
            val raw = snap.get("reactions") as? Map<*, *> ?: emptyMap<Any, Any>()
            val reactions = raw.mapValues { (_, value) -> (value as? List<*>)?.mapNotNull { it as? String }?.toMutableList() ?: mutableListOf() }.toMutableMap()
            val users = reactions[emoji] ?: mutableListOf()
            if (users.contains(userId)) users.remove(userId) else users.add(userId)
            if (users.isEmpty()) reactions.remove(emoji) else reactions[emoji] = users
            transaction.update(ref, "reactions", reactions)
        }.addOnSuccessListener { onOk() }.addOnFailureListener { onError(it) }
    }

    fun reportMessage(roomId: String, message: ChatMessage, reporterId: String, reporterName: String, onOk: () -> Unit, onError: (Exception) -> Unit) {
        val payload = hashMapOf<String, Any>("roomId" to roomId, "messageId" to message.id, "messageText" to message.text.take(1000), "messageSenderId" to message.senderId, "messageSenderName" to message.senderName, "reporterId" to reporterId, "reporterName" to reporterName, "ts" to FieldValue.serverTimestamp(), "status" to "open")
        db.collection("chat_reports").add(payload).addOnSuccessListener { onOk() }.addOnFailureListener { onError(it) }
    }

    private fun DocumentSnapshot.toChatMessageOrNull(): ChatMessage? {
        if (getBoolean("deleted") == true) {
            return ChatMessage(id = id, text = "", senderId = getString("senderId") ?: "", senderName = getString("senderName") ?: "", ts = getTimestamp("ts"), deleted = true, deletedAt = getTimestamp("deletedAt"))
        }
        val text = getString("text") ?: return null
        val rawReactions = get("reactions") as? Map<*, *> ?: emptyMap<Any, Any>()
        val reactions = rawReactions.mapNotNull { (key, value) ->
            val emoji = key as? String ?: return@mapNotNull null
            emoji to ((value as? List<*>)?.mapNotNull { it as? String } ?: emptyList())
        }.toMap()
        return ChatMessage(id = id, text = text, senderId = getString("senderId") ?: "", senderName = getString("senderName") ?: "", ts = getTimestamp("ts"), replyToMessageId = getString("replyToMessageId"), replyToSenderName = getString("replyToSenderName"), replyToText = getString("replyToText"), mentions = (get("mentions") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(), reactions = reactions, edited = getBoolean("edited") == true, editedAt = getTimestamp("editedAt"), deleted = false)
    }
}

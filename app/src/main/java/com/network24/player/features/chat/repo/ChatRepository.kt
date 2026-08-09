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
    val ts: Timestamp? = null
)

class ChatRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun listenMessages(
        roomId: String,
        limit: Long = 200,
        onUpdate: (List<ChatMessage>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("rooms")
            .document(roomId)
            .collection("messages")
            .orderBy("ts", Query.Direction.ASCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                val docs = snap?.documents ?: emptyList()
                val messages = docs.mapNotNull { it.toChatMessageOrNull() }
                onUpdate(messages)
            }
    }

    fun sendMessage(
        roomId: String,
        text: String,
        senderId: String,
        senderName: String,
        onOk: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val payload = hashMapOf(
            "text" to text,
            "senderId" to senderId,
            "senderName" to senderName,
            "ts" to FieldValue.serverTimestamp()
        )

        db.collection("rooms")
            .document(roomId)
            .collection("messages")
            .add(payload)
            .addOnSuccessListener { onOk() }
            .addOnFailureListener { onError(it) }
    }

    private fun DocumentSnapshot.toChatMessageOrNull(): ChatMessage? {
        val text = getString("text") ?: return null
        val senderId = getString("senderId") ?: ""
        val senderName = getString("senderName") ?: ""
        val ts = getTimestamp("ts")
        return ChatMessage(
            id = id,
            text = text,
            senderId = senderId,
            senderName = senderName,
            ts = ts
        )
    }
}

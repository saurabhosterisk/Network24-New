package com.network24.player.features.chat.model

data class ChatRoom(
    val id: String,
    val name: String,
    val emoji: String,
    val order: Int,
    val readOnly: Boolean
)

package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "auto_replies")
data class AutoReplyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val platform: String, // WHATSAPP, EMAIL
    val incomingMessage: String,
    val summary: String,
    val generatedReply: String,
    val status: String, // DRAFTED, SENT, PENDING_APPROVAL
    val timestamp: Long = System.currentTimeMillis()
)

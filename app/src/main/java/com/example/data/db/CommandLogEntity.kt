package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_logs")
data class CommandLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val response: String,
    val actionType: String, // VOICE, SYSTEM, WHATSAPP, EMAIL, FILE, CALL, GENERAL
    val status: String,     // SUCCESS, EXECUTED, FAILED
    val timestamp: Long = System.currentTimeMillis()
)

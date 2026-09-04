package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val fileType: String = "TXT", // TXT, DOCX, PDF, SUMMARY
    val folder: String = "General",
    val timestamp: Long = System.currentTimeMillis()
)

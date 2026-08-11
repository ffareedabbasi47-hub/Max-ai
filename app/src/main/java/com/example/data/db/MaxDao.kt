package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MaxDao {
    @Query("SELECT * FROM command_logs ORDER BY timestamp DESC")
    fun getAllCommandLogs(): Flow<List<CommandLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommandLog(log: CommandLogEntity)

    @Query("DELETE FROM command_logs")
    suspend fun clearCommandLogs()

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Query("SELECT * FROM auto_replies ORDER BY timestamp DESC")
    fun getAllAutoReplies(): Flow<List<AutoReplyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutoReply(reply: AutoReplyEntity)

    @Query("UPDATE auto_replies SET status = :status WHERE id = :id")
    suspend fun updateReplyStatus(id: Long, status: String)
}

package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CommandLogEntity::class, NoteEntity::class, AutoReplyEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MaxDatabase : RoomDatabase() {
    abstract fun maxDao(): MaxDao

    companion object {
        @Volatile
        private var INSTANCE: MaxDatabase? = null

        fun getInstance(context: Context): MaxDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MaxDatabase::class.java,
                    "max_jarvis_db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

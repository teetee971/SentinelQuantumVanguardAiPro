package com.sentinel.quantumvanguard.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sentinel.quantumvanguard.data.EventDao
import com.sentinel.quantumvanguard.data.SecurityEvent

@Database(entities = [SecurityEvent::class], version = 1, exportSchema = false)
abstract class SentinelDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    
    companion object {
        @Volatile
        private var INSTANCE: SentinelDatabase? = null
        
        fun getDatabase(context: Context): SentinelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SentinelDatabase::class.java,
                    "sentinel_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

package com.sentinel.quantumvanguard.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sentinel.quantumvanguard.data.Contact
import com.sentinel.quantumvanguard.data.ContactDao
import com.sentinel.quantumvanguard.data.EventDao
import com.sentinel.quantumvanguard.data.SecurityEvent

@Database(entities = [SecurityEvent::class, Contact::class], version = 2, exportSchema = false)
abstract class SentinelDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun contactDao(): ContactDao
    
    companion object {
        @Volatile
        private var INSTANCE: SentinelDatabase? = null
        
        fun getDatabase(context: Context): SentinelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SentinelDatabase::class.java,
                    "sentinel_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

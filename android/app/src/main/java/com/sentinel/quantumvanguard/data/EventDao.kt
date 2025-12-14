package com.sentinel.quantumvanguard.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM security_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<SecurityEvent>>
    
    @Query("SELECT * FROM security_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int = 10): List<SecurityEvent>
    
    @Query("SELECT COUNT(*) FROM security_events")
    suspend fun getEventCount(): Int
    
    @Query("SELECT MAX(timestamp) FROM security_events")
    suspend fun getLatestEventTimestamp(): Long?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: SecurityEvent): Long
    
    @Insert(onConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<SecurityEvent>)
    
    @Query("DELETE FROM security_events")
    suspend fun deleteAllEvents()
}

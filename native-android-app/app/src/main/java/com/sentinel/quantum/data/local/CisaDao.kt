package com.sentinel.quantum.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CisaDao {
    @Query("SELECT * FROM cisa_intel_feed ORDER BY cachedAt DESC")
    fun getAllAlerts(): Flow<List<CisaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerts(alerts: List<CisaEntity>)
}

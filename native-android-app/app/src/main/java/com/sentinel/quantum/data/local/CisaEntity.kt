package com.sentinel.quantum.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cisa_intel_feed")
data class CisaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val dateReleased: String,
    val cachedAt: Long = System.currentTimeMillis()
)

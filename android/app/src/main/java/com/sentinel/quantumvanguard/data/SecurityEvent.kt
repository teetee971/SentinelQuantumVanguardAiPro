package com.sentinel.quantumvanguard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "security_events")
data class SecurityEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val eventType: String,
    val severity: String,
    val title: String,
    val description: String,
    val source: String,
    val riskScore: Int,
    val isSimulated: Boolean
)

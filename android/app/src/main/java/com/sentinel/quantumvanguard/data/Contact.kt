package com.sentinel.quantumvanguard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Contact Entity - Local contact tagging
 * 
 * Stores contact tags locally in Room database
 * Tags: KNOWN (trusted), UNKNOWN (unverified), REPORTED (spam/scam)
 * 
 * No cloud sync - 100% local storage
 */
@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: String,        // Android contact ID
    val displayName: String,
    val phoneNumber: String,
    val tag: String,              // KNOWN, UNKNOWN, REPORTED
    val riskScore: Int = 0,       // 0-100
    val isBlocked: Boolean = false,
    val notes: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)

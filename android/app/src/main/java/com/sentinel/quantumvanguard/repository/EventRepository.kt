package com.sentinel.quantumvanguard.repository

import com.sentinel.quantumvanguard.data.EventDao
import com.sentinel.quantumvanguard.data.SecurityEvent
import kotlinx.coroutines.flow.Flow

class EventRepository(private val eventDao: EventDao) {
    
    val allEvents: Flow<List<SecurityEvent>> = eventDao.getAllEvents()
    
    suspend fun insert(event: SecurityEvent) = eventDao.insertEvent(event)
    
    suspend fun insertMultiple(events: List<SecurityEvent>) = eventDao.insertEvents(events)
    
    suspend fun getRecentEvents(limit: Int = 10) = eventDao.getRecentEvents(limit)
    
    suspend fun getEventCount() = eventDao.getEventCount()
    
    suspend fun getLatestTimestamp() = eventDao.getLatestEventTimestamp()
    
    suspend fun deleteAll() = eventDao.deleteAllEvents()
}

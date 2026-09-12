package com.sentinel.quantum.data.repository

import com.sentinel.quantum.data.local.CisaDao
import com.sentinel.quantum.data.local.CisaEntity
import kotlinx.coroutines.flow.Flow

class CisaRepository(
    private val cisaDao: CisaDao
) {
    val alerts: Flow<List<CisaEntity>> = cisaDao.getAllAlerts()

    suspend fun saveAlerts(newAlerts: List<CisaEntity>) {
        cisaDao.insertAlerts(newAlerts)
    }
}

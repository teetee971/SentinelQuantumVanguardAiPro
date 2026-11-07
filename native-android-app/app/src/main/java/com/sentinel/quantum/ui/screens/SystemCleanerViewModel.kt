package com.sentinel.quantum.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.quantum.security.SentinelCleanerSession
import com.sentinel.quantum.security.SentinelOwnedCleanupCollector
import com.sentinel.quantum.security.SentinelOwnedCleanupExecutor

/** Activity-owned: rotation and leaving the screen do not discard in-flight results. */
class SystemCleanerViewModel(application: Application) : AndroidViewModel(application) {
    private val collector = SentinelOwnedCleanupCollector(application)
    private val executor = SentinelOwnedCleanupExecutor(application)
    val session = SentinelCleanerSession(viewModelScope, { collector.scan() }, executor::execute)
}

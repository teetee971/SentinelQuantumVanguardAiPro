package com.sentinel.quantum.talkiewalkie

interface FloorController {
    suspend fun requestFloor(sessionId: String, nowMs: Long): Result<FloorLease>

    fun startRenewal(lease: FloorLease)

    fun stopRenewal()

    suspend fun releaseFloor(lease: FloorLease)
}

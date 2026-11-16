package com.sentinel.quantum.ai

// TEST ONLY. No Android authentication claim and no Android side effect.
internal val testActions = listOf("ACTION_A", "ACTION_B", "SCAN_LOCAL", "A", "B")
    .associateWith { SentinelAiActionPolicy({ true }, { true }, {}) }
internal fun testAuthority() = SentinelAiApprovalAuthority({ 1000L }, testActions, { true })
internal fun testOwner() = SentinelAiApprovalPorts.createOwner({ 1000L }, testActions, { true })

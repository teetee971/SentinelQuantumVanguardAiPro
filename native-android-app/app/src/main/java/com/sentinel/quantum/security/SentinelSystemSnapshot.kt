package com.sentinel.quantum.security

import android.os.Build

/** Local, permission-free Android system observations. */
data class SentinelSystemSnapshot(
    val sdkInt: Int,
    val release: String,
    val securityPatch: String,
    val buildDisplay: String,
    val observedAtEpochMillis: Long
) {
    companion object {
        fun capture(nowEpochMillis: Long = System.currentTimeMillis()): SentinelSystemSnapshot =
            SentinelSystemSnapshot(
                sdkInt = Build.VERSION.SDK_INT,
                release = Build.VERSION.RELEASE.orEmpty(),
                securityPatch = Build.VERSION.SECURITY_PATCH.orEmpty(),
                buildDisplay = Build.DISPLAY.orEmpty(),
                observedAtEpochMillis = nowEpochMillis
            )
    }
}

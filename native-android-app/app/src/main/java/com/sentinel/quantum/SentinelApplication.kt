package com.sentinel.quantum

import android.app.Application
import com.sentinel.quantum.security.CallBlocklistStore
import kotlin.concurrent.thread

/**
 * Process-level initialization that keeps potentially slow AndroidKeyStore work away from
 * CallScreeningService.onScreenCall(). A cold-process race fails open for exact-number rules until
 * the cache is ready; prefix and signed-prefix rules remain available.
 */
class SentinelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        thread(name = "sentinel-call-key-warmup", isDaemon = true) {
            runCatching { CallBlocklistStore(this).prepareFingerprintKeys() }
        }
    }
}

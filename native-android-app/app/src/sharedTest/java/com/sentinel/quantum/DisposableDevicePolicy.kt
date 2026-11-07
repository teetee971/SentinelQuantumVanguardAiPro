package com.sentinel.quantum

/** Pure test-only policy shared verbatim by JVM and Android instrumentation tests. */
object DisposableDevicePolicy {
    fun permits(fingerprint: String, hardware: String, product: String, allowDestructive: String?): Boolean =
        fingerprint.startsWith("generic") ||
            fingerprint.contains("emulator", ignoreCase = true) ||
            hardware.equals("goldfish", ignoreCase = true) ||
            hardware.equals("ranchu", ignoreCase = true) ||
            product.contains("sdk_gphone") || allowDestructive == "true"
}

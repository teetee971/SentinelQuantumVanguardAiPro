package com.sentinel.quantum.security

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkScannerTimelineIntegrationSourceTest {
    @Test
    fun wifiScannerPublishesBoundedTimelineObservationsOffTheCallbackPath() {
        val source = File("src/main/java/com/sentinel/quantum/security/WifiScanner.kt").readText()
        assertTrue(source.contains("NetworkTimelineStore(appContext)"))
        assertTrue(source.contains("NetworkTimelineSource.ANDROID_WIFI"))
        assertTrue(source.contains("Dispatchers.IO"))
        assertTrue(source.contains("networkTimelineStore.recordObserved"))
    }

    @Test
    fun bluetoothScannerPublishesBoundedTimelineObservationsOffTheCallbackPath() {
        val source = File("src/main/java/com/sentinel/quantum/security/BluetoothScanner.kt").readText()
        assertTrue(source.contains("NetworkTimelineStore(appContext)"))
        assertTrue(source.contains("NetworkTimelineSource.ANDROID_BLE"))
        assertTrue(source.contains("Dispatchers.IO"))
        assertTrue(source.contains("networkTimelineStore.recordObserved"))
    }
}

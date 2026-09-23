package com.sentinel.quantum.security

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

data class DiscoveredWifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val band: WifiBand,
    val assessment: WifiRiskAssessment
)

data class WifiScanOutcome(
    val networks: List<DiscoveredWifiNetwork>,
    val source: WifiScanResultTruth.Source
)

/**
 * Scanner Wi-Fi passif et local.
 *
 * Le scanner se contente de lire les résultats de scan fournis par Android, ne modifie
 * aucune configuration réseau et n'émet aucune donnée vers l'extérieur.
 */
class WifiScanner(context: Context) {

    private val appContext = context.applicationContext
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val trustStore = NetworkTrustStore(appContext)
    private var receiver: BroadcastReceiver? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    /**
     * WifiManager.startScan()/scanResults remain gated by precise location on the
     * Android versions supported by Sentinel. NEARBY_WIFI_DEVICES covers other
     * nearby-Wi-Fi APIs on Android 13+, but must not become an extra blocker here.
     */
    val requiredPermissions: Array<String> =
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private val mandatoryPermissions: Array<String> = requiredPermissions

    fun hasPermissions(): Boolean = mandatoryPermissions.all { permission ->
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun isSupported(): Boolean = wifiManager != null

    fun isWifiEnabled(): Boolean = wifiManager?.isWifiEnabled == true

    fun isLocationEnabled(): Boolean {
        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager?.isLocationEnabled == true
        } else {
            @Suppress("DEPRECATION")
            (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true)
        }
    }

    /**
     * Déclenche un scan et publie les résultats via [onResults]. Si Android refuse ou limite
     * le déclenchement (throttling), les derniers résultats disponibles sont utilisés.
     */
    @Suppress("DEPRECATION")
    fun scan(onResults: (WifiScanOutcome) -> Unit, onError: (String) -> Unit) {
        val manager = wifiManager
        if (manager == null) {
            onError("Service Wi-Fi indisponible sur cet appareil.")
            return
        }
        if (!hasPermissions()) {
            onError("Autorisation Position précise requise pour le scan Wi-Fi Android.")
            return
        }
        if (!isLocationEnabled()) {
            onError("La localisation Android doit être activée pour obtenir les résultats du scan Wi-Fi.")
            return
        }

        release()
        val scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val updated = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) == true
                release()
                readResults(
                    manager,
                    if (updated) WifiScanResultTruth.Source.FRESH
                    else WifiScanResultTruth.Source.CACHED_PLATFORM_STALE,
                    onResults,
                    onError
                )
            }
        }
        receiver = scanReceiver
        ContextCompat.registerReceiver(
            appContext,
            scanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val started = try {
            manager.startScan()
        } catch (_: SecurityException) {
            false
        }
        if (!started) {
            release()
            readResults(
                manager,
                WifiScanResultTruth.Source.CACHED_SCAN_REJECTED,
                onResults,
                onError
            )
            return
        }

        // Some Android/OEM builds accept startScan() but suppress or delay the broadcast
        // because of platform throttling. Never leave the UI stuck indefinitely: after a
        // bounded wait, fall back to Android's latest locally cached scan results.
        val fallback = Runnable {
            if (receiver === scanReceiver) {
                release()
                readResults(
                    manager,
                    WifiScanResultTruth.Source.CACHED_TIMEOUT,
                    onResults,
                    onError
                )
            }
        }
        timeoutRunnable = fallback
        mainHandler.postDelayed(fallback, SCAN_RESULT_TIMEOUT_MS)
    }

    fun release() {
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable = null
        receiver?.let {
            runCatching { appContext.unregisterReceiver(it) }
        }
        receiver = null
    }

    // Les autorisations sont vérifiées par hasPermissions() avant tout appel, et une
    // SecurityException reste interceptée pour rester fail-safe.
    @SuppressLint("MissingPermission")
    private fun readResults(
        manager: WifiManager,
        source: WifiScanResultTruth.Source,
        onResults: (WifiScanOutcome) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val results = manager.scanResults
                .orEmpty()
                .map { result -> toNetwork(result.SSID, result.BSSID, result.level, result.frequency, result.capabilities) }
                .sortedWith(
                    compareBy<DiscoveredWifiNetwork> { riskOrder(it.assessment.riskLevel) }
                        .thenByDescending { it.rssiDbm }
                )
            if (source == WifiScanResultTruth.Source.FRESH) {
                PhonePrivateTimelineStore(appContext).append(
                    PhonePrivateTimeline.Event(
                        kind = PhonePrivateTimeline.Kind.WIFI,
                        timestampMs = System.currentTimeMillis(),
                        direction = "LOCAL",
                        signal = PhoneCorePhysicalValidation.SIGNAL_WIFI_SCAN_FRESH
                    )
                )
            }
            onResults(WifiScanOutcome(results, source))
        } catch (_: SecurityException) {
            onError("Android a refusé l'accès aux résultats Wi-Fi. Vérifiez l'autorisation Position précise et l'activation de la localisation.")
        } catch (_: RuntimeException) {
            onError("Le service Wi-Fi Android n'a pas pu fournir les résultats du scan.")
        }
    }

    private fun toNetwork(
        rawSsid: String?,
        rawBssid: String?,
        rssiDbm: Int,
        frequencyMhz: Int,
        capabilities: String?
    ): DiscoveredWifiNetwork {
        val bssid = rawBssid.orEmpty()
        val ssid = rawSsid?.trim()?.takeIf { it.isNotEmpty() } ?: WifiRiskEvaluator.HIDDEN_SSID_LABEL
        return DiscoveredWifiNetwork(
            ssid = ssid,
            bssid = bssid,
            rssiDbm = rssiDbm,
            frequencyMhz = frequencyMhz,
            band = WifiRiskEvaluator.band(frequencyMhz),
            assessment = WifiRiskEvaluator.evaluate(
                ssid = ssid,
                capabilities = capabilities,
                rssiDbm = rssiDbm,
                blocked = bssid.isNotEmpty() && trustStore.isBlocked(bssid),
                allowed = bssid.isNotEmpty() && trustStore.isAllowed(bssid)
            )
        )
    }

    private fun riskOrder(level: NetworkRiskLevel): Int = when (level) {
        NetworkRiskLevel.HIGH -> 0
        NetworkRiskLevel.MEDIUM -> 1
        NetworkRiskLevel.LOW -> 2
    }

    companion object {
        internal const val SCAN_RESULT_TIMEOUT_MS = 8_000L
    }
}

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

/**
 * Scanner WiFi passif et local.
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

    /** Autorisations à demander à l'utilisateur avant un scan. */
    val requiredPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }

    private val mandatoryPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasPermissions(): Boolean = mandatoryPermissions.all { permission ->
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

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
    fun scan(onResults: (List<DiscoveredWifiNetwork>) -> Unit, onError: (String) -> Unit) {
        val manager = wifiManager
        if (manager == null) {
            onError("Service WiFi indisponible sur cet appareil.")
            return
        }
        if (!hasPermissions()) {
            onError("Autorisations Appareils à proximité et Position précise requises pour le scan WiFi Android.")
            return
        }
        if (!isLocationEnabled()) {
            onError("La localisation Android doit être activée pour obtenir les résultats du scan WiFi.")
            return
        }

        release()
        val scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                release()
                readResults(manager, onResults, onError)
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
            readResults(manager, onResults, onError)
            return
        }

        // Some Android/OEM builds accept startScan() but suppress or delay the broadcast
        // because of platform throttling. Never leave the UI stuck indefinitely: after a
        // bounded wait, fall back to Android's latest locally cached scan results.
        val fallback = Runnable {
            if (receiver === scanReceiver) {
                release()
                readResults(manager, onResults, onError)
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
        onResults: (List<DiscoveredWifiNetwork>) -> Unit,
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
            onResults(results)
        } catch (_: SecurityException) {
            onError("Android a refusé l'accès aux résultats WiFi. Vérifiez Position précise et Appareils à proximité.")
        } catch (_: RuntimeException) {
            onError("Le service WiFi Android n'a pas pu fournir les résultats du scan.")
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

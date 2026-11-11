package com.sentinel.quantum.security

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.MacAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.provider.Settings

object WifiConnectPlan {
    fun needsKey(type: WifiSecurityType): Boolean =
        type == WifiSecurityType.WPA || type == WifiSecurityType.WPA2 || type == WifiSecurityType.WPA3

    fun canRequest(type: WifiSecurityType): Boolean =
        type == WifiSecurityType.OPEN || needsKey(type)

    fun usableKey(passphrase: String?): Boolean {
        val value = passphrase?.trim().orEmpty()
        return value.length in 8..63
    }
}

class WifiConnect(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun request(ssid: String, bssid: String, type: WifiSecurityType, passphrase: String?): String {
        if (ssid.isBlank() || WifiRiskEvaluator.isHiddenSsid(ssid)) {
            return "Nom de réseau absent. Rien n’a été demandé."
        }
        if (Build.VERSION.SDK_INT < 29 || !WifiConnectPlan.canRequest(type)) {
            openSettings()
            return "Ce mode s’ouvre dans les réglages Wi-Fi. Aucun code WPS n’est utilisé."
        }
        if (WifiConnectPlan.needsKey(type) && !WifiConnectPlan.usableKey(passphrase)) {
            return "La clé doit être la vôtre, de 8 à 63 caractères. Elle n’est pas enregistrée."
        }
        val specifier = WifiNetworkSpecifier.Builder().setSsid(ssid.trim())
        val address = bssid.trim()
        if (address.isNotBlank() && !address.equals("02:00:00:00:00:00", ignoreCase = true)) {
            runCatching { specifier.setBssid(MacAddress.fromString(address)) }
        }
        val key = passphrase?.trim().orEmpty()
        when (type) {
            WifiSecurityType.WPA3 -> specifier.setWpa3Passphrase(key)
            WifiSecurityType.WPA, WifiSecurityType.WPA2 -> specifier.setWpa2Passphrase(key)
            else -> Unit
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier.build())
            .build()
        val next = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = Unit
            override fun onUnavailable() = Unit
        }
        callback?.let { runCatching { manager.unregisterNetworkCallback(it) } }
        callback = next
        return runCatching {
            manager.requestNetwork(request, next)
            "Android demande confirmation pour « ${ssid.trim()} ». Aucun code WPS n’est utilisé."
        }.getOrElse {
            openSettings()
            "La demande a échoué. Les réglages Wi-Fi sont ouverts."
        }
    }

    fun openSettings() {
        val intent = if (Build.VERSION.SDK_INT >= 29) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
    }
}

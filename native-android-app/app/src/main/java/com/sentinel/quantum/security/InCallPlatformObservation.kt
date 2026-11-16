package com.sentinel.quantum.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

/** Null means the system observation was unavailable, never that the phone is idle. */
internal fun Context.readTelecomInCall(): Boolean? {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return null
    return try {
        getSystemService(TelecomManager::class.java)?.isInCall
    } catch (_: SecurityException) {
        null
    } catch (_: RuntimeException) {
        null
    }
}

internal fun Context.requestAndroidInCallScreen(): String {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
        return "Accès à l’état téléphonique requis pour demander l’écran d’appel Android."
    return try {
        val telecom = getSystemService(TelecomManager::class.java)
            ?: return "Le service Téléphone Android est indisponible."
        telecom.showInCallScreen(false)
        "Ouverture de l’écran d’appel demandée à Android."
    } catch (_: SecurityException) {
        "Android a refusé l’accès à son écran d’appel. Vérifiez les autorisations."
    } catch (_: RuntimeException) {
        "Android n’a pas pu ouvrir son écran d’appel. Utilisez la notification d’appel du système."
    }
}

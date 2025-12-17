package com.sentinel.quantum.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sentinel.quantum.services.SecurityAuditService

/**
 * BootReceiver - Démarrage contrôlé au boot
 * 
 * Lance le service d'audit sécurité au démarrage du téléphone
 * si l'utilisateur a activé cette option.
 * 
 * Comportement:
 * - Vérification préférences utilisateur
 * - Démarrage service si autorisé
 * - Logging pour audit
 * 
 * Privacy:
 * - Pas de données envoyées
 * - Service 100% local
 * - Contrôle utilisateur total
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "BootReceiver"
        const val PREF_AUTO_START = "auto_start_audit_service"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            
            Log.d(TAG, "Boot completed, checking auto-start preference")
            
            // Vérifier préférences utilisateur
            val prefs = context.getSharedPreferences("sentinel_prefs", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean(PREF_AUTO_START, false)
            
            if (autoStart) {
                Log.d(TAG, "Auto-start enabled, starting SecurityAuditService")
                SecurityAuditService.start(context)
            } else {
                Log.d(TAG, "Auto-start disabled by user")
            }
        }
    }
}

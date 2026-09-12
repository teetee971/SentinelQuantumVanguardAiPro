package com.sentinel.quantum.security

import android.telecom.Call
import android.telecom.CallScreeningService
import com.sentinel.quantum.security.SentinelRoomDatabase
import com.sentinel.quantum.security.CallFilterLogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SentinelCallScreeningService : CallScreeningService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        // 1. Extraire le numéro de téléphone entrant sainement
        val incomingHandle = callDetails.handle
        if (incomingHandle == null) {
            respondWithPass(callDetails)
            return
        }

        val rawNumber = incomingHandle.schemeSpecificPart ?: ""
        val cleanNumber = rawNumber.replace(Regex("[\\s\\-\\(\\)]"), "")

        if (cleanNumber.isBlank()) {
            respondWithPass(callDetails)
            return
        }

        serviceScope.launch {
            try {
                // 2. Interroger la base de données Room locale pour vérifier la liste noire
                val db = SentinelRoomDatabase.get(applicationContext)
                val dao = db.callFilterDecisionDao()
                
                // On recherche si une décision de blocage préexistante cible ce numéro ou son empreinte
                val history = dao.latest(500)
                val isBlacklisted = history.any { it.source == cleanNumber && it.action == "BLOCKED" }

                if (isBlacklisted) {
                    // 3. Appliquer le blocage système immédiat et silencieux
                    val response = CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipCallLog(false)
                        .setSkipNotification(true)
                        .build()

                    respondToCall(callDetails, response)

                    // 4. Archiver l'événement d'interception de menace dans le store local
                    val logStore = CallFilterLogStore.get(applicationContext)
                    logStore.recordAsync("BLOCKED", "Interception Liste Noire Automatique", cleanNumber)
                } else {
                    respondWithPass(callDetails)
                }
            } catch (e: Exception) {
                // En cas de défaillance de la base de données, la sécurité passive laisse passer l'appel
                respondWithPass(callDetails)
            }
        }
    }

    private fun respondWithPass(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, response)
    }
}

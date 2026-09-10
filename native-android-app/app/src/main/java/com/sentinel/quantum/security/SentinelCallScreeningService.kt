package com.sentinel.quantum.security

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import com.sentinel.quantum.CallerIdActivity

/** Android system entrypoint. Decisions are local, synchronous, and user-reversible. */
class SentinelCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return

        val store = CallBlocklistStore(this)
        val snapshot = store.snapshot()
        val decision = CallRuleEngine(
            snapshot.blockedNumberHashes,
            snapshot.blockedPrefixes,
            reputationSilencePrefixes = snapshot.signedSilencePrefixes,
            fingerprintsForNumber = store::fingerprintsForNumber
        )
            .evaluate(callDetails.handle?.schemeSpecificPart)
        val response = CallResponse.Builder()
        when (decision.action) {
            CallRuleEngine.Action.BLOCK -> response
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(false)
            CallRuleEngine.Action.SILENCE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                response.setSilenceCall(true)
            }
            CallRuleEngine.Action.ALLOW -> Unit
        }
        respondToCall(callDetails, response.build())

        // Caller-ID rendering happens only after the mandatory platform response. The profile is
        // computed offline and contains no invented person or company identity.
        val verification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (callDetails.callerNumberVerificationStatus) {
                Connection.VERIFICATION_STATUS_PASSED -> "Numéro validé par le réseau"
                Connection.VERIFICATION_STATUS_FAILED -> "Échec de validation réseau"
                else -> "Non vérifié par le réseau"
            }
        } else {
            "Statut indisponible sur cette version Android"
        }
        val localIdentity = LocalContactLookup(this).find(callDetails.handle?.schemeSpecificPart)
        val profile = CallerIdentityResolver.resolve(
            rawNumber = callDetails.handle?.schemeSpecificPart,
            verification = verification,
            displayName = localIdentity?.displayName,
            organisation = localIdentity?.organisation,
            identitySource = if (localIdentity == null) null else "Répertoire local de l’utilisateur",
            identityVerified = localIdentity != null
        )
        runCatching {
            startActivity(Intent(this, CallerIdActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                putExtra(CallerIdActivity.EXTRA_NUMBER, profile.displayNumber)
                putExtra(CallerIdActivity.EXTRA_COUNTRY, profile.countryName)
                putExtra(CallerIdActivity.EXTRA_FLAG, profile.countryFlag)
                putExtra(CallerIdActivity.EXTRA_TYPE, profile.callType)
                putExtra(CallerIdActivity.EXTRA_VERIFICATION, profile.verification)
                putExtra(CallerIdActivity.EXTRA_ACTION, decision.action.name)
                putExtra(CallerIdActivity.EXTRA_REASON, decision.reason)
                putExtra(CallerIdActivity.EXTRA_NAME, profile.displayName)
                putExtra(CallerIdActivity.EXTRA_ORGANISATION, profile.organisation)
                putExtra(CallerIdActivity.EXTRA_SOURCE, profile.identitySource)
                putExtra(CallerIdActivity.EXTRA_IDENTITY_VERIFIED, profile.identityVerified)
            })
        }.onFailure {
            LocalLogger(this).log(
                LocalLogger.LogLevel.WARNING,
                "CallerId",
                "Fiche appelant indisponible; la décision de filtrage a déjà été rendue"
            )
        }
        LocalLogger(this).log(LocalLogger.LogLevel.SECURITY, "CallScreening",
            "Décision=${decision.action} source=${decision.source} motif=${decision.reason}")
        // Persistence is deliberately scheduled only after the mandatory platform response.
        // No database or Keystore access is allowed to consume the five-second screening budget.
        CallFilterLogStore.get(this).recordAsync(decision)
    }
}

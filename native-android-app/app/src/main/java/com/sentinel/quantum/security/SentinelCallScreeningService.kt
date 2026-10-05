package com.sentinel.quantum.security

import android.content.Intent
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.util.Log
import com.sentinel.quantum.CallerIdActivity
import java.util.concurrent.atomic.AtomicLong

/** Android system entrypoint. Decisions are local, synchronous, and user-reversible. */
class SentinelCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return

        // PII-free lifecycle marker used by emulator qualification to prove that Telecom actually
        // invoked Sentinel for an incoming screening callback. Keep it before emergency-number
        // classification: callback observation and a completed rule-engine decision are distinct
        // proofs, especially on Android 10 emulators where emergency classification may be unknown.
        Log.i(LIFECYCLE_TAG, CALLBACK_MARKER)

        val rawCallerNumber = callDetails.handle?.schemeSpecificPart
        val emergency = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getSystemService(TelephonyManager::class.java).isEmergencyNumber(rawCallerNumber.orEmpty())
            } else {
                @Suppress("DEPRECATION")
                PhoneNumberUtils.isEmergencyNumber(rawCallerNumber.orEmpty())
            }
        }.getOrNull()
        // Emergency classification is safety-critical. If Android cannot classify the number,
        // fail open rather than applying a blocking or silencing rule. The lifecycle marker above
        // may still prove callback invocation, but no CALL_SCREENED:* evidence is manufactured.
        if (emergency != false) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val decision = runCatching {
            // Strict screening boundary: process-memory caches only. Do not instantiate
            // CallBlocklistStore here because its constructor obtains SharedPreferences.
            val snapshot = CallBlocklistStore.cachedSnapshotForScreening()
            CallRuleEngine(
                snapshot.blockedNumberHashes,
                snapshot.effectiveBlockedPrefixes,
                reputationSilencePrefixes = snapshot.signedSilencePrefixes,
                fingerprintsForNumber = SCREENING_FINGERPRINTER::cachedCandidates
            ).evaluate(rawCallerNumber)
        }.getOrElse {
            // The platform response must not depend on local rule storage remaining healthy.
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }
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
        val verificationCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (callDetails.callerNumberVerificationStatus) {
                Connection.VERIFICATION_STATUS_PASSED -> "VERIFIED"
                Connection.VERIFICATION_STATUS_FAILED -> "FAILED"
                else -> "NOT_VERIFIED"
            }
        } else {
            "UNKNOWN"
        }
        val verification = when (verificationCode) {
            "VERIFIED" -> "Numéro validé par le réseau"
            "FAILED" -> "Échec de validation réseau"
            "NOT_VERIFIED" -> "Non vérifié par le réseau"
            else -> "Statut indisponible sur cette version Android"
        }
        val profile = CallerIdentityResolver.resolve(
            rawNumber = rawCallerNumber,
            verification = verification,
            displayName = null,
            organisation = null,
            identitySource = null,
            identityVerified = false
        )
        runCatching {
            startActivity(Intent(this, CallerIdActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                putExtra(CallerIdActivity.EXTRA_NUMBER, profile.displayNumber.ifBlank { "Numéro masqué ou indisponible" })
                putExtra(CallerIdActivity.EXTRA_COUNTRY, profile.countryName)
                putExtra(CallerIdActivity.EXTRA_FLAG, profile.countryFlag)
                putExtra(CallerIdActivity.EXTRA_TYPE, profile.callType)
                putExtra(CallerIdActivity.EXTRA_VERIFICATION, profile.verification)
                putExtra(CallerIdActivity.EXTRA_VERIFICATION_CODE, verificationCode)
                putExtra(CallerIdActivity.EXTRA_ACTION, decision.action.name)
                putExtra(CallerIdActivity.EXTRA_REASON, decision.reason)
                putExtra(CallerIdActivity.EXTRA_NAME, profile.displayName)
                putExtra(CallerIdActivity.EXTRA_ORGANISATION, profile.organisation)
                putExtra(CallerIdActivity.EXTRA_SOURCE, profile.identitySource)
                putExtra(CallerIdActivity.EXTRA_IDENTITY_VERIFIED, profile.identityVerified)
            })
        }.onFailure {
            LocalLogger(applicationContext).logAsync(
                LocalLogger.LogLevel.WARNING,
                "CallerId",
                "Fiche appelant indisponible; la décision de filtrage a déjà été rendue"
            )
        }

        // Everything below can touch disk or initialize Room. Keep it outside the screening
        // callback after the mandatory Android response has already been delivered.
        val appContext = applicationContext
        val submitted = runCatching {
            POST_RESPONSE_WORKER.execute {
                val logger = LocalLogger(appContext)
                logger.log(
                    LocalLogger.LogLevel.SECURITY,
                    "CallScreening",
                    "Décision=${decision.action} source=${decision.source} motif=${decision.reason}"
                )

                runCatching {
                    CallFilterLogStore.get(appContext).recordAsync(decision)
                }.onFailure {
                    logger.log(
                        LocalLogger.LogLevel.WARNING,
                        "CallScreening",
                        "Historique de filtrage indisponible; la décision Android a déjà été rendue"
                    )
                }

                // Persist only privacy-bounded call metadata; never the raw or normalized number.
                runCatching {
                    PhonePrivateTimelineStore(appContext).append(CallTimelineMapper.toEvent(decision))
                }.onFailure {
                    logger.log(
                        LocalLogger.LogLevel.WARNING,
                        "CallScreening",
                        "Chronologie privée indisponible; la décision de filtrage a déjà été rendue"
                    )
                }
            }
        }.isSuccess

        if (!submitted) {
            REJECTED_POST_RESPONSE_WORK.incrementAndGet()
        }
    }

    private companion object {
        const val LIFECYCLE_TAG = "SentinelLifecycle"
        const val CALLBACK_MARKER = "CallScreeningService:onScreenCall"
        val SCREENING_FINGERPRINTER = CallNumberFingerprinter()
        val POST_RESPONSE_WORKER = BoundedPostResponseExecutor.create(
            threadName = "sentinel-call-screening-post-response"
        )
        val REJECTED_POST_RESPONSE_WORK = AtomicLong(0L)
    }
}

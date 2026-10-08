package com.sentinel.quantum

import android.app.Activity
import android.app.AppOpsManager
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.sentinel.quantum.security.SmsActivationDiagnostics
import java.lang.ref.WeakReference

internal object SmsActivationAppOpsPolicy {
    internal val operations = setOf(
        AppOpsManager.OPSTR_SEND_SMS,
        AppOpsManager.OPSTR_READ_SMS,
        AppOpsManager.OPSTR_RECEIVE_SMS,
        AppOpsManager.OPSTR_READ_PHONE_STATE
    )

    fun isRelevant(operation: String): Boolean = operation in operations
}

/**
 * Process-level guard against stale SMS authorization UI after Android mutates permissions/AppOps.
 *
 * Android may kill/restart the default SMS app while role/permission reconciliation is still in
 * flight. A Compose snapshot taken during that transition can therefore outlive the framework
 * state it represented. This coordinator observes SEND_SMS AppOp changes and revalidates the full
 * authorization fingerprint whenever the composer becomes visible, including one delayed read for
 * framework reconciliation that settles just after onResume. When authorization changes, it asks
 * the visible composer to refresh its activation epoch in place so attachments, SIM selection and
 * in-flight send/callback state are not discarded by Activity recreation.
 *
 * The coordinator is deliberately read-only: it never grants a role, permission or AppOp.
 */
internal class SmsActivationStateCoordinator(
    private val application: Application
) {
    private data class AuthorizationFingerprint(
        val roleState: SmsActivationDiagnostics.SmsRoleState,
        val authorizationBlockers: Set<SmsActivationDiagnostics.Blocker>
    )

    private val authorizationBlockers = setOf(
        SmsActivationDiagnostics.Blocker.SMS_ROLE_REQUIRED,
        SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED,
        SmsActivationDiagnostics.Blocker.READ_SMS_PERMISSION_REQUIRED,
        SmsActivationDiagnostics.Blocker.RECEIVE_SMS_PERMISSION_REQUIRED,
        SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val diagnostics = SmsActivationDiagnostics(application.applicationContext)
    private val appOpsManager = application.getSystemService(AppOpsManager::class.java)
    private var visibleComposer = WeakReference<SmsComposeActivity>(null)
    private var lastFingerprint: AuthorizationFingerprint? = readFingerprint()

    private val appOpListener = AppOpsManager.OnOpChangedListener { op, packageName ->
        if (
            SmsActivationAppOpsPolicy.isRelevant(op) &&
            (packageName == null || packageName == application.packageName)
        ) {
            scheduleRevalidation()
        }
    }

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) {
            if (activity !is SmsComposeActivity) return
            visibleComposer = WeakReference(activity)
            revalidateVisibleComposer(activity)
            // A permission-role transition can settle just after onResume. Re-read once after the
            // framework has had a chance to finish reconciliation; a changed fingerprint refreshes
            // the existing composer exactly once because lastFingerprint is updated first.
            mainHandler.postDelayed(
                {
                    if (visibleComposer.get() === activity) {
                        revalidateVisibleComposer(activity)
                    }
                },
                SETTLE_RECHECK_MS
            )
        }

        override fun onActivityPaused(activity: Activity) {
            if (visibleComposer.get() === activity) visibleComposer.clear()
        }

        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) {
            if (visibleComposer.get() === activity) visibleComposer.clear()
        }
    }

    fun start() {
        application.registerActivityLifecycleCallbacks(activityCallbacks)
        appOpsManager?.let { manager ->
            SmsActivationAppOpsPolicy.operations.forEach { operation ->
                manager.startWatchingMode(operation, application.packageName, appOpListener)
            }
        }
    }

    private fun scheduleRevalidation() {
        mainHandler.post {
            val activity = visibleComposer.get() ?: return@post
            revalidateVisibleComposer(activity)
        }
    }

    private fun revalidateVisibleComposer(activity: SmsComposeActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val current = readFingerprint() ?: return
        val previous = lastFingerprint
        lastFingerprint = current
        if (previous != null && previous != current) {
            activity.refreshActivationAuthorization()
        }
    }

    private fun readFingerprint(): AuthorizationFingerprint? = runCatching {
        val snapshot = diagnostics.snapshot()
        AuthorizationFingerprint(
            roleState = snapshot.smsRoleState,
            authorizationBlockers = snapshot.blockers.filterTo(linkedSetOf()) {
                it in authorizationBlockers
            }
        )
    }.getOrNull()

    private companion object {
        const val SETTLE_RECHECK_MS = 400L
    }
}

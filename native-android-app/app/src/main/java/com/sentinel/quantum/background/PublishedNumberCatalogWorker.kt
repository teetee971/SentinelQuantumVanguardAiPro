package com.sentinel.quantum.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sentinel.quantum.data.SettingsStore
import com.sentinel.quantum.security.ArcepDirectoryClient
import com.sentinel.quantum.security.CallBlocklistStore
import com.sentinel.quantum.security.CallRuleSyncClient
import com.sentinel.quantum.security.CallRuleSyncConfig
import com.sentinel.quantum.security.OkHttpCallRulePackageTransport
import com.sentinel.quantum.security.PublishedCatalogPolicy
import com.sentinel.quantum.security.PublishedNumberCatalogStore
import com.sentinel.quantum.security.RtrDirectoryClient
import com.sentinel.quantum.security.SignedCallRulePackageVerifier

/**
 * Refreshes official allocation catalogs outside call screening.
 * ARCEP and RTR copies identify number blocks. They do not block or silence calls.
 * A remote spam list is attempted only when a production signed feed is actually configured.
 */
class PublishedNumberCatalogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        PublishedNumberCatalogStore.bind(context)
        val settings = SettingsStore(context)
        if (!settings.isPublishedCatalogRefreshEnabled()) return Result.success()

        val arcepFresh = PublishedNumberCatalogStore.isFresh(PublishedCatalogPolicy.Kind.ARCEP)
        val rtrFresh = PublishedNumberCatalogStore.isFresh(PublishedCatalogPolicy.Kind.RTR)
        val arcepOk = arcepFresh || runCatching { ArcepDirectoryClient().refreshFromNetwork() }.getOrDefault(false)
        val rtrOk = rtrFresh || runCatching { RtrDirectoryClient().refreshFromNetwork() }.getOrDefault(false)
        val catalogsOk = arcepOk && rtrOk

        when (
            PublishedCatalogPolicy.signedSpamFeedPlan(
                productionFeedReady = CallRuleSyncConfig.SYNC_ENABLED &&
                    CallRuleSyncConfig.TRUSTED_KEYS.isNotEmpty(),
                userEnabled = settings.isRuleSyncEnabled()
            )
        ) {
            PublishedCatalogPolicy.SignedFeedPlan.NOT_PROVISIONED,
            PublishedCatalogPolicy.SignedFeedPlan.USER_DISABLED -> Unit
            PublishedCatalogPolicy.SignedFeedPlan.RUN -> runCatching {
                val verifier = SignedCallRulePackageVerifier(
                    CallRuleSyncConfig.TRUSTED_KEYS,
                    CallRuleSyncConfig.EXPECTED_ISSUER_ID
                )
                val transport = OkHttpCallRulePackageTransport(
                    CallRuleSyncConfig.ENDPOINT,
                    CallRuleSyncConfig.ALLOWED_HOSTS
                )
                CallRuleSyncClient(
                    transport,
                    CallBlocklistStore(context),
                    verifier
                ).synchronize()
            }
        }

        return if (catalogsOk) Result.success() else {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}

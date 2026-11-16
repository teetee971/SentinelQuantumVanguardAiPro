package com.sentinel.quantum.security

/**
 * Truth boundary for Android package visibility.
 *
 * A normal app may receive only the packages visible to it under Android package
 * visibility rules. A non-empty list therefore proves only that those packages
 * were visible, never that the device-wide inventory was exhaustive.
 */
object SentinelAppInventoryDiagnostic {
    enum class Scope {
        VISIBLE_PACKAGES_ONLY,
        COMPLETE_BY_VERIFIED_PRIVILEGE
    }

    data class Snapshot(
        val visiblePackageCount: Int,
        val scope: Scope,
        val collectionSucceeded: Boolean,
        val observedAtEpochMillis: Long
    ) {
        init {
            require(visiblePackageCount >= 0) { "Visible package count must be non-negative" }
        }
    }

    fun evaluate(snapshot: Snapshot): SentinelDeviceDiagnostic.Evidence {
        if (!snapshot.collectionSucceeded) {
            return SentinelDeviceDiagnostic.Evidence(
                id = "apps.inventory_scope",
                status = SentinelDeviceDiagnostic.Status.UNKNOWN,
                summary = "Inventaire applicatif non collecté de façon fiable.",
                observedAtEpochMillis = snapshot.observedAtEpochMillis
            )
        }

        return when (snapshot.scope) {
            Scope.VISIBLE_PACKAGES_ONLY -> SentinelDeviceDiagnostic.Evidence(
                id = "apps.inventory_scope",
                status = SentinelDeviceDiagnostic.Status.NOT_ACCESSIBLE,
                summary = "Android limite la visibilité : seules les applications visibles par Sentinel ont été analysées.",
                observedValue = snapshot.visiblePackageCount.toString(),
                observedAtEpochMillis = snapshot.observedAtEpochMillis
            )
            Scope.COMPLETE_BY_VERIFIED_PRIVILEGE -> SentinelDeviceDiagnostic.Evidence(
                id = "apps.inventory_scope",
                status = SentinelDeviceDiagnostic.Status.OK,
                summary = "Inventaire applicatif complet selon un privilège explicitement vérifié.",
                observedValue = snapshot.visiblePackageCount.toString(),
                observedAtEpochMillis = snapshot.observedAtEpochMillis
            )
        }
    }
}

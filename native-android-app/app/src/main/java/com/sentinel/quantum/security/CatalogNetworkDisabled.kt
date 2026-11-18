package com.sentinel.quantum.security

/**
 * Raised when a numbering catalog is neither in memory nor on disk and the user has not allowed
 * a download. Callers must keep local identification and must not treat this as a network outage.
 */
class CatalogNetworkDisabled : IllegalStateException("CATALOG_NETWORK_DISABLED")

internal const val CATALOG_LOCAL_COPY_ABSENT =
    "Copie locale absente. L’actualisation des catalogues est désactivée."

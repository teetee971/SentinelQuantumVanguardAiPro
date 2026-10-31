package com.sentinel.quantum.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * User-consent-only contracts for selecting APK files and a folder to inspect.
 * Launch these intents from an Activity Result API caller; no broad storage access.
 */
internal object ApkDocumentPickerContract {
    fun selectApk(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/vnd.android.package-archive"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
            "application/vnd.android.package-archive",
            "application/octet-stream",
            "application/zip"
        ))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun selectFolder(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
    }

    /**
     * Persist only the read permission actually granted by the document provider.
     * Call after a successful Activity Result, never before user confirmation.
     */
    fun persistReadGrant(context: Context, uri: Uri, resultFlags: Int): Boolean {
        if (uri.scheme != "content") return false
        if (resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return false
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun isTree(uri: Uri): Boolean = DocumentsContract.isTreeUri(uri)
}

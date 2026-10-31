package com.sentinel.quantum.security

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.database.Cursor
import java.util.ArrayDeque

/**
 * Read-only APK discovery for a user-selected Storage Access Framework tree.
 *
 * Does not request broad storage permissions, install packages, or traverse private app data.
 * The caller must launch ACTION_OPEN_DOCUMENT_TREE and obtain the user's consent first.
 */
internal class UserSelectedApkDiscovery(private val context: Context) {
    data class ApkDocument(val uri: Uri, val displayName: String, val sizeBytes: Long?)
    data class ScanResult(val documents: List<ApkDocument>, val complete: Boolean, val scanned: Int, val inaccessibleFolders: Int)

    fun discover(treeUri: Uri, maxDocuments: Int = 5000, maxDepth: Int = 12): List<ApkDocument> =
        scan(treeUri, maxDocuments, maxDepth).documents

    fun scan(treeUri: Uri, maxDocuments: Int = 5000, maxDepth: Int = 12): ScanResult {
        require(maxDocuments in 1..20000)
        require(maxDepth in 0..32)
        require(treeUri.scheme == ContentResolver.SCHEME_CONTENT) { "Only content URI trees are supported" }
        require(DocumentsContract.isTreeUri(treeUri)) { "A SAF tree URI is required" }
        val resolver = context.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val pending = ArrayDeque<Pair<String, Int>>()
        pending.add(rootId to 0)
        val results = ArrayList<ApkDocument>()
        val visited = HashSet<String>()
        var scanned = 0
        var inaccessibleFolders = 0
        var depthLimited = false
        var documentLimitReached = false
        while (pending.isNotEmpty() && scanned < maxDocuments) {
            val (parentId, depth) = pending.removeFirst()
            if (!visited.add(parentId)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )
            val cursor: Cursor = try {
                resolver.query(children, projection, null, null, null).also { if (it == null) inaccessibleFolders++ } ?: continue
            } catch (_: SecurityException) {
                inaccessibleFolders++
                continue // User may have revoked the tree grant.
            } catch (_: IllegalArgumentException) {
                inaccessibleFolders++
                continue // Provider may no longer expose the directory.
            } catch (_: android.database.sqlite.SQLiteException) {
                inaccessibleFolders++
                continue // Provider query failed before returning a cursor.
            }
            try { cursor.use {
                val idIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                while (it.moveToNext()) {
                    if (scanned >= maxDocuments) {
                        documentLimitReached = true
                        break
                    }
                    scanned++
                    val id = it.getString(idIndex) ?: continue
                    val name = it.getString(nameIndex) ?: continue
                    val mime = it.getString(mimeIndex)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < maxDepth) pending.add(id to depth + 1) else depthLimited = true
                    } else if (name.endsWith(".apk", ignoreCase = true)) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                        val size = if (it.isNull(sizeIndex)) null else it.getLong(sizeIndex)
                        results.add(ApkDocument(uri, name, size))
                    }
                }
            } } catch (_: SecurityException) {
                inaccessibleFolders++
                continue // Permission revoked during cursor iteration.
            } catch (_: IllegalArgumentException) {
                inaccessibleFolders++
                continue // Provider returned invalid or inconsistent columns.
            } catch (_: android.database.sqlite.SQLiteException) {
                inaccessibleFolders++
                continue // Malformed provider cursor.
            }
        }
        return ScanResult(results, pending.isEmpty() && !documentLimitReached && !depthLimited && inaccessibleFolders == 0, scanned, inaccessibleFolders)
    }

    fun inspectSelectedDocument(uri: Uri): ApkDocument? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        val cursor = try {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
            ) ?: return null
        } catch (_: SecurityException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: android.database.sqlite.SQLiteException) {
            return null
        }
        return try { cursor.use {
            if (!it.moveToFirst()) return@use null
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex < 0) return@use null
            val name = it.getString(nameIndex) ?: return@use null
            if (!name.endsWith(".apk", ignoreCase = true)) return@use null
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            val size = if (sizeIndex < 0 || it.isNull(sizeIndex)) null else it.getLong(sizeIndex)
            ApkDocument(uri, name, size)
        } } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: android.database.sqlite.SQLiteException) {
            null
        }
    }
}

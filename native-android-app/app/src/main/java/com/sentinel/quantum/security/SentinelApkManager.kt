package com.sentinel.quantum.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.text.DateFormat
import java.util.Date

/**
 * User-confirmed OS package UI only. This class never invokes a silent install,
 * uninstall or root operation. Logging records REQUESTED, not SUCCESS.
 */
internal class SentinelApkManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("sentinel_apk_manager_audit", Context.MODE_PRIVATE)
    private val protectedIds = setOf("com.sentinel.quantum", "com.sentinel.quantum.labpreview")

    fun installUserSelected(uri: Uri, evidence: ApkEvidence) {
        require(evidence.signatureVerified == true) { "Signature APK non validée : installation bloquée" }
        val packageName = evidence.packageName ?: throw IllegalArgumentException("Identifiant APK indéterminé")
        require(packageName !in protectedIds) { "Mise à jour de Sentinel interdite depuis le laboratoire" }
        require(uri.scheme == "content") { "Seuls les documents sélectionnés par Android sont acceptés" }
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        require(intent.resolveActivity(context.packageManager) != null) { "Aucun installateur Android disponible" }
        record("INSTALLATION_DEMANDEE", packageName)
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun requestUninstall(packageName: String) {
        require(packageName.isNotBlank() && packageName !in protectedIds) {
            "Désinstallation de Sentinel ou identifiant vide interdite"
        }
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        require(intent.resolveActivity(context.packageManager) != null) { "Désinstallateur Android indisponible" }
        record("DESINSTALLATION_DEMANDEE", packageName)
        context.startActivity(intent)
    }

    fun entries(): List<String> = prefs.getString("entries", "").orEmpty().lines().filter { it.isNotBlank() }.takeLast(30).asReversed()

    private fun record(event: String, packageName: String) {
        val safeId = packageName.take(180).replace(Regex("[\\r\\n|]"), "_")
        val entry = "${DateFormat.getDateTimeInstance().format(Date())} | $event | $safeId"
        val all = (entries().asReversed() + entry).takeLast(30)
        prefs.edit().putString("entries", all.joinToString("\n")).apply()
    }
}

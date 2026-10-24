package com.sentinel.quantum.security

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.security.MessageDigest
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

/**
 * Reads only a user-selected APK through Android's document picker.
 * Copies into app cache for PackageManager parsing, never executes or installs the APK.
 */
class SelectedApkInspector(private val context: Context) {
    suspend fun inspect(uri: Uri): ApkEvidence = withTimeout(45_000L) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val inspectionJob = coroutineContext[kotlinx.coroutines.Job]
        withTemporaryApk(context.cacheDir) { file ->
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            val limit = 350L * 1024 * 1024
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Fichier illisible")
            input.use { source ->
                file.outputStream().use { target ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        inspectionJob?.ensureActive()
                        val n = source.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > limit) throw IllegalArgumentException("APK trop volumineux")
                        digest.update(buf, 0, n)
                        target.write(buf, 0, n)
                    }
                }
            }
            if (total == 0L) throw IllegalArgumentException("APK vide")
            inspectionJob?.ensureActive()
            ApkArchiveValidator.validate(file)
            val signatureVerified = ApkSignatureVerifier.verify(file)
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val info = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageArchiveInfo(
                    file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            }
            val signing = if (Build.VERSION.SDK_INT >= 28) info?.signingInfo else null
            val signers = if (signing == null) null else {
                val certificates = if (signing.hasMultipleSigners())
                    signing.apkContentsSigners else signing.signingCertificateHistory
                certificates?.map { cert ->
                    MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
                        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                }?.toSet()
            }
            val versionCode = info?.let {
                if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else {
                    @Suppress("DEPRECATION")
                    it.versionCode.toLong()
                }
            }
            ApkEvidence(
                packageName = info?.packageName,
                versionCode = versionCode,
                fileSha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) },
                signerSha256 = signers,
                signatureVerified = signatureVerified
            )
        }
        }
    }
}

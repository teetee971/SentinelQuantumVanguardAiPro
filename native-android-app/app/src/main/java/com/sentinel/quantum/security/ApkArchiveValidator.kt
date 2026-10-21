package com.sentinel.quantum.security

import java.io.File
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** Structural screening only. Cryptographic validity is checked separately with apksig. */
internal object ApkArchiveValidator {
    fun validate(file: File) {
        require(file.isFile && file.length() > 0) { "APK vide ou absent" }
        file.inputStream().use { input ->
            val header = ByteArray(4)
            require(input.read(header) == 4 &&
                header[0] == 0x50.toByte() && header[1] == 0x4b.toByte() &&
                header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) {
                "Le fichier n'est pas une archive APK ZIP"
            }
        }
        try {
            ZipFile(file).use { zip ->
                require(zip.getEntry("AndroidManifest.xml") != null) { "Manifeste Android absent" }
                require(zip.entries().asSequence().any { it.name.matches(Regex("classes[0-9]*\\.dex")) }) {
                    "Code DEX absent : archive APK non prise en charge"
                }
            }
        } catch (e: ZipException) {
            throw IllegalArgumentException("Archive ZIP invalide", e)
        }
    }
}

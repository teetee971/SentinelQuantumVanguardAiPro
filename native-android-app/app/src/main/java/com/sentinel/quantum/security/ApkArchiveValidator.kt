package com.sentinel.quantum.security

import java.io.File
import java.util.zip.ZipFile

object ApkArchiveValidator {
    fun validate(file: File) {
        require(file.isFile && file.length() > 0) {
            "APK absent ou vide"
        }

        ZipFile(file).use { zip ->
            require(zip.size() in 1..100000) {
                "Nombre d'entrees ZIP invalide"
            }

            val entries = zip.entries()
            val names = HashSet<String>()
            var manifestFound = false

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name

                require(
                    !name.startsWith("/") &&
                    !name.contains("\\") &&
                    name.split('/').none { it == ".." }
                ) {
                    "Chemin APK dangereux"
                }

                require(names.add(name)) {
                    "Entree ZIP dupliquee"
                }

                if (name == "AndroidManifest.xml") {
                    manifestFound = true
                }
            }

            require(manifestFound) {
                "Manifest Android absent"
            }
        }
    }
}

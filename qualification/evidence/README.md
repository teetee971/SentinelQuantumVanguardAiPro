# Conservation des preuves

`RAW_LOGS_AND_PATCHES.zip` conserve les octets bruts des journaux et du patch de reproduction. Les copies `.log` lisibles ont uniquement leurs espaces de fin de ligne et lignes vides finales normalisés pour respecter git diff --check. Le patch de reproduction, dont les lignes de contexte contiennent des espaces significatifs, reste intégralement dans l’archive. Les XML/JUnit/SARIF et résultats JSON conservent leur contenu. Les empreintes de sources se rapportent aux commits de source indiqués, avant les commits ultérieurs de preuves.

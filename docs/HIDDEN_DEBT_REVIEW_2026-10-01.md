# Revue des dettes cachées — 1 octobre 2026

Base : `8b794b3711382b276f091154e6718967c1bc0443`. Revue ciblée Phone Core : reducer SMS, persistance de progression, receiver et chronologie privée. Ce document ne constitue pas une attestation d'absence de dette dans tout le dépôt.

| Défaut démontré | Correction et régression |
| --- | --- |
| Les callbacks identiques pouvaient retourner un Outcome puis répéter providers, bus, logs et chronologie, même sans nouvelle preuve agrégée. | Le reducer retourne null sans mutation pour les doublons ; le store et le receiver abandonnent avant ces effets. Tests doublons SENT et échec. |
| Une partie en échec pouvait repasser en succès sur un callback tardif du même token, permettant un agrégat de réussite erroné. | Échec conservé pour le même token et la même étape ; une nouvelle tentative nécessite un nouveau token. Succès puis échec peut se dégrader. Tests multiparties SENT et DELIVERED. |
| Le sanitizer supprimait des caractères et tronquait les chaînes ; `SMS_ALL_PARTS_SENT!` pouvait devenir le signal exact de certification. | Rejet des tokens invalides ou trop longs, sans réparation. Fonction pure partagée par lecture et écriture ; tests signaux malformés, longueur et horodatage. |

Vérifications locales : guards vérité produit et manifeste réussis. Les régressions Kotlin et la compilation restent à exécuter par la CI du commit final ; ne pas utiliser une CI antérieure comme preuve des corrections.

Limites : pas de téléphone physique ni de capture Android ; comportement opérateur, accessibilité visuelle, contraintes constructeur et tests Mesh déployé restent à vérifier. Le stockage borné et les TTL restent des politiques explicites ; aucune promesse de conservation illimitée ou de correction de toutes les races et erreurs de disque n'est faite.

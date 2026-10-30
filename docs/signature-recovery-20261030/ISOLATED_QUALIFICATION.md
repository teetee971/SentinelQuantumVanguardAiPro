# Si la clé d'origine reste indisponible

L'absence de clé dans cet environnement ne prouve pas sa disparition sur le téléphone, dans les backups ou Codemagic. Sans cette clé ni une lignée autorisée déjà valide, Android ne permet pas une mise à jour ordinaire de com.sentinel.quantum signé6891 avec un APK signédee0 en conservant son identité. Ne pas contourner cette protection par désinstallation, effacement, root ou signature arbitraire.

1. Conserver le Sentinel actuel, ses données privées et ses rôles sur le S24+. Aucun export de bases privées nécessaire à la recherche de certificat.
2. Pour poursuivre la qualification fonctionnelle complète indépendamment du conflit, utiliser un **second appareil de test** autorisé, sans installation Sentinel incompatible ni données à préserver. Une installation fraîche d'un candidat debug sur ce second appareil ne prouve pas une mise à jour/migration du S24+ principal. Toute installation reste soumise à autorisation, jamais automatisée ici.
3. Un profil secondaire/work profile n'est pas présumé disponible ni équivalent pour Telecom/SMS sur Samsung. Ses restrictions de rôles doivent être vérifiées séparément ; pas de conseil de modifier les données du profil principal.
4. La variante existante com.sentinel.quantum.labpreview a un identifiant séparé mais retire explicitement InCallService, activités Téléphone/Messages, récepteurs SMS/MMS et permissions de communication dans son manifeste. Elle peut servir à des surfaces limitées ; **elle ne qualifie pas les26 scénarios Phone Core**. Aucun APK de ce type n'a été produit dans cette réparation.
5. Les tests locaux et les revues peuvent continuer ; ne pas inventer de disponibilité Voice, de conformité D.1 complète ou de validation physique.

Retour au chemin de mise à jour seulement quand une signature6891 réelle ou une lignée autorisée est vérifiée. Pas de nouvelle clé créée pour maquiller la compatibilité.

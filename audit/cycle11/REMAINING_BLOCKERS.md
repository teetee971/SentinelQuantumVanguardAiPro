# Blocages et suites Sentinel C11

**NO-GO global.** Le code du protocole, les suites JVM et la construction de la paire debug sont vérifiés. Les preuves d’exécution Android et de distribution restent des critères ouverts.

| Priorité | Blocage ou risque | Preuve requise |
| --- | --- | --- |
| P0 C11 | Appareil et copie Termux indisponibles. ADB local vide. Les résultats physiques signalés par le propriétaire ont un SHA de source et des empreintes APK encore inconnus dans ce workspace. | Récupérer les logs et empreintes de la copie locale et exécuter la paire C11 dédiée sur le S24+. |
| P0 C11 | Persistance après arrêt et reboot, vrai Keyguard, absence d’action non autorisée et inférence réelle Qwen ARM64 restent à établir. | Les deux reçus indépendants du protocole, les fenêtres HMAC 0/1, les traces d’annulation et les observations Android de la procédure physique. |
| P0 distribution | Signature de livraison, preuve d’installation et fonctions Android critiques sur appareil manquantes. Les politiques APK/AAB sont couvertes par des tests de contrat locaux. | Candidat de distribution signé et traçable, validation des permissions et des parcours Phone Core, revue Play et concordance des promesses. |
| P1 Phone Core | Le raccrochage depuis la notification, la réception MMS avec rôle retiré et les courses autour de la soumission et de la recréation restent ouverts. | Régression ciblée par cas, correction dédiée puis Telecom/One UI et deux lignes de test sur appareil. |
| P1 réseau | Périmètre LAN, rappels NSD, notifications traceurs, preuve de protection et expiration des pairs VPN, état de révocation du relais Mesh. | Reproductions défensives ciblées et tests Wi-Fi/BLE sur appareil. Étendre la qualification au service VPN/Mesh seulement quand l’activation et la révocation sont démontrées. |
| P1 journal | L’arrêt entre deux remplacements entraîne un refus de vérification. Une restauration conjointe cohérente reste hors de la preuve du format actuel. | Documenter la garantie de détection délivrée et qualifier une éventuelle évolution du mécanisme séparément. |
| P1 produit | L’exception HTTP locale exacte manque dans le brouillon Play et l’accueil public doit rester aligné sur la disponibilité déployée. | Mettre la fiche de distribution en accord avec la politique et relier les pages déployées à leur provenance de build. |
| P2 | Races de cache contacts/récents et contrôles UI inertes inventoriés. | Tests à la frontière d’état et correction dans les chantiers concernés. |

Codemagic n’a fourni ni projet accessible ni jeton dans cet environnement. La validation de build a été menée par Gradle local. La branche locale conserve les commits de cette mission. Toute publication ultérieure relève d’une décision distincte.

[Constats et références](AUDIT_GLOBAL_C11.md) · [Procédure de qualification S24+](VALIDATION_S24_PLUS.md) · [Matrice produit](PRODUCT_READINESS.md)

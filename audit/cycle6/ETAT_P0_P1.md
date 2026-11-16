# Cycle6 — exception locale autorisée et activée — NO-GO production

L'utilisateur a expressément autorisé l'exception proposée au cycle5. Elle est
maintenant appliquée au manifeste et au XML de production. Ne pas réappliquer le
patch historique du cycle5 sur cet état:il est déjà inclus.

Portée:base HTTP cleartext refusée,exception pour le seul hôte127.0.0.1,sans
sous-domaines. Android Network Security Config ne restreint pas les ports;
le client SentinelAiLocalEngine impose lui-même127.0.0.1:18080,Proxy.NO_PROXY,
aucune redirection et l'authentification HMAC. Aucun endpoint distant ni clé API
cloud n'est ajouté. Les permissions,usesCleartextTraffic=false et tous les
composants déjà déclarés restent identiques,comparaison XML structurelle effectuée.
Les contrôles HTTPS existants ne sont pas remplacés par du HTTP distant.

## Exécutions vérifiées

- Trois tests statiques XML:rouges avant activation(1 échec,2 erreurs de fichier
  absent),puis3 réussis après activation. Ils ne simulent pas Android.
-342 suites JVM,1623 tests exécutés,0 échec,0 erreur,0 ignoré.
-JavaScript complet avec PostgreSQL de test isolé:1328 réussis,0 échec/ignoré.
-Compilation Kotlin,compilation instrumentée,assembleDebug et assemblage de
 l'APK de tests:BUILD SUCCESSFUL.
-Signatures des deux APK vérifiées avec apksigner(exit0). Manifeste APK extrait;
 référence networkSecurityConfig présente,usesCleartextTraffic=false conservé.
-XML compilé de l'APK extrait avec aapt2:base false,domaine127.0.0.1 true,
 includeSubdomains=false. Le résultat brut est dans apk-network-resource.log.
-Aucun appareil ADB détecté. Aucun test instrumenté exécuté.

Nouveau test instrumenté:SentinelAiNetworkPolicyInstrumentationTest vérifie sur
le vrai Android l'exception127.0.0.1 et les refus example.com,localhost,127.0.0.2,
::1,127.0.0.1.example.com. Il est compilé,mais ses assertions ne sont pas déclarées
réussies. Les particularités de l'OS cible doivent être mesurées,sans affaiblir
le test pour masquer un élargissement de politique.

Les commandes exactes,résultats et SHA256 des APK figurent dans validation.json.
APK debug:f8fed4adf319318e3e4fd72804801498d81464e996c63bfb877db31f0ed416d2.
APK instrumenté:712c2845cb265bc8b29592c0a652060f963b5bb96926a067edc6c903a591aaec.
Aucun keystore,clé privée,APK ou secret d'appariement n'est publié dans Git.

## Étape physique encore obligatoire

Travailler dans un nouveau clone de la branche de transfert,pas dans le dépôt
local de référence modifié. Construire localement sous Termux/Ubuntu proot avec
le SDK/JDK qualifié. Ne pas désinstaller l'app existante pour contourner une
incompatibilité de signature. Les APK produits ici emploient le certificat debug
de cet environnement,qui peut différer du certificat du Galaxy.

Sur un appareil de test avec ADB déjà autorisé,exécuter les tests instrumentés:

    adb shell am instrument -w -e class com.sentinel.quantum.ai.SentinelAiNetworkPolicyInstrumentationTest,com.sentinel.quantum.ai.SentinelAiActivityInstrumentationTest,com.sentinel.quantum.ai.SentinelAiAuditJournalInstrumentationTest com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner

Ensuite exécuter réellement le service llama.cpp/Qwen ARM64 local avec le modèle
épinglé du README backend/local-ai. Dans Paramètres→Agent IA local:consentement,
appariement privé,demande d'un point de contrôle,confirmation explicite dans le
verrouillage Android,vérification du résultat et du journal. Ne jamais publier
la clé d'appariement ni des captures contenant des informations personnelles.
Mesurer aussi refus de consentement,mauvaise clé,moteur arrêté,expiration,
annulation,home/rotation,mort du processus et résultats d'authentification tardifs.

Le moteur réel a été éprouvé sur x86_64 au cycle5,pas encore sur S24+. Cette
activation de politique réseau ne démontre pas une inférence Android réussie,
ni une authentification humaine réelle,ni un parcours de bout en bout réussi.
Le seul effet autorisé reste un point de contrôle de journal,sans action Phone
Core/SMS/MMS/VPN/Wi-Fi/Bluetooth. Les limites des cycles4/5 demeurent:capacité du
journal,rollback conjoint indétectable,révocation globale interinstances absente,
LiveKit sans barrière fournisseur qualifiée,performances/thermiques ARM64 et
régressions physiques non qualifiées. Budget0USD et décision NO-GO maintenus.

# Cycle 2 — 16 novembre 2026 — NO-GO production

Base: 96d7981e4cb4b71bb35647e35a04fb06b11dd45f, branche audit/sentinel-integral-p0-20261116.
Les 21 corrections précédentes sont conservées. Aucun changement de source Android,
Phone Core, SMS/MMS, VPN, Wi-Fi, Bluetooth, Tracker ou schéma Room dans ce cycle.
Aucun push, déploiement, clé OpenAI ou service payant.

## Corrections démontrées

P1 LiveKit: absence de barrière de révocation effective. Les créations sont désormais
refusées sans adaptateur de révocation. Fermeture persistée pending avant I/O,
verrou distribué par session, confirmation obligatoire avant succès, maintien des
tombstones en cas d'erreur/timeout; pièces JWT limitées au microphone, sans données,
et salle/identité isolées pour chaque session. Les tests injectent explicitement
un faux adaptateur; aucun adaptateur fournisseur réel n'est livré. Un JWT ancien
ou une session média active n'est PAS révoqué par simple expiration ou suppression
locale. Risque résiduel bloquant, y compris jetons émis avant cette correction.

Garde Android: correction du contrat statique qui exigeait du travail HMAC/Keystore
synchrone au démarrage et une permission coarse plafonnée. Les neuf tests de mutation
vérifient démarrage sans I/O, screening avant maintenance et contrôle effectif de
toutes les permissions. Aucune permission Android n'a été retirée ou affaiblie.

Service IA local expérimental: llama.cpp MIT et Qwen Apache2.0, empreinte modèle
vérifiée, propositions bornées, authentification HMAC, nonce serveur unique expirant,
refus du rejeu, annulation du processus. Aucun droit d'approbation/exécution donné
au moteur. Aucune dépense. Voir backend/local-ai/README.md.

## Exécutions conservées

- Compilation Kotlin Android: BUILD SUCCESSFUL.
- Tests Android JVM complets:339 suites,1595 tests,0 échec/erreur/ignoré.
- Compilation tests instrumentés: BUILD SUCCESSFUL; aucune exécution sur appareil.
- JavaScript complet avec PostgreSQL17 isolé:1328 réussites,0 échec/ignoré.
- PostgreSQL ciblé:5 tests réellement exécutés, nettoyage réussi.
- Backend vocal:38 réussites.
- Service IA local:10 réussites, avertissement de dépréciation TestClient.
- Inférence réelle x86_64 via service:HTTP200, réponse2+2=4, MAC vérifiés,
  rejeu409; environ5,3secondes. Ce test synthétique ne qualifie pas le modèle.
- Essai SmolLM2:sortie réelle incorrecte sur2+2, candidat non retenu.
- Assemblage debug:interrompu au traitement DEX, aucun statut final enregistré.
  Aucun APK de ce cycle n'est déclaré assemblé/signé/qualifié.

Les rapports antérieurs sont des preuves d'exécution conservées, pas des tests
réexécutés lors de la reprise après interruption. Rapports et commandes dans ZIP.

## Blocages et prochaine validation physique

P0:aucun raccordement Android réel de ce moteur, aucune authentification humaine
Android vérifiée de bout en bout, aucun adaptateur d'action ni journal Android
persistant qualifié. Les contrats JVM ne prouvent pas ces fonctions.
P1:barrière fournisseur LiveKit absente; reconnexion/rejeu et fermeture média
à vérifier réellement. Ne pas activer la délivrance en contournant le refus.
P1:finir assembleDebug, contrôler manifeste APK et apksigner, puis exécuter les
instrumentés sur S24+. Aucun appareil ADB détecté pendant les contrôles disponibles.
P2:compatibilité ARM64, performances, consommation/thermiques du modèle non mesurées;
avertissements de dépendances/Gradle à traiter séparément sans refonte massive.

Sur S24+:reconstruire dans une copie isolée; compiler Kotlin, exécuter les tests,
compiler les instrumentés, assembler l'APK, contrôler signature/manifeste/empreinte.
Exécuter les instrumentés et les parcours réels Phone Core/SMS/MMS/VPN, permissions
refusées/révoquées, redémarrage/process death et pertes réseau. Compiler llama.cpp
ARM64 et vérifier le GGUF avant tout essai. Ne pas présenter le service Termux
séparé comme un moteur autonome déjà embarqué dans l'APK.

Décision:NO-GO. Ce paquet constitue une livraison intermédiaire traçable.

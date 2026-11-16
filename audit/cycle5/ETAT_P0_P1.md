# Cycle5 — raccordement Android expérimental — NO-GO production

Base:b69fe97c146cd3bd52ff00434886cc538c1d2931, transfert précédent8dd85d2b760d449ef626bc702ae6cb1281351d4d.
Budget0USD. Aucun service cloud,clé API payante,déploiement ou changement de main.

## Code de production raccordé

Paramètres V3→SentinelAiActivity (non exportée)→consentement explicite,non mémorisé
→contrôle NetworkSecurityPolicy pour127.0.0.1→appariement HMAC et moteur local
→réponse/proposition strictement bornée→Session.propose→bouton de confirmation
→verrouillage Android via KeyguardManager→ticket exact éphémère et à usage unique
→Authority.approve→Authority.execute→journal HMAC→résultat explicite.

Une seule action est inscrite:CREATE_AUDIT_CHECKPOINT, paramètres vides, qui écrit
un événement sans contenu personnel dans le journal. Aucun appel,SMS,MMS,VPN,
Wi-Fi,Bluetooth ou traitement Phone Core n'est accessible au modèle. Le moteur
ne reçoit ni Owner,ni DecisionPort,ni preuve d'authentification.

Le journal,le réseau et l'action s'exécutent sur un worker série hors thread UI.
La révocation invalide immédiatement ticket,consentement et génération,ferme le
client,puis révoque l'autorité sur ce worker. Le message distingue la demande de
révocation de son achèvement. Un effet journal déjà commencé peut finir avant
la révocation effective; aucune annulation rétroactive n'est revendiquée.

L'authentification demande un verrouillage Android sécurisé. Chaque lancement
emploie une clé de résultat UUID et capture son ticket exact. Lire simplement le
ticket mutable au retour aurait risqué d'associer un ancien résultat à une
nouvelle proposition:le callback refuse désormais toute identité remplacée.
Un callback ne crée jamais une proposition ni ne sélectionne une action.
Le lancement du verrouillage est la seule exception à la fermeture sur onStop;
son retour exige toujours consentement,activité au premier plan,ticket valide,
expiration monotone,appareil sécurisé et vérification de l'autorité. Rotation ou
mort du processus ne restaure aucune preuve. Ces comportements natifs doivent
encore être vérifiés sur appareil; les tests JVM n'en constituent pas une preuve.

Aucun texte ni clé n'est enregistré dans l'état sauvegardé. Autofill est désactivé
sur cet écran à partir d'API26 et FLAG_SECURE protège son contenu. Les données du
modèle sont affichées séparément du statut d'exécution applicatif. Une réponse du
modèle déclarant une réussite ne devient pas une réussite de l'application.

## Politique réseau:proposition INACTIVE

Le manifeste conserve usesCleartextTraffic=false. Le fichier
loopback-policy-proposed.patch est une proposition vérifiée par git apply --check,
PAS une modification activée. Il ajouterait une configuration avec base cleartext
false et une exception uniquement pour127.0.0.1,sans sous-domaines. Les autres
hôtes n'y recevraient aucune autorisation HTTP. Sur un Android refusant actuellement
ce transport,le bouton d'inférence reste désactivé. Les chemins HTTPS existants,
leurs contrôles et leurs permissions ne sont pas modifiés par ce cycle.

La consigne utilisateur exige confirmation avant changement sensible de
configuration:une autorisation explicite sera demandée avant activation du patch.
Le HMAC reste obligatoire même si cette exception locale est approuvée.

## Exécutions

-342 suites Android JVM,1623 tests,0 échec,0 erreur,0 ignoré sur les sources finales.
-Neuf tests nouveaux:preuve à usage unique,absence d'authentification implicite,
 annulation,proposition différente,expiration,révocation,résultat ancien,horloge
 reculant et consommateurs concurrents.
-JavaScript complet avec PostgreSQL isolé:1328 tests réussis,0 échec/ignoré.
 Une première tentative de reporting mélangeait les deux reporters;commande
 corrigée puis suite réexécutée avec rapports XML/TAP séparés. Aucun échec caché.
-Gardes manifeste et cohérence UI:réussis. Permissions et composants existants
 comparés structurellement:identiques;seule activité interne ajoutée au manifeste.
-Parmi101 fichiers protégés,100 identiques;le manifeste diffère uniquement par
 l'enregistrement de cette activité. Schéma Room et correctifs MMS/HMAC conservés.
-Qwen réel sur x86_64 a proposéCREATE_AUDIT_CHECKPOINT lors d'un essai synthétique.
 Aucune action exécutée pendant ce test;cela ne qualifie ni le modèle ni Android.
-Le test instrumenté d'entrée vérifie consentement initial absent,bouton inactif,
 activité non exportée et absence d'initialisation du journal avant consentement.
 Il est à compiler puis à exécuter sur appareil;aucun résultat matériel n'est présumé.

## Limites et contrôles physiques obligatoires

P0:NO-GO. Aucun appareil ADB disponible aux derniers contrôles. Le parcours complet
clé locale→modèle ARM64→proposition→vraie authentification→écriture journal→résultat
n'a pas été exécuté sur S24+. Le point de contrôle est le seul effet autorisé.
Vérifier réellement PIN/biométrie selon OS,annulation,home/retour,rotation,écran
verrouillé,mort du processus,réponse tardive,proposition expirée,clavier/overlays,
et concurrence révocation/exécution. La révocation applicative globale entre
plusieurs instances d'Activity n'est pas raccordée;chaque instance révoque son
propre Owner. Ne pas confondre cela avec une révocation globale interinstances.

P1:journal limité256événements,aucune rotation/export UX fourni;indisponibilité
bloque les effets. Une restauration conjointe journal+ancre reste indétectable.
Un refus peut ne pas être journalisable si le stockage est indisponible;aucune
autorisation ne suit cet échec. Les erreurs d'écriture ne prouvent pas l'absence
d'un événement partiellement persisté:OUTCOME_UNKNOWN n'est jamais transformé en
succès. Le service de génération peut poursuivre son calcul borné30s après une
déconnexion client;aucune annulation instantanée du calcul n'est revendiquée.

P1:LiveKit reste sans barrière fournisseur validée. Les anciens accès média ne
sont pas déclarés révoqués. Les résultats ARM64,mémoire,énergie,thermiques et les
régressions physiques Phone Core/SMS/MMS/VPN/Wi-Fi/Bluetooth restent à qualifier.

Après approbation éventuelle du patch réseau,construire une copie de test et suivre
la procédure S24+ du cycle3,sans désinstaller une app dont la signature diffère.
Tests ciblés d'entrée et de journal:

    adb shell am instrument -w -e class com.sentinel.quantum.ai.SentinelAiActivityInstrumentationTest,com.sentinel.quantum.ai.SentinelAiAuditJournalInstrumentationTest com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner

Le succès de ces tests ciblés ne remplacerait toujours pas le vrai parcours
Keyguard/moteur ni les essais physiques critiques.

Compilation Kotlin et des instrumentés réussie sur les sources finales;
APK debug et APK de tests construits,signatures apksigner vérifiées(exit0).
Manifeste APK extrait,activité interne présente,usesCleartextTraffic=false conservé.
ADB ne présente aucun appareil:aucun test instrumenté exécuté. Les empreintes et
commandes sont dans validation.json. Le code seul ne démontre pas le vrai résultat
Keyguard ni le bon fonctionnement du parcours Android sur S24+.

GitHub signalait six alertes de dépendances sur sa branche par défaut lors du
transfert précédent(1 élevée,4 modérées,1 faible). Elles ne sont ni résolues ni
qualifiées par ce cycle et ne constituent pas une analyse des dépendances du
présent instantané. La décision NO-GO reste inchangée.

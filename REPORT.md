# Cycle 10 — bornes de sortie et appariement — NO-GO

Base: 8ff3ceb38745846d07b7a593aba2fde3dad32822.
Sources locales et deux correctifs reçus dans l'archive SHA256 21f18eb1fa390768d5df84372152a33125e3fd4fe95c6e99a67a016c45b9f524 préservés au commit 3ad6864.
Code corrigé et compilé: 8b7f388e82dd9c01855bba78e80c2d37c4404682. Aucun fichier du téléphone remplacé par ce travail cloud.

## Corrections
- Lectures stdout/stderr concurrentes plafonnées avant ajout en mémoire (8192/65536 octets, chunks <=4096). Sur dépassement: refus502, arrêt du processus direct puis récupération, pipes vidés sans accumulation. Expiration30s et annulation simple/double conservent le nettoyage avant libération du verrou occupé.
- Barrière d'appariement commune aux activités du processus, tickets capturés avant mise en file. Oublier invalide immédiatement les anciens tickets; aucune sauvegarde tardive autorisée. Suppression effective hors UI; échec garde les nouveaux accès bloqués dans ce processus. Révocation de session sérialisée avec les opérations existantes. La sauvegarde passe également par la barrière de révocation du processus.
- Mémorisation AES-GCM, copie utilisateur et réutilisation du secret côté service transférées et conservées. Aucune nouvelle fonctionnalité ajoutée.

## Exécutions
Python: six nouveaux cas bornes/nettoyage d'abord rouges (deux reproductions d'overflow et quatre cas API initialement absente); six réussites après correction. Régression locale complète:22 tests réussis, zéro échec/erreur/ignoré, trois avertissements dépréciation. Deux faux processus des tests historiques ont dû adopter les flux/attente; assertions stdin/kill/reap conservées. Une tentative intermédiaire comptait20 réussites et2 échecs pour ces anciens doubles, consignée.
Coordinateur: sept tests JUnit réussis, dont ordres contrôlés par CountDownLatch. Mutation dans une copie de test supprimant l'invalidation de génération: trois échecs attendus; mutation jamais ajoutée aux sources livrées.
Kotlin Android: compilation réussie. Suite Android JVM complète:345 suites,1640 tests, zéro échec/erreur/ignoré, tâche relancée explicitement. Les sept tests du coordinateur sont INCLUS dans1640; les trois correctifs horloge/MAC sont INCLUS dans22.
Compilation des tests instrumentés et assemblage des deux APK: réussis. Vérification apksigner v2 et inspection manifestes: réussies. Paquet application com.sentinel.quantum.aivalidation, test .test, sept bibliothèques AArch64. Même certificat debug que le cycle9, aucun secret de signature livré.
Qwen réel exécuté sur l'hôte x86_64 avec le service corrigé: réponse2+2=4, actionNONE, verrou occupé libéré. Ce smoke n'est PAS une exécution Android.

## Préservation et limites
Diff code limité à huit chemins IA/tests par rapport à la base; PhoneCore, SMS/MMS, VPN, Wi-Fi/Bluetooth, Tracker, permissions, manifeste source et schémas Room inchangés.
Aucun appareil connecté au cloud, aucun nouveau test instrumenté exécuté. Les preuves physiques du cycle9 restent attachées à ses anciens APK. Il faut rejouer les parcours d'appariement, autorisation humaine, HMAC, refus/erreurs et annulation sur le NOUVEL APK avec le NOUVEAU service.
La barrière est limitée à ce processus Android; elle n'est pas une révocation distante du secret serveur. Les I/O de stockage déjà commencées peuvent finir avant l'acquittement d'une révocation. Aucun effacement rétroactif n'est revendiqué.
Le nettoyage garantit le processus direct du moteur: pas de qualification d'exécutables tiers qui créent leurs propres descendants. Les plafonds ne bornent pas la mémoire du modèle lui-même.
Restent: restauration conjointe des deux fichiers HMAC, transaction effet/journal, LiveKit réel, politique Play/contenus IA, signalement des réponses et marquage sensible du presse-papiers. Pas de production GO.
Aucune régression JavaScript ou voix/Redis relancée, ces composants n'ont pas changé. Budget0USD, aucune GitHub Action utilisée pour compiler ou tester.

Voir S24_VALIDATION.md pour les essais physiques à exécuter. S'arrêter aux erreurs, conserver les journaux, ne jamais désinstaller l'application habituelle ni effacer les données.

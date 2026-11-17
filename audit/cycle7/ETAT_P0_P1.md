# Cycle7 — barrière de révocation commune — NO-GO production

La révocation du cycle6 était limitée à l'Owner d'une seule Activity. La composition
Android possède désormais une barrière mémoire unique partagée par toutes les
instances de SentinelAiActivity dans le même processus. Elle complète l'autorité
existante et ne constitue ni une authentification ni une autorisation autonome.
Le moteur ne reçoit jamais cette barrière,un accord ou un port de décision.

L'accord opaque est capturé immédiatement lors du consentement utilisateur,
pas dans un worker retardé. Une requête en attente ne peut donc pas capturer
après révocation un accord neuf sur la base d'un ancien consentement. Les
vérifications de proposition,d'authentification et de permission refusent un
accord invalidé. Au point d'exécution réel,le point de contrôle est écrit sous
le verrou commun après une nouvelle vérification. Révocation et effet sont
ainsi sérialisés:la révocation attend un effet déjà commencé,puis invalide tous
les anciens accords avant de retourner. Elle ne défait pas un effet terminé.
Les appels réentrants sont refusés plutôt que produire un acquittement trompeur.

Le nouveau bouton révoque les autorisations IA des écrans du processus. Le statut
distingue demande et achèvement. Une nouvelle action nécessite un nouveau
consentement explicite après révocation. Un autre écran peut afficher encore
son ancien état jusqu'à sa prochaine interaction/lifecycle;son ancien accord
ne peut néanmoins plus exécuter un point de contrôle. Aucun broadcast Android
exporté,registre d'Activity persistant ou permission supplémentaire n'est ajouté.

L'événement PROCESS_REVOKED est tenté après invalidation. Le refus reste effectif
si le journal est plein,corrompu ou indisponible;le statut signale ce défaut.
La révocation et son écriture d'audit ne forment pas une transaction atomique.
La chaîne HMAC atteste l'ordre de persistance de métadonnées,elle ne reconstruit
pas toute la causalité des opérations simultanées. Des événements d'un nouveau
consentement peuvent s'intercaler avant l'enregistrement de la révocation.
Une ancienne version du lecteur ignorant PROCESS_REVOKED refuserait ce journal:
aucun downgrade transparent ni migration destructive n'est revendiqué.

## Exécutions de ce cycle

Sept nouveaux tests JVM:invalidation de plusieurs accords,accord neuf sans
réactivation des anciens,attente d'un effet en cours,requêtes concurrentes
retardées,exception pendant l'effet,révocation réentrante et effet imbriqué.
Rouge initial avant création de la classe,puis vert;rapport ciblé conservé.

Régression complète:343 suites,1630 tests JVM,0 échec,0 erreur,0 ignoré.
JavaScript avec PostgreSQL isolé:1328 réussis,0 échec/ignoré.
Trois contrôles statiques de politique réseau réussis;gardes manifeste/UI réussis.
Compilation Kotlin,compilation instrumentée,assemblage debug et assemblage des
tests instrumentés réussis. Les signatures APK sont vérifiées par apksigner.
Commandes exactes,empreintes et résultats dans validation.json.
Les101 fichiers protégés sont identiques à la base du cycle6;les permissions,
le manifeste,Room et les sources Phone Core/SMS/MMS/VPN/Wi-Fi/Bluetooth/Tracker
ne changent pas dans ce cycle. L'exception127.0.0.1 autorisée est conservée.

## Limites et vérifications encore obligatoires

P0:NO-GO maintenu. Aucun appareil ADB disponible;aucun test instrumenté exécuté.
Les tests prouvent le contrat JVM de la barrière,pas le parcours Android réel.
Il reste à tester deux instances Android,les callbacks de verrouillage tardifs,
la révocation pendant l'écriture,la rotation et la mort du processus sur S24+,
avec le modèle ARM64 réellement connecté et une vraie authentification humaine.

Cette révocation concerne les effets autorisés par la composition IA actuelle
dans ce processus. Elle ne révoque ni les clés d'appariement du service local,
ni des droits dans un autre processus. Les calculs/réseaux déjà lancés dans un
autre écran ne sont pas arrêtés instantanément:leur résultat est refusé après
invalidation et leur ancien accord ne peut plus autoriser un effet. Le service
local peut poursuivre sa génération bornée30s. Aucune révocation LiveKit ou
fermeture des accès média existants n'est prétendue par ce bouton.

P1:journal limité256événements,sans rotation/export UX;aucun historique n'est
effacé ou remplacé. Restauration conjointe journal+ancre toujours indétectable.
La révocation ne doit jamais dépendre de la disponibilité de cet historique.
La corrélation forensique reste limitée aux types d'événements et à leur uptime.
Le seul effet inscrit reste CREATE_AUDIT_CHECKPOINT;aucune action Phone Core
n'est ajoutée. Performances/thermiques ARM64,LiveKit et régressions physiques
restent non qualifiés. Budget0USD;aucune clé ou API cloud payante utilisée.

Les procédures S24+ du cycle6 restent applicables. Ajouter aux essais manuels:
ancien accord dans un second écran,révocation depuis le premier,refus de toute
ancienne proposition,même après retour du verrouillage,puis nouveau consentement
explicite. Une génération encore visible n'est pas une preuve d'autorisation
encore valide;vérifier l'absence d'effet et le journal. Une indisponibilité du
journal doit être signalée sans empêcher la révocation effective.

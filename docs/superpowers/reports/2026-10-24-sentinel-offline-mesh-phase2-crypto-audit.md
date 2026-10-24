# Sentinel Offline Mesh — audit cryptographique phase 2

Date : 2026-10-24  
Référence auditée : `d7215a9eb683180c21252df223b17a214c9b9f34`  
Branche de correction : `feature/sentinel-offline-mesh-foundation-20261024`

## Verdict technique

Le canal livré est une construction cryptographique propriétaire. Il ne doit pas
être appelé une implémentation Noise : aucun pattern Noise, état de handshake
Noise, message pattern, transcript hash ou preuve de conformité n'est présent.
La combinaison ECDH/ECDSA/HKDF/AES-GCM apporte des propriétés utiles, mais ne
constitue pas à elle seule une qualification Noise.

Le transport reste fail-closed pour les données privées tant que le handshake
authentifié n'est pas établi. Le statut global reste `LIMITED` et non `READY`.

## Garanties présentes après correction

- Identité : clé publique X.509 EC validée comme P-256; le point brut annoncé
  est dérivé de cette même clé.
- Authentification : la preuve ECDSA du challenge est vérifiée avec la clé
  annoncée; une signature produite par une autre clé est rejetée.
- Accord de clé : ECDH P-256 éphémère, avec clés de session renouvelées après
  `reset()` et après une reconnexion.
- Dérivation : HKDF-SHA-256 avec transcript et séparation des clés dans les
  deux directions.
- Confidentialité/intégrité : AES-GCM, AAD liée au sens et au numéro de
  séquence.
- Anti-rejeu : compteur de réception et fenêtre de numéros déjà vus; les
  compteurs sont recréés par session.
- Sessions obsolètes : génération de session dans les callbacks GATT et
  invalidation à l'interruption/déconnexion; délai de handshake fini.
- Résilience : les points EC malformés, les signatures DER incohérentes et les
  clés hors P-256 sont rejetés sans laisser remonter une exception de parsing.
- Concurrence : l'état de session et du canal sécurisé est sérialisé.
- Révocation : le constructeur d'identité accepte un fournisseur de confiance
  réévalué à chaque handshake; le retrait de la clé par le propriétaire est
  donc pris en compte à la session suivante.

## Garanties absentes ou dépendantes de l'intégration

- Il n'existe pas de magasin persistant de confiance/révocation dans ce
  périmètre. La persistance et la protection matérielle des identités restent
  à fournir par l'intégrateur Android.
- Il n'y a pas de protocole de renouvellement/révocation distribué : une
  révocation n'est effective qu'une fois la politique locale mise à jour.
- L'authenticité de la clé d'identité dépend du fournisseur de confiance
  appelant. Une liste de clés vide reste un refus par défaut, mais ne crée pas
  une chaîne de confiance.
- La protection MITM est présente uniquement si l'identité annoncée est
  comparée à une relation de confiance authentique hors bande. BLE, CCCD,
  connexion et succès de compilation ne sont jamais considérés comme une
  authentification.
- Aucune revue indépendante et aucune preuve de conformité Noise n'ont été
  réalisées.

## Tests de sécurité ajoutés

`OfflineMeshGattCryptoTest` couvre les clés non-P-256, la discordance entre
clé annoncée et signataire, les points malformés, le renouvellement de matériel
éphémère et la révocation au handshake suivant. `OfflineMeshGattSecureTransportTest`
couvre l'expiration déterministe du handshake. La régression complète du
24-10-2026 a exécuté 1 389 tests, dont 72 Offline Mesh, sans échec ni erreur.

## Risques résiduels

Cette construction ne doit pas être promue comme Noise sans remplacement ou
validation formelle par une bibliothèque/profil Noise éprouvé. Une qualification
physique à deux appareils est également nécessaire pour valider les chemins
radio, les limites MTU, les callbacks et les interruptions.

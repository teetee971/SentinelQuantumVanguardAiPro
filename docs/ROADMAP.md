# Roadmap — Sentinel Quantum Vanguard AI Pro

**Dernière mise à jour : 3 octobre 2026**

La source canonique des états produit est désormais `config/product-capabilities.json`. Le fichier généré `docs/PRODUCT_CAPABILITY_STATUS.md` donne la lecture humaine correspondante. Cette roadmap décrit l’ordre de finalisation ; elle ne duplique plus les statuts de déploiement ou de disponibilité qui vieillissaient sans preuve.

## Règle de vérité

Une capacité ne devient jamais « disponible client » parce que son code existe ou parce qu’une CI est verte. Selon la capacité, la chaîne de preuve exigée est :

`implémentation → configuration → déploiement → vérification runtime → validation physique → signature de release → disponibilité client`

Les étapes non applicables sont déclarées explicitement dans le registre canonique. Un bloqueur ouvert interdit la promotion `customer_available=true`.

## Priorité 1 — Phone Core Android

Le socle logiciel est présent : composeur Sentinel, InCall, filtrage `CallScreeningService`, Caller ID local, contacts, historique, SMS, MMS, notifications, multi-SIM et diagnostic séparé du Wi-Fi.

Le certificat Phone Core v5 comporte **exactement 14 preuves** définies par `PhoneCorePhysicalValidation.kt` et détaillées dans `PHONE_CORE_PHYSICAL_VALIDATION.md`. Les deux sondes provider et les douze observations opérationnelles doivent appartenir au périmètre d’installation courant.

Condition de sortie :

- 14/14 preuves physiques documentées sur l’APK exact ;
- scénarios complémentaires double-SIM, retrait/rétablissement des rôles et permissions, notifications et erreurs transport examinés ;
- aucun résultat Wi-Fi compté dans les 14 preuves ;
- APK/AAB public signé, checksum/provenance reliés au commit ;
- publication observée sur le canal choisi.

Tant que ces éléments ne sont pas réunis, `Phone Core 100 % fonctionnel` reste interdit comme statut.

## Priorité 2 — Release Android publique

Le build debug/installabilité et les validations CI ne remplacent pas une release publique signée. La clôture exige la custody de la clé de release, APK/AAB signés, empreintes vérifiées, SBOM/provenance, lien au commit et publication réellement observée.

Le pipeline doit conserver la distinction entre :

- artefact de test physique ;
- AAB de validation ;
- artefact public signé ;
- preuve de publication.

## Priorité 3 — Vérité OSINT et GeoIntel

Les vues OSINT doivent conserver trois propriétés : fraîcheur explicite, couverture des sources explicite et absence de date inventée. Une source manquante ne doit jamais être transformée en « aucun événement » ni une entrée sans date en événement récent.

GeoIntel reste séparé en deux niveaux :

- couche séismes USGS effectivement câblée dans le code ;
- couches conflits, points chauds, sanctions, météo et coupures non disponibles tant que leurs sources de production ne sont pas vérifiées et branchées.

Chaque nouvelle couche doit fournir provenance, fraîcheur, limites de taille/temps, comportement dégradé et tests de source avant promotion dans le registre produit.

## Priorité 4 — Collective Defense

Le backend Collective Defense et le centre Android sont deux capacités distinctes. Un backend observé en production ne constitue pas une preuve de release Android.

Pour le client Android, la sortie exige :

- APK/AAB du même commit que le client audité ;
- tests réels lookup/report/watch ;
- recheck WorkManager ;
- notification de hausse de risque réellement observée ;
- vérification du comportement hors réseau et des erreurs serveur ;
- release signée avant disponibilité client.

## Priorité 5 — Réputation téléphonique communautaire

La logique de vérification de paquet signé, modération, stockage PostgreSQL et rate limiting Redis ne doit pas être confondue avec un service opérationnel.

La sortie exige notamment : clés de signature de production et issuer mapping, custody/rotation/révocation, séquence monotone durable, scheduler, stockage/publication, identité authentifiée, modération durable, rate limiting partagé et synchronisation Android end-to-end.

## Priorité 6 — VPN Sentinel

Le client WireGuard Android n’est pas, à lui seul, un service VPN. La sortie exige :

- passerelle Sentinel réelle ;
- TLS/identité/provisionnement opérationnels ;
- autorité monotone de séquence pour la persistance anti-rollback ;
- tunnel réel validé ;
- DNS, IPv4, IPv6, MTU, coupure, reprise et révocation vérifiés ;
- aucune adresse/pays/IP de sortie affiché sans preuve runtime.

## Priorité 7 — Voice Studio / appels transformés

Le moteur DSP et le pipeline LiveKit/WebRTC côté client sont des prérequis, pas une preuve de service téléphonique transformé.

La sortie exige : serveur LiveKit/signaling, émetteur de jetons éphémères, passerelle VoIP/PSTN, appel réel de bout en bout, mesures de latence/écho, Bluetooth/haut-parleur/écouteur, mute/hold/reconnexion/interruption, Data Safety et entitlement commercial.

La transformation d’un appel SIM natif ne doit jamais être revendiquée : Android ne fournit pas à une application ordinaire un pipeline public de capture/transformation/réinjection de l’uplink opérateur.

## Priorité 8 — Internationalisation

La présence de quelques ressources `values-en` ne signifie pas que l’application est multilingue. Le sélecteur de langue global reste hors production tant que :

- les chaînes utilisateur Android sont externalisées ;
- les écrans critiques ont une couverture de traduction cohérente ;
- les erreurs backend et contenus juridiques ont une stratégie locale ;
- une gate détecte les nouvelles chaînes utilisateur codées en dur.

## Tests Android

Les tests unitaires, lint et smoke tests émulateur restent nécessaires mais ne couvrent pas tout. Une couche d’instrumentation `androidTest` doit compléter les scénarios UI/état qui peuvent être automatisés sans fabriquer les preuves physiques opérateur.

Les tests instrumentés ne doivent jamais remplir artificiellement les 14 critères de validation physique.

## Diagnostic Wi-Fi

Le Wi-Fi reste un diagnostic réseau indépendant du certificat Phone Core. La validation terrain doit couvrir fraîcheur, cache, permissions, localisation et throttling Android. Son résultat ne modifie jamais le compteur 14/14.

## Dossier de preuve obligatoire

Pour toute promotion d’une capacité :

- commit exact ;
- date et environnement ;
- workflow/artefact concernés ;
- résultat runtime ou physique attendu et observé ;
- écarts et nouvelle vérification ;
- mise à jour de `config/product-capabilities.json` ;
- régénération de `docs/PRODUCT_CAPABILITY_STATUS.md`.

Aucun jalon opérationnel n’est fermé par une modification documentaire seule.

Sentinel reste strictement séparé de **A KI PRI SA YÉ**.

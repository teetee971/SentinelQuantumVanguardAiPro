# Roadmap — Sentinel Quantum Vanguard AI Pro

**Dernière mise à jour : 4 septembre 2026**

Cette feuille de route distingue strictement ce qui existe dans le dépôt de ce qui reste à construire. Une case ou un statut planifié ne constitue pas une preuve d'implémentation ni de validation de production.

## État actuel

### Déjà présent

- Architecture défensive et gouvernance de sécurité dans le dépôt.
- Contrôle d'isolation automatique empêchant les dépendances opérationnelles non autorisées.
- Validation des workflows GitHub Actions et de leur épinglage.
- Contrôle automatique des claims de la surface publique.
- Validation des liens statiques du site.
- Contrôle des permissions et paramètres de sécurité du manifeste Android.
- Tests de gouvernance IA, validation des plans d'action et fuzzing de gouvernance.
- Application Android native sous `native-android-app/`.
- Surface web statique construite vers `frontend/dist`.
- Briques défensives et de veille déjà présentes dans le dépôt, sans extrapolation à des capacités non implémentées.

### Non démontré actuellement

- Aucun statut « production ready » global.
- Aucun antivirus/EDR/SOC de production démontré.
- Aucun APK signé officiellement distribué par le dépôt.
- Aucun taux de détection ou de disponibilité garanti.
- Aucune certification réglementaire obtenue n'est revendiquée.
- La réussite globale de CI reste à confirmer tant que les workflows critiques n'ont pas produit des exécutions complètes et vérifiables.
- Les nouveaux périmètres Social Intelligence, Investigations et Sovereign Defense décrits ci-dessous sont des objectifs d'architecture et de développement, pas des fonctionnalités déjà livrées.

## Priorité 0 — Geler et consolider l'architecture

Avant d'ajouter de grandes fonctionnalités :

1. Définir les frontières entre Sentinel Civil, Sentinel Professional et Sentinel Sovereign Defense — France.
2. Formaliser le modèle de confiance : identité, intégrité, provenance, autorisation, contexte et niveau de confiance.
3. Formaliser le modèle de données commun et le Threat Graph.
4. Formaliser le Privacy Firewall et les permissions inter-modules.
5. Définir les contrats API et les états `IMPLEMENTED`, `TESTED`, `PARTIAL`, `EXPERIMENTAL`, `PLATFORM-LIMITED`, `NOT IMPLEMENTED` et `UNKNOWN`.
6. Maintenir la séparation stricte entre Sentinel et A KI PRI SA YÉ.

**Critère de sortie :** architecture et contrats documentés avant implémentation des nouveaux modules.

## Priorité 1 — Restaurer une validation CI réellement exécutable

1. Diagnostiquer le problème des runners GitHub Actions.
2. Obtenir au moins une exécution complète de chaque workflow critique.
3. Corriger les erreurs révélées par ces exécutions, sans affaiblir les contrôles.
4. Conserver les preuves de run et les artefacts nécessaires à la traçabilité.

**Critère de sortie :** résultats CI observés, reproductibles et associés au commit contrôlé.

## Priorité 2 — Qualité, sécurité et Trust Layer

1. Étendre le fuzzing du `decision-plane` aux structures imbriquées et aux limites de taille.
2. Ajouter des tests de propriétés pour les règles d'autorisation, d'anti-rejeu et de rollback.
3. Vérifier systématiquement les entrées non fiables aux frontières JavaScript/Kotlin.
4. Ajouter des tests de régression pour chaque vulnérabilité corrigée.
5. Mesurer les limites mémoire/temps des parseurs et validateurs.
6. Introduire un modèle commun de provenance et de niveau de confiance pour les alertes et décisions.
7. Ajouter des contrôles anti-faux-positifs et la distinction explicite entre « inconnu » et « malveillant ».

**Critère de sortie :** chemins critiques testés et résultats réellement exécutés et conservés.

## Priorité 3 — Protection téléphonique et mobile

1. Stabiliser l'application Android et ses contrôles de sécurité.
2. Vérifier l'APK produit et son manifeste final.
3. Ajouter une analyse des dépendances Gradle et de leurs versions.
4. Ajouter des tests unitaires sur les composants de sécurité locaux.
5. Implémenter, lorsque les APIs de plateforme le permettent, le caller ID et le filtrage défensif des appels avec une base locale et une latence maîtrisée.
6. Étendre la protection SMS contre le spam, le phishing et les fraudes.
7. Consolider la protection SIM-swap déjà amorcée.
8. Concevoir le Device Trust et le Lost Device Mode : révocation de sessions, révocation de clés et effacement cryptographique des données Sentinel, sans effacement arbitraire du téléphone.
9. Préparer une release uniquement après compilation réelle, signature, checksum et conservation de l'artefact.

## Priorité 4 — Email Security et Digital Exposure

1. Construire l'analyseur d'en-têtes et de chaîne de réception.
2. Vérifier SPF, DKIM et DMARC lorsqu'ils sont observables.
3. Analyser domaines, liens, infrastructures et réputation avec des sources autorisées.
4. Ajouter la détection BEC, usurpation et phishing.
5. Construire un module Digital Exposure séparant exposition connue, compromission probable et absence de résultat.
6. Utiliser uniquement des sources et APIs autorisées ; ne pas accéder à des espaces clandestins ou à des données obtenues illicitement.

## Priorité 5 — Social Intelligence

Créer un module de veille et d'analyse des réseaux sociaux sur données publiques ou légalement accessibles : tendances et signaux faibles, propagation de contenus, réseaux de comptes, comportements coordonnés, réutilisation de contenus, domaines et infrastructures associés, signaux d'automatisation, usurpation et faux sites, médias synthétiques comme indicateur et non comme preuve absolue, chronologie des campagnes et corrélation avec les autres sources de Sentinel.

**Règle :** observation → corrélation → hypothèse → caractérisation → attribution avec niveau de confiance. Aucune attribution automatique d'un individu ou d'un État.

## Priorité 6 — Foreign Interference Defense

Construire un module inspiré méthodologiquement des pratiques publiques françaises de lutte contre les manipulations de l'information, sans copier les outils ou procédures d'un service public.

Fonctions prévues : OSINT, analyse des modes opératoires informationnels, infrastructure correlation, Social Campaign Graph, analyse de coordination, détection précoce, corrélation multi-source, attribution avec niveaux de confiance, Evidence Vault et rapports reproductibles.

Le périmètre doit rester défensif et respecter les sources accessibles légalement.

## Priorité 7 — Sentinel Investigations

Mode destiné aux journalistes, chercheurs, ONG, fact-checkers et analystes autorisés : Investigation Workspace, timeline, graphe d'enquête, conservation des sources, hash et provenance, comparaison de versions, export de rapports et séparation stricte entre faits observés, corrélations, hypothèses et conclusions.

L'outil doit aider à vérifier et documenter une enquête, pas produire automatiquement une accusation.

## Priorité 8 — Threat Graph et Campaign Intelligence

Unifier les objets suivants dans un modèle commun :

`numéro · email · domaine · IP · compte · appareil · réseau social · infrastructure · événement · campagne · indicateur`.

Le graphe doit permettre la corrélation inter-canaux tout en respectant les permissions et la minimisation des données.

## Priorité 9 — Sovereign Defense — France

Créer un périmètre technique séparé destiné aux organismes publics légalement habilités.

Fondations prévues : identité et authentification forte, gestion des habilitations, mission et finalité, périmètre de données, autorisation vérifiable, politiques d'accès, journal d'audit, chaîne de preuve, séparation cryptographique, révocation et kill switch, supervision humaine, conformité et traçabilité.

Les capacités sensibles restent soumises aux autorisations et cadres juridiques applicables. Elles ne doivent jamais être exposées à l'édition civile par un simple changement de rôle ou de paramètre.

## Priorité 10 — Research / Red Team Lab

Maintenir un environnement totalement séparé de la production pour : fuzzing, tests adversariaux, sécurité des modèles IA, simulation d'incidents, analyse de logiciels malveillants dans des environnements contrôlés, tests de résilience et tests de récupération.

Aucune capacité expérimentale ne doit être considérée comme une preuve d'efficacité en production.

## Priorité 11 — Privacy, chiffrement et résilience

1. Local-first lorsque cela est techniquement possible.
2. Minimisation des données.
3. Chiffrement au repos et en transit.
4. Séparation des clés et des données.
5. Identités par appareil et révocation.
6. Protection des sauvegardes.
7. Rotation des clés.
8. Protection contre l'extraction hors ligne dans les limites réelles de la plateforme.
9. Mode hors ligne testé.
10. Récupération après compromission.

Aucune garantie absolue ne doit être revendiquée contre un système d'exploitation ou un appareil entièrement compromis.

## Priorité 12 — Supply chain, SBOM et releases

1. Maintenir les Actions épinglées par SHA.
2. Maintenir `npm ci` et le lockfile comme sources de vérité.
3. Mettre à jour les dépendances uniquement via une régénération réelle du lockfile et une validation complète.
4. Produire un SBOM lors des releases lorsque la chaîne de build est stabilisée.
5. Vérifier les artefacts avant publication.
6. Ajouter provenance et checksum aux releases.
7. Surveiller les dépendances Android, JavaScript et modèles IA.

## Priorité 13 — Legal, CGU et conformité

Le cadre juridique et contractuel fait partie du produit et doit rester aligné avec les capacités réellement disponibles.

À construire et maintenir :

1. CGU / Conditions générales d'utilisation, avec version, date d'entrée en vigueur et historique des modifications.
2. Politique de confidentialité et règles de traitement des données.
3. Politique de conservation, suppression et export des données.
4. Politique cookies lorsque des cookies ou traceurs non essentiels sont effectivement utilisés.
5. Conditions spécifiques aux usages professionnels.
6. Conditions spécifiques au périmètre Sovereign Defense — France.
7. Politique d'utilisation acceptable et limites d'usage.
8. Politique de signalement des vulnérabilités / divulgation responsable.
9. Clauses spécifiques aux OSINT, investigations, preuves et conservation des sources.
10. Clauses encadrant les capacités de sécurité, de red team et de recherche.
11. Clauses encadrant les fonctions IA : assistance, incertitude, absence d'autorité automatique et limites des décisions.
12. Séparation juridique et technique des périmètres Civil / Professional / Sovereign.
13. Vérification des mentions publiques contre les capacités réellement implémentées.

### Legal / Compliance Gate

Toute modification touchant les permissions, la collecte ou le partage de données, la conservation, le réseau, les capacités sensibles, l'IA, les paiements ou les conditions d'utilisation doit déclencher une vérification de conformité avant release.

**Critère de sortie :** les documents juridiques applicables sont versionnés, cohérents avec le produit réel, relus selon le périmètre concerné et contrôlés avant publication. Aucune formulation juridique ne doit être présentée comme un avis juridique professionnel.

## Priorité 14 — Surface web

1. Maintenir une seule source de vérité pour les pages publiques.
2. Supprimer les doublons ou façades non utilisées après vérification.
3. Étendre les contrôles de liens aux références JavaScript et CSS détectables statiquement.
4. Maintenir le contrôle automatique des affirmations non démontrées.
5. Vérifier le build généré plutôt que seulement les sources.
6. Tester le rendu mobile sur plusieurs tailles d'écran avant publication.

## Priorité 15 — Internationalisation

Construire un `Global Core` complété par des `Country/Territory Intelligence Packs` : numérotation et préfixes, langues, règles locales, opérateurs, sources de menace, typologies de fraude, contraintes réglementaires et disponibilité réelle des données.

La couverture fonctionnelle doit être déclarée pays par pays et ne doit jamais être présentée comme universelle sans preuve.

## Évolutions volontairement non prioritaires

Ne pas ajouter avant stabilisation du socle : marketplace, réseau communautaire de menaces non gouverné, blockchain, extension navigateur, versions desktop/iOS non justifiées par l'architecture actuelle, partage automatique de données, backend centralisé non spécifié et orchestration autonome non vérifiée.

Ces éléments peuvent être réévalués après validation des fondations.

## Principes permanents

- Ne jamais transformer une simulation en preuve d'efficacité réelle.
- Ne jamais présenter un composant non testé comme opérationnel.
- Ne jamais inventer de télémétrie, métrique ou incident.
- Ne jamais contourner un contrôle CI pour obtenir un résultat vert.
- Maintenir Sentinel totalement séparé des autres projets.
- Préférer une fonctionnalité plus limitée mais démontrable à une fonctionnalité plus ambitieuse non vérifiée.
- L'IA ne constitue pas à elle seule une autorité d'exécution.
- Toute attribution de campagne ou d'acteur doit conserver ses preuves et son niveau de confiance.
- Les fonctions sensibles doivent être contrôlables, vérifiables, traçables et réversibles.
- Les documents juridiques doivent rester synchronisés avec les capacités effectivement livrées.

## Critère de maturité

Une fonctionnalité est considérée comme validée uniquement lorsque :

`code/configuration → test ciblé → exécution observée → résultat conservé → documentation alignée → conformité applicable vérifiée`.

**Prochaine révision recommandée : après la prochaine série d'exécutions CI complètes et l'audit du socle avant implémentation des nouveaux modules.**

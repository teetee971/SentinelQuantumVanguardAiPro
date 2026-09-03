# Défense contre l'ingérence numérique

## Objectif

Sentinel peut évoluer vers une capacité défensive d'analyse de campagnes d'influence, de manipulation informationnelle et de signaux coordonnés à partir de données publiques autorisées.

Cette capacité ne doit pas devenir un outil d'influence ou d'opération offensive.

## Modèle de traitement

`SOURCE PUBLIQUE → UNTRUSTED → NORMALISATION → CORRÉLATION → ÉVIDENCE → ANALYSE → SECURITY CASE → VALIDATION HUMAINE`

## Capacités prévues

### Provenance

Pour chaque observation : source, identifiant, horodatage de collecte, date de publication lorsqu'elle est disponible, empreinte du contenu lorsque cela est approprié et niveau de confiance.

### Signaux coordonnés

Identifier des motifs observables tels que synchronisation temporelle, répétition anormale, réutilisation de contenus ou relations entre sources. Un signal n'est pas une preuve d'intention.

### Analyse de manipulation

Repérer incohérences, changements de contexte, usurpations apparentes, contenus recyclés et contradictions entre sources. Les conclusions doivent rester proportionnées aux preuves.

### Corrélation cyber-informationnelle

Mettre en relation, lorsqu'une preuve existe, des indicateurs cyber publics et des événements informationnels publics : domaines, certificats, vulnérabilités, campagnes de phishing, publications ou changements d'infrastructure.

### Chronologie et dossiers

Construire une timeline vérifiable des observations. Une campagne suspecte peut devenir un `Security Case` contenant observations, preuves, hypothèses, niveau de confiance, décisions, corrections et revalidation.

## Niveaux de confiance

- `UNTRUSTED` : donnée externe non vérifiée.
- `NORMALIZED` : donnée nettoyée, structurée et validée syntaxiquement.
- `TRUSTED` : donnée suffisamment corroborée pour être utilisée comme élément de preuve selon des règles explicites.

`HTTPS` ne suffit jamais à transformer une source en preuve fiable.

## Garde-fous

Sentinel ne doit pas créer ou contrôler de faux comptes pour influencer une audience, amplifier artificiellement un message, cibler ou harceler des personnes, infiltrer des comptes ou systèmes, conduire une campagne de manipulation, ni attribuer automatiquement une opération à une personne ou organisation sans preuve suffisante.

Les données synthétiques ou de démonstration ne doivent jamais être présentées comme des événements réels.

## Principe de non-hallucination

Toute conclusion doit pouvoir être ramenée à :

`INPUT → OBSERVATIONS → RÈGLES → CORRÉLATIONS → PREUVES → CONCLUSION → LIMITATIONS`

Si une information n'est pas observée ou suffisamment étayée, son état reste `UNKNOWN` ou `NOT_OBSERVED`.

## Statut

Cette capacité constitue une orientation d'architecture et de développement. Ce document ne constitue pas la preuve qu'une fonction opérationnelle d'analyse d'ingérence est déjà implémentée ou validée.

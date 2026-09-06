# Méthodologie d'analyse géopolitique cyber

**Classification : public**  
**État documentaire : septembre 2026**

## Objet

Le module géopolitique de Sentinel sert à contextualiser des événements cyber à partir de sources ouvertes. Il s'agit d'un outil d'aide à l'analyse défensive, pas d'un système d'attribution, de renseignement classifié ou d'action offensive.

> **Usage défensif — aucune action non autorisée — veille, analyse, audit, formation et évaluation uniquement.**

## Sources

Les données doivent provenir de sources publiques et traçables : publications institutionnelles, CERT/CSIRT, organismes internationaux, médias reconnus, travaux académiques et rapports de threat intelligence rendus publics.

Une source publique peut être incomplète, retardée ou erronée. Sentinel ne doit pas inventer de donnée de remplacement lorsqu'une source n'est pas disponible.

Sont hors périmètre : renseignement classifié, interception non autorisée, données volées, exfiltration et collecte illicite de données personnelles.

## Méthode

L'analyse peut relier un événement géopolitique documenté à des observations cyber publiques en conservant au minimum :

- la date et la source de l'événement ;
- la région ou le périmètre concerné ;
- les observations cyber utilisées ;
- le niveau de confiance ;
- les limites et hypothèses de l'analyse.

La corrélation temporelle ou thématique ne constitue pas une preuve de causalité. Elle ne permet pas non plus, à elle seule, d'attribuer une opération à un acteur, un État ou une organisation.

## Scoring

Tout score produit par Sentinel est un indicateur interne d'aide à la priorisation. Il ne doit pas être présenté comme une probabilité scientifique, une prédiction certaine ou une mesure officielle sans validation spécifique de la méthode et des données utilisées.

Les pondérations, seuils et transformations appliqués doivent rester documentés dans le code ou la configuration correspondante afin que le résultat puisse être reproduit et audité.

## Attribution et prédiction

Sentinel ne doit pas présenter comme fait :

- l'auteur d'une cyberattaque sur la seule base d'une corrélation OSINT ;
- une intention politique non étayée ;
- une attaque future comme certaine ;
- une relation causale déduite uniquement d'une proximité temporelle.

Les sorties doivent distinguer clairement observation, hypothèse, corrélation et conclusion validée par un analyste.

## Cas d'usage

Le module peut contribuer à :

- enrichir une veille CERT/CSIRT ;
- contextualiser des alertes de threat intelligence ;
- préparer des exercices de crise ;
- produire des synthèses pour analystes, RSSI ou équipes SOC ;
- comparer des tendances observées dans des sources publiques.

Ces usages restent des aides à l'analyse. Les décisions opérationnelles et stratégiques demeurent sous responsabilité humaine et doivent être confrontées aux informations réellement disponibles dans l'environnement concerné.

## Traçabilité

Chaque analyse importante devrait pouvoir être reliée aux sources et paramètres qui l'ont produite. Une représentation minimale peut contenir :

```json
{
  "event": "événement documenté",
  "observed_at": "2026-09-01T12:00:00Z",
  "sources": ["source-publique-1", "source-publique-2"],
  "confidence": "medium",
  "limitations": ["corrélation non causale"]
}
```

L'exemple ci-dessus décrit une structure de traçabilité ; il ne constitue pas une preuve qu'un événement réel a été observé.

## Référentiels externes

Les publications de l'ANSSI, du CERT-FR, de l'ENISA, de MITRE ATT&CK ou d'autres organismes peuvent servir de références méthodologiques lorsqu'elles sont pertinentes. Leur utilisation ne signifie pas que Sentinel est certifié, homologué, approuvé ou conforme par ces organismes.

Toute revendication de conformité réglementaire ou de certification exige une évaluation formelle distincte.

## Limites

Les principales limites sont :

- biais et couverture inégale des sources ouvertes ;
- sous-déclaration des incidents ;
- décalage entre occurrence et publication ;
- désinformation et information non vérifiée ;
- simplification d'environnements géopolitiques complexes ;
- absence de renseignement non public.

Les résultats doivent donc conserver leur contexte, leur niveau de confiance et leurs limites au lieu d'être convertis en affirmations catégoriques.

## Conclusion

Le Geopolitics Engine est un composant défensif de contextualisation OSINT. Sa valeur dépend de la qualité des sources, de la traçabilité et de la distinction stricte entre faits observés, corrélations, hypothèses et décisions humaines.

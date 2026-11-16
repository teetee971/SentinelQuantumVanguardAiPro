# Intégration défensive de DISARM-FR

## Décision

Le dépôt public `VIGINUM-FR/DISARM-FR` est pertinent pour normaliser en français les tactiques et techniques de manipulation de l’information. Il complète le futur module Foreign Interference Defense ; il ne remplace pas les flux de vulnérabilités ou de cybermenaces techniques.

## Usage autorisé

- importer uniquement des versions identifiées et révisées ;
- conserver l’identifiant DISARM, la version, la source et la date d’import ;
- afficher l’attribution requise par la licence CC BY 4.0 ;
- relier une observation à une technique avec preuve, analyste et niveau de confiance ;
- séparer strictement faits, corrélations, hypothèses et conclusions.

## Interdictions de conception

- ne pas transformer une technique DISARM en IoC ;
- ne pas déduire automatiquement un acteur, un pays ou une intention ;
- ne pas présenter la taxonomie comme un flux temps réel ;
- ne pas déclencher d’action offensive ou de remédiation autonome ;
- ne pas mélanger les données DISARM-FR avec les données personnelles de la protection téléphonique.

## Modèle minimal envisagé

Chaque liaison entre une observation et DISARM-FR devra contenir : `techniqueId`, `taxonomyVersion`, `sourceUrl`, `observedAt`, `evidenceRefs`, `confidence`, `analystStatus` et `licenseAttribution`.

L’import de production reste conditionné à une revue de licence, à des tests de schéma et à une mise à jour review-gated.

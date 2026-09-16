# Sentinel Investigations — première tranche locale

État du 16 septembre 2026. Implémentation originale Sentinel, sans copie de code,
de bases de sites ou d’identité visuelle des projets étudiés.

## Livré dans cette tranche

`public/investigations.html` : carnet expérimental en français, entités typées,
relations dirigées, graphe SVG et listes accessibles, chronologie UTC, suppression
d’une entité avec ses relations, import/export JSON Sentinel v1. Accessible depuis
la veille et la roadmap. Aucune requête de collecte, aucun LLM, aucune persistance
automatique : fermeture ou recharge perd le dossier sans export. Les fichiers
exportés sont en clair, pas chiffrés ni signés. Aucune promesse de coffre de preuves.

Chaque entité/relation exige une URL HTTPS, une date et une description. La source
reste **non vérifiée**, y compris si un import prétend le contraire. Les URL saisies
sont affichées comme texte, sans ouverture ou téléchargement automatique. Une
observation déclarée n’est pas un fait authentifié. Aucune fusion d’homonymes ni
notation de dangerosité. Les domaines/IP/téléphones sont des libellés, pas une
validation de propriété, de routabilité ou d’identité.

Bornes : 60 entités, 120 relations, 256 Kio par fichier ; remplacement atomique
après validation ; échec d’import conserve le dossier ; import tardif écarté après
une modification. Les champs inconnus sont supprimés. Les déclarations importées
ne sont pas des instructions exécutables. Le graphe peut se chevaucher à forte
densité : les listes font référence, ce n’est pas encore un moteur de grand graphe.

Tests : `npm run test:investigations` couvre le contrat et les vrais gestionnaires
d’événements avec un DOM simulé. Ce n’est pas une validation visuelle sur appareil.

## Références examinées et décisions

| Référence | Idée retenue | Frontière d’intégration |
| --- | --- | --- |
| [Flowsint](https://github.com/reconurge/flowsint) | Graphe typé, enrichissements explicites et workflows | Apache-2.0 dans la version examinée ; conserver licence/NOTICE si du code est repris. Ne pas embarquer sa pile PostgreSQL/Neo4j/Celery dans le petit service Render gratuit. Flowsint propose déjà un enrichisseur Maigret. |
| [Maigret](https://github.com/soxoj/maigret) | Connecteur futur de résultats de pseudonymes | MIT ; conserver attribution pour toute réutilisation. Un compte trouvé n’établit pas l’identité. Pas de recherche récursive par défaut, de contournement CAPTCHA ou de collecte privée. JSON Maigret brut non pris en charge dans cette tranche. |
| [World Monitor](https://github.com/koala73/worldmonitor) | Veille en panneaux, carte multicouche et fraîcheur des sources | Application AGPL-3.0-only ; certains clients SDK seulement sont MIT. API hébergée et données sous conditions distinctes. Pas de code applicatif copié dans Sentinel ; toute reprise exige une décision explicite sur les obligations. |
| [ADS-B Exchange](https://globe.adsbexchange.com/) | Contexte aérien agrégé optionnel pour analystes | Lien externe uniquement. Pas de scraping de la carte. L’API destinée à une entreprise requiert une licence commerciale, même sans revenus ; incompatible avec une promesse de flux gratuit illimité. |

Sources de conditions : [Flowsint NOTICE](https://github.com/reconurge/flowsint/blob/main/NOTICE),
[Maigret MIT](https://github.com/soxoj/maigret/blob/main/LICENSE),
[World Monitor licences](https://github.com/koala73/worldmonitor/blob/main/docs/license.mdx),
[ADS-B usage entreprise](https://support.adsbexchange.com/hc/en-us/articles/37363886073613-I-m-building-a-project-for-my-company-but-we-aren-t-making-money-off-of-it-can-I-get-a-free-API-key).
Vérification documentaire, pas avis juridique ; revalider aux versions retenues.

## Suite proposée, non implémentée

1. Adaptateurs versionnés vers les contrats OSINT existants : conserver capture,
   auteur de la saisie, provenance, hash, droits et dates de collecte/publication.
   Ne pas transformer une entrée manuelle en fait authentifié. Jeux de tests de
   faux positifs/homonymes, erreurs HTTP distinctes des absences de comptes.
2. Enrichissements opt-in d’actifs autorisés : prévisualisation du périmètre,
   allowlist de fournisseurs, budgets réseau, annulation, isolation des secrets,
   anti-SSRF et contrôle des redirections/DNS, aucun accès automatique aux URL
   importées. Ne pas lancer des milliers de recherches depuis Render Free.
3. Chronologie filtrable et carte de contexte agrégé : licence par source,
   fraîcheur/indisponibilité explicites, pas de géolocalisation de personnes ni
   d’identité de passager déduite d’un vol. Pas de corrélation géopolitique présentée
   comme preuve d’intrusion ou de fraude téléphonique.
4. Workflows reproductibles avec aperçu avant exécution, checkpoints et revue
   humaine ; branchement aux contrôles de gouvernance existants. Les futurs
   résumés IA devront citer les observations et conserver les contradictions.
5. Offre B2B séparée : collaboration avec droits fins, coffre chiffré, audit signé,
   connecteurs sous licence, exports professionnels. Paiement non activé ; le
   socle téléphone reste gratuit.

Critères pour dire « amélioré » : taux de faux rapprochements mesuré sur un corpus
annoté, traçabilité de chaque relation, rejets de sources périmées, absence de fuite
de dossier, import/export déterministe, tests clavier/mobile et budgets de temps
et mémoire. Aucun résultat de benchmark comparatif n’est revendiqué aujourd’hui.

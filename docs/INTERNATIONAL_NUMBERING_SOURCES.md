# Sources officielles de numérotation internationale

Statut : index/pipelines intégrés pour la France, l’Autriche, le Royaume-Uni, les Pays-Bas et la Tchéquie ; catalogue de sources officielles vérifié pour l'expansion internationale. Ce document ne constitue pas un annuaire mondial des opérateurs.

## Règle de preuve

Une source de numérotation peut décrire un plan national, l'attribution initiale d'un bloc, un code opérateur ou la disponibilité d'une ressource. Elle ne prouve pas :

- l'identité de la personne qui appelle ;
- l'opérateur qui dessert actuellement le numéro après portabilité ;
- la légitimité de l'appel ;
- l'absence de spoofing ;
- qu'un bloc attribué est effectivement actif.

Sentinel doit donc conserver séparément la provenance, la date de publication, la date d'attribution, le statut de la ressource et le niveau de preuve.

## Index déjà intégrés

| Pays | Autorité | Source | État Sentinel |
|---|---|---|---|
| France | ARCEP | MAJNUM + identifiants opérateurs | index local automatisé ; publication autonome après validation CI |
| Autriche | RTR | Open Data de numérotation | index local importé ; mode automatique ajouté, qualification de téléchargement en production encore à vérifier |
| Royaume-Uni | Ofcom | S1/S3/S5/S7/S8/S9 | importeur hebdomadaire fail-closed intégré ; publication autonome après validation CI |
| Pays-Bas | ACM | registre public des numéros | importeur hebdomadaire fail-closed intégré ; publication autonome après validation CI |
| Tchéquie | ČTÚ | numéros et codes attribués | importeur quotidien fail-closed intégré ; publication autonome après validation CI |

## Priorité A — données officielles structurées directement exploitables

| Pays | Autorité / administrateur | Données officielles utiles | Format / cadence observée | Cible Sentinel |
|---|---|---|---|---|
| Royaume-Uni | Ofcom | numéros disponibles/alloués, blocs, codes de portabilité, CUPID, MNC, plages protégées | CSV/XLSX/ZIP ; publication annoncée chaque mercredi | import automatique hebdomadaire intégré |
| Belgique | IBPT / BIPT | base des numéros réservés et attribués par bloc ; base C00XX ; séries annulées | XLSX ; publication récente 30/09/2026 | LEGAL_REVIEW_REQUIRED : réutilisation publique encouragée par l’IBPT, mais conditions spécifiques du dataset à rattacher explicitement avant snapshot |
| Pays-Bas | ACM | registre public complet des numéros et titulaires | ZIP d'un CSV, CC0 1.0 ; fichier sans date de publication intrinsèque | import automatique hebdomadaire intégré |
| Tchéquie | ČTÚ | numéros et codes attribués | CSV/XLSX Open Data + schéma CSVW ; périodicité quotidienne | import automatique quotidien intégré |
| Finlande | Traficom | plages fixes, indicatifs mobiles, numéros de service, codes opérateurs et MNC | API OData v4 + tables ; open data sous CC BY 4.0 avec attribution | REDISTRIBUTION_ALLOWED ; import automatique après découverte déterministe des entity sets |
| États-Unis et territoires | NANPA | affectations NPA-NXX, milliers de blocs, société/OCN, rate center, statut | ZIP texte/CSV/XLSX ; plusieurs rapports quotidiens ou temps réel | import techniquement faisable ; redistribution publique à bloquer jusqu’à validation explicite des droits |
| Canada | CNA / CNAC | statut des CO codes NPA-NXX, société/OCN, zone, statut | CSV par NPA + archive CSV globale, publication nuits ouvrées | import techniquement faisable ; redistribution publique à bloquer jusqu’à validation explicite des droits |
| Nouvelle-Zélande | NAD | registre des Code Blocks, attributaire, statut, date, catégorie et zone | téléchargements CSV par plage ; complétude à agréger et valider | LEGAL_REVIEW_REQUIRED avant snapshot public ; import technique possible après agrégation déterministe |

## Priorité B — source officielle exploitable, format à qualifier avant automatisation

| Pays | Autorité | Données | État de qualification |
|---|---|---|---|
| Allemagne | Bundesnetzagentur | répertoire des blocs géographiques attribués + titulaires | ZIP officiel mis à jour selon besoin ; schéma interne à figer avant parser |
| Suisse | OFCOM / BAKOM | blocs et indicatifs E.164 disponibles/attribués | listes officielles ; format d'export à valider |
| Espagne | CNMC | registre géographique/mobile, assignations et sous-assignations ; portabilité explicitement hors dataset | ZIP global quotidien `bd-num.zip` ; fichiers texte structurés ; REDISTRIBUTION_ALLOWED sous CC BY-SA 4.0 avec attribution CNMC ; importeur en préparation |
| Pologne | UKE | tables de numérotation attribuée PSTN, PLMN, M2M, MNC, services | tableaux publics ; mécanisme d'export à confirmer |
| Danemark | Digitaliseringsstyrelsen | Nummerregistret : ressources attribuées, disponibles et réservées, titulaires | registre public ; interface/export à qualifier |
| Norvège | Nkom | plan E.164 complet : plage, fournisseur, statut, quantité, catégorie, point code | CSV direct ; schéma réel vérifié ; licence NLOD 2.0 ; REDISTRIBUTION_ALLOWED ; importeur fail-closed en préparation |
| Irlande | ComReg | assignments & availability, SMS, 1800/0818, DNO | outils de recherche officiels ; bulk public à confirmer |
| Portugal | ANACOM | gammes du PNN et décisions d'attribution/révocation | données officielles surtout décisionnelles ; bulk à confirmer |
| Italie | MIMIT | ressources de numérotation attribuées | XLSX officiel, fichier attribué daté du 15/09/2026 lors de la vérification ; LEGAL_REVIEW_REQUIRED : le MIMIT utilise IODL 2.0 pour ses Open Data, mais la couverture explicite de ce fichier de numérotation reste à démontrer |
| Grèce | EETT | ressources primaires attribuées + recherche de l'opérateur courant pour mobile | registre officiel ; bulk/export à qualifier ; la recherche opérateur courant tient explicitement compte de la portabilité |
| Roumanie | ANCOM | licences de ressources de numérotation, opérateur, domaine et statut | registre public consultable ; import après qualification du mécanisme bulk/export |

## Priorité C — autorité équivalente identifiée, bulk d'attribution non encore confirmé

| Pays | Autorité | Observation |
|---|---|---|
| Australie | ACMA | gère le plan national et publie plusieurs registres de codes ; couverture bulk des numéros téléphoniques à confirmer |
| Singapour | IMDA | gère le National Numbering Plan et NORS ; accès public bulk d'attributions à confirmer |
| Brésil | ANATEL | gère les ressources de numérotation via nSAPN ; disponibilité d'un export public de blocs à confirmer |
| Inde | Department of Telecommunications / TRAI | NRMS et décisions d'allocation ; plusieurs listes/circulaires existent mais pas encore de source bulk normalisée retenue |
| Slovaquie | Úrad pre reguláciu elektronických komunikácií a poštových služieb | listes officielles de numéros attribués ; format et licence à qualifier |
| Mexique | IFT (références historiques ; autorité successeure à vérifier) | continuité du registre et source bulk générale à requalifier avant intégration |
| Émirats arabes unis | TDRA | gestion et allocation nationale des ressources de numérotation | portail d'allocation surtout réservé aux opérateurs ; bulk public non identifié |
| Arabie saoudite | CST | plan national, zones et codes publics alloués | tables publiques partielles ; pas encore de registre bulk d'attribution retenu |
| Suède | PTS — Post- och telestyrelsen | autorité des communications électroniques et de la poste ; registre de blocs, export et conditions de réutilisation à qualifier |
| Maroc | ANRT — Agence nationale de réglementation des télécommunications | autorité télécom ; source publique des attributions, format, cadence et droits à qualifier |
| Algérie | ARPCE — Autorité de régulation de la poste et des communications électroniques | autorité télécom et poste ; source publique des attributions et droits à qualifier |
| Sénégal | ARTP — Autorité de régulation des télécommunications et des postes | autorité télécom et poste ; source publique des attributions et droits à qualifier |
| Côte d’Ivoire | ARTCI — Autorité de régulation des télécommunications/TIC | autorité télécom/TIC ; source publique des attributions et droits à qualifier |
| Cameroun | ART — Agence de régulation des télécommunications | autorité télécom ; source publique des attributions et droits à qualifier |
| Afrique du Sud | ICASA — Independent Communications Authority of South Africa | autorité télécom, audiovisuel et poste ; source publique des attributions et droits à qualifier |

## Sources officielles — références du catalogue

Les références précédentes portent la vérification déclarée du 1er octobre 2026. Les nouvelles autorités ci-dessous sont des pistes à qualifier : leur présence ne certifie ni la disponibilité actuelle du site, ni un export, ni une licence. Le rapport automatisé vérifie uniquement l’accès HTTP et les changements d’octets ; il ne renouvelle pas une vérification documentaire ou juridique.

- Ofcom numbering data: https://www.ofcom.org.uk/phones-and-broadband/phone-numbers/numbering-data
- BIPT reserved/allocated numbers: https://www.bipt.be/operators/publication/database-with-reserved-and-allocated-numbers
- ACM public number register: https://www.acm.nl/nl/telefoonnummers-zoeken
- ACM dataset catalogue: https://data.overheid.nl/dataset/register-van-toegekende-telefoonnummers
- ČTÚ allocated numbers and codes: https://data.ctu.gov.cz/dataset/pridelena-cisla-kody
- ČTÚ machine-readable schema: https://ctu.gov.cz/schemas/pridelena_cisla_a_kody.json
- Traficom open data: https://tieto.traficom.fi/en/open-data
- Traficom licence (CC BY 4.0): https://static.traficom.fi/en/transport-system/geoinformationsmaterial/use-and-licences-data
- Bundesnetzagentur assigned geographic blocks: https://www.bundesnetzagentur.de/DE/Fachthemen/Telekommunikation/Nummerierung/ONRufnr/Verzeichnisse/start.html
- CNMC numbering register: https://numeracionyoperadores.cnmc.es/
- CNMC Spain open-data terms (CC BY-SA 4.0): https://data.cnmc.es/condiciones-de-uso
- UKE numbering tables: https://numeracja.uke.gov.pl/
- Digitaliseringsstyrelsen number register: https://digst.dk/tele/telefoni-og-internet/numre/nummerregistret/
- Nkom Norwegian number series: https://nkom.no/telefoni-og-telefonnummer/telefonnummer-og-den-norske-nummerplan/alle-nummerserier-for-norske-telefonnumre
- Nkom E.164 open dataset: https://data.norge.no/nb/datasets/c1617f91-fb9c-4546-8f06-dcd53f82a76f/samla-norsk-nummerplan-for-telefoni-mm-e164
- NLOD 2.0 licence: https://data.norge.no/nlod/en/2.0
- ComReg numbering: https://www.comreg.ie/industry/licensing/numbering/
- ANACOM numbering ranges: https://anacom.pt/render.jsp?categoryId=364956
- BAKOM number blocks and codes: https://www.bakom.admin.ch/en/number-blocks-and-codes
- NANPA CO code assignment records: https://nanpa.com/index.php/reports/co-code-reports/cocodes_assign
- CNAC CO code status: https://www.cnac.ca/co_codes/co_code_status.htm
- ACMA numbering: https://www.acma.gov.au/
- IMDA numbering: https://www.imda.gov.sg/regulations-and-licensing-listing/numbering
- ANATEL numbering: https://www.gov.br/anatel/pt-br/regulado/numeracao
- India DoT NRMS: https://eservices.dot.gov.in/numbering-resource-management-system
- MIMIT Italy numbering resources: https://www.mimit.gov.it/index.php/it/comunicazioni/telefonia/risorse-di-numerazione
- EETT Greece numbering: https://www.eett.gr/en/operators/electronic-communications/numbering/
- ANCOM Romania numbering: https://www.ancom.ro/en/category/reglementare-ro-10001-en/numbering/
- New Zealand NAD number register: https://www.nad.org.nz/number-register
- IFT Mexico numbering: https://www.ift.org.mx/
- UAE TDRA number resources: https://tdra.gov.ae/en/Services/allocate-number-resources
- Saudi CST numbering: https://www.cst.gov.sa/en/about/Numbering

- PTS Suède : https://www.pts.se/
- ANRT Maroc : https://www.anrt.ma/
- ARPCE Algérie : https://www.arpce.dz/
- ARTP Sénégal : https://www.artp.sn/
- ARTCI Côte d’Ivoire : https://www.artci.ci/
- ART Cameroun : https://www.art.cm/
- ICASA Afrique du Sud : https://www.icasa.org.za/
- FCC États-Unis (régulateur ; NANPA est l’administrateur de numérotation) : https://www.fcc.gov/
- CRTC Canada (régulateur ; CNA/CNAC est l’administrateur de numérotation) : https://crtc.gc.ca/
- AGCOM Italie (régulateur ; MIMIT publie les ressources référencées ici) : https://www.agcom.it/
- RTR Autriche, jeux Open Data : https://data.rtr.at/pages/open-data/tn-geo
- RTR Autriche, jeux Open Data : https://data.rtr.at/pages/open-data/tn-dienste
- RTR Autriche, jeux Open Data : https://data.rtr.at/pages/open-data/tn-ortsnetze

## Ordre d'implémentation recommandé

1. Ofcom Royaume-Uni : intégré.
2. ACM Pays-Bas : intégré.
3. ČTÚ Tchéquie : intégré.
4. BIPT Belgique : XLSX officiel, à intégrer après validation d'un pipeline XLSX déterministe.
5. Traficom Finlande : API OData v4 et plusieurs catégories de numérotation.
6. NANPA États-Unis : valider d’abord les droits de réutilisation/redistribution avant tout snapshot dans le dépôt public.
7. CNAC Canada : valider d’abord les droits de réutilisation/redistribution avant tout snapshot dans le dépôt public.
8. NAD Nouvelle-Zélande : téléchargements CSV par plage ; construire une agrégation complète et contrôlée avant import.
9. CNMC Espagne : ZIP quotidien + CC BY-SA 4.0 ; importeur géographique/mobile en préparation.
10. Nkom Norvège : CSV E.164 + NLOD 2.0 ; importeur en préparation.
11. Bundesnetzagentur Allemagne, puis MIMIT Italie, Suisse, Pologne et Danemark après qualification de format et de droits.

## Fonctionnement automatique et autonome

Après fusion de cette PR dans `main`, le workflow `international-numbering-refresh.yml` lance quotidiennement à 06:19 UTC les collectes ARCEP, Ofcom, ACM, ČTÚ et RTR, successivement. Chaque collecte télécharge les sources, exécute son parseur, les tests de l’intégration téléphonique et le build, puis publie uniquement son index autorisé dans `main`, sans PR de données à approuver. Un contenu inchangé ne crée pas de commit.

Le lancement quotidien sérialise les pays et la surveillance. L’échec d’un pays n’empêche pas l’exécution des suivants. Les déclenchements manuels d’un collecteur isolé restent protégés par le contrôle de concurrence sur `main`. Le script refuse un changement de schéma/pays, une régression de date de publication connue, une régression de date maximale d’attribution quand disponible, une perte de plus de 10 % des enregistrements, des fichiers déjà indexés sans rapport et une branche `main` ayant changé depuis le checkout. Aucun push forcé ni contournement des protections de branche n’est utilisé. En cas d’échec, la dernière version publiée reste disponible ; la prochaine exécution planifiée retente la mise à jour. Une perte légitime supérieure au seuil nécessite une investigation avant modification du contrôle.

Le workflow `international-numbering-watch.yml` contrôle quotidiennement à 05:47 UTC toutes les URL de la section des sources et publie `docs/data/international-numbering-watch.json` : accès, changement d’empreinte, dernière réussite et erreurs. Il limite la taille, le temps et le nombre de requêtes, retente une fois chaque source et conserve l’empreinte précédente en cas d’échec. Il ne republie aucun contenu externe et ne confond pas changement d’une page et nouvelle publication d’un dataset. Les redirections sont refusées : une URL déplacée apparaît indisponible jusqu’à requalification de son adresse officielle.

Les nouvelles autorités sont surveillées automatiquement. Les pays sans parseur qualifié restent non intégrés : aucun numéro, format ou droit de redistribution n’est inventé. RTR dispose d’un mode `--automatic` : découverte d’un unique lien CSV nommé pour chacun des trois jeux sur les pages Open Data officielles, téléchargement HTTPS et validation par le parseur existant. Le parcours réseau réel n’a pas pu être vérifié ici : si les pages ne publient pas ces liens statiques, la collecte échoue explicitement et conserve le dernier index. Aucun endpoint n’est deviné. Le mode manuel reste disponible pour le diagnostic. Les états `QUERY_ONLY`, `LEGAL_REVIEW_REQUIRED` et `DISABLED` continuent d’interdire un snapshot public.

Conditions de fonctionnement : GitHub Actions doit être activé et `GITHUB_TOKEN` doit pouvoir écrire dans `main` selon les règles du dépôt. Si une protection exige une PR ou des vérifications externes pour chaque commit, la publication échoue explicitement ; ces règles ne sont pas changées par cette PR. Les commits réalisés avec `GITHUB_TOKEN` ne déclenchent pas d’autres workflows `push` GitHub Actions : les tests et le build sont donc exécutés avant le push dans le workflow de collecte. Le déploiement et la synchronisation Android ne sont pas ajoutés ici. La planification GitHub n’est active que sur la branche par défaut et peut être retardée ou désactivée par GitHub.

## Contrat commun futur des importeurs

Chaque importeur pays doit produire une sortie normalisée séparant au minimum :

- countryCallingCode ;
- nationalPrefixStart / nationalPrefixEnd ;
- resourceType ;
- originalAssignee ;
- assigneeCode quand disponible ;
- territory / rateCenter / geographicArea quand disponible ;
- allocationDate ;
- sourcePublishedAt quand la source le fournit réellement ;
- fetchedAt ;
- sourceUrl ;
- sourceHash ;
- sourceSchemaVersion ;
- portabilityDisclaimer.

Un import doit être fail-closed si le schéma officiel change, si la taille dépasse les bornes prévues, si le fichier est vide, si la provenance HTTPS n'est pas celle autorisée, ou si la nouvelle publication est plus ancienne que le snapshot courant lorsque la source expose une date/version fiable.

## Garde juridique de réutilisation

La disponibilité publique d'un fichier ne vaut pas automatiquement autorisation de le republier dans le dépôt ou dans un produit commercial. Avant tout nouvel import pays, Sentinel doit classer la source en l'un des états suivants :

- `REDISTRIBUTION_ALLOWED` : licence ou texte officiel autorisant clairement la réutilisation/redistribution ;
- `QUERY_ONLY` : consultation ou interrogation de la source permise, mais snapshot public local non autorisé ou non établi ;
- `LEGAL_REVIEW_REQUIRED` : conditions ambiguës ou droit de redistribution non démontré ;
- `DISABLED` : réutilisation incompatible avec les conditions applicables.

Aucun workflow ne doit committer un dataset externe dans le dépôt public lorsque l'état est `QUERY_ONLY`, `LEGAL_REVIEW_REQUIRED` ou `DISABLED`.

## Garde anti-rollback

Les datasets officiels ne doivent plus pouvoir régresser silencieusement. Toute CI d'ingestion doit refuser par défaut :

- un `sourcePublishedAt` antérieur au snapshot courant lorsque ce champ est fourni officiellement ;
- une version de catalogue inférieure ;
- une date maximale d'attribution qui recule sans justification explicite ;
- une chute anormale du nombre d'enregistrements au-delà d'un seuil documenté ;
- un changement de schéma non couvert par les tests.

Pour une source sans date/version intégrée au fichier, Sentinel ne doit jamais inventer une date de publication : il conserve le hash et `fetchedAt`, puis applique les contrôles déterministes avant publication autonome ; une anomalie bloque la publication.

Une diminution du nombre d'enregistrements peut être légitime (retraits/révocations) ; elle doit donc déclencher une revue, pas être interprétée automatiquement comme une corruption.

## Limites produit

L'attribution réglementaire d'un bloc est une donnée de provenance télécom, pas un verdict de réputation. La portabilité peut rendre l'opérateur courant différent de l'attributaire initial. Les informations de numérotation ne doivent donc jamais déclencher seules un blocage d'appel ou une accusation de fraude.


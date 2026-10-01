# Sources officielles de numérotation internationale

Statut : index locaux France et Autriche ; catalogue de sources officielles vérifié pour l'expansion internationale. Ce document ne constitue pas un annuaire mondial des opérateurs.

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
| France | ARCEP | MAJNUM + identifiants opérateurs | index local automatisé, refresh review-gated |
| Autriche | RTR | Open Data de numérotation | index local importé ; refresh encore manuel |
| Royaume-Uni | Ofcom | S1/S3/S5/S7/S8/S9 | importeur hebdomadaire fail-closed intégré |

## Priorité A — données officielles structurées directement exploitables

| Pays | Autorité / administrateur | Données officielles utiles | Format / cadence observée | Cible Sentinel |
|---|---|---|---|---|
| Royaume-Uni | Ofcom | numéros disponibles/alloués, blocs, codes de portabilité, CUPID, MNC, plages protégées | CSV/XLSX/ZIP ; publication annoncée chaque mercredi | import automatique hebdomadaire intégré |
| Belgique | IBPT / BIPT | base des numéros réservés et attribués par bloc ; base C00XX ; séries annulées | XLSX ; publication récente 30/09/2026 | import après validation XLSX déterministe |
| Pays-Bas | ACM | registre public complet des numéros et titulaires | ZIP d'un CSV, CC0 1.0 ; fichier sans date de publication intrinsèque | import automatique hebdomadaire |
| Tchéquie | ČTÚ | numéros et codes attribués | CSV/XLSX Open Data + schéma CSVW ; périodicité quotidienne | import automatique quotidien |
| Finlande | Traficom | plages fixes, indicatifs mobiles, numéros de service, codes opérateurs et MNC | API OData v4 + tables ; données ouvertes | import automatique API |
| États-Unis et territoires | NANPA | affectations NPA-NXX, milliers de blocs, société/OCN, rate center, statut | ZIP texte/CSV/XLSX ; plusieurs rapports quotidiens ou temps réel | import quotidien, par type de ressource |
| Canada | CNA / CNAC | statut des CO codes NPA-NXX, société/OCN, zone, statut | CSV par NPA + archive CSV globale | import automatique |
| Nouvelle-Zélande | NAD | registre complet des Code Blocks, attributaire, statut, date, catégorie et zone | export CSV global et CSV par plage | import automatique |

## Priorité B — source officielle exploitable, format à qualifier avant automatisation

| Pays | Autorité | Données | État de qualification |
|---|---|---|---|
| Allemagne | Bundesnetzagentur | répertoire des blocs géographiques attribués + titulaires | ZIP officiel mis à jour selon besoin ; schéma interne à figer avant parser |
| Suisse | OFCOM / BAKOM | blocs et indicatifs E.164 disponibles/attribués | listes officielles ; format d'export à valider |
| Espagne | CNMC | registre de numérotation, opérateurs, mouvements, bulk download | téléchargement global annoncé ; schéma à capturer et tester |
| Pologne | UKE | tables de numérotation attribuée PSTN, PLMN, M2M, MNC, services | tableaux publics ; mécanisme d'export à confirmer |
| Danemark | Digitaliseringsstyrelsen | Nummerregistret : ressources attribuées, disponibles et réservées, titulaires | registre public ; interface/export à qualifier |
| Norvège | Nkom | séries de numéros norvégiennes attribuées | publication officielle ; format bulk à qualifier |
| Irlande | ComReg | assignments & availability, SMS, 1800/0818, DNO | outils de recherche officiels ; bulk public à confirmer |
| Portugal | ANACOM | gammes du PNN et décisions d'attribution/révocation | données officielles surtout décisionnelles ; bulk à confirmer |
| Italie | MIMIT | ressources de numérotation attribuées | XLSX officiel, fichier attribué daté du 15/09/2026 lors de la vérification | import après schéma XLSX déterministe |
| Grèce | EETT | ressources primaires attribuées + recherche de l'opérateur courant pour mobile | registre officiel ; bulk/export à qualifier | enrichissement intéressant car la portabilité est explicitement prise en compte dans la recherche opérateur |
| Roumanie | ANCOM | licences de ressources de numérotation, opérateur, domaine et statut | registre public consultable ; export à qualifier | import après qualification du mécanisme bulk |

## Priorité C — autorité équivalente identifiée, bulk d'attribution non encore confirmé

| Pays | Autorité | Observation |
|---|---|---|
| Australie | ACMA | gère le plan national et publie plusieurs registres de codes ; couverture bulk des numéros téléphoniques à confirmer |
| Singapour | IMDA | gère le National Numbering Plan et NORS ; accès public bulk d'attributions à confirmer |
| Brésil | ANATEL | gère les ressources de numérotation via nSAPN ; disponibilité d'un export public de blocs à confirmer |
| Inde | Department of Telecommunications / TRAI | NRMS et décisions d'allocation ; plusieurs listes/circulaires existent mais pas encore de source bulk normalisée retenue |
| Slovaquie | Úrad pre reguláciu elektronických komunikácií a poštových služieb | listes officielles de numéros attribués ; format et licence à qualifier |
| Mexique | IFT | plan national, système de numérotation et base de certains numéros non géographiques spécifiques | téléchargement public prévu pour certaines ressources ; source bulk générale à localiser avant intégration |
| Émirats arabes unis | TDRA | gestion et allocation nationale des ressources de numérotation | portail d'allocation surtout réservé aux opérateurs ; bulk public non identifié |
| Arabie saoudite | CST | plan national, zones et codes publics alloués | tables publiques partielles ; pas encore de registre bulk d'attribution retenu |

## Sources officielles vérifiées le 1er octobre 2026

- Ofcom numbering data: https://www.ofcom.org.uk/phones-and-broadband/phone-numbers/numbering-data
- BIPT reserved/allocated numbers: https://www.bipt.be/operators/publication/database-with-reserved-and-allocated-numbers
- ACM public number register: https://www.acm.nl/nl/telefoonnummers-zoeken
- ACM dataset catalogue: https://data.overheid.nl/dataset/register-van-toegekende-telefoonnummers
- ČTÚ allocated numbers and codes: https://data.ctu.gov.cz/dataset/pridelena-cisla-kody
- ČTÚ machine-readable schema: https://ctu.gov.cz/schemas/pridelena_cisla_a_kody.json
- Traficom open data: https://tieto.traficom.fi/en/open-data
- Bundesnetzagentur assigned geographic blocks: https://www.bundesnetzagentur.de/DE/Fachthemen/Telekommunikation/Nummerierung/ONRufnr/Verzeichnisse/start.html
- CNMC numbering register: https://numeracionyoperadores.cnmc.es/
- UKE numbering tables: https://numeracja.uke.gov.pl/
- Digitaliseringsstyrelsen number register: https://digst.dk/tele/telefoni-og-internet/numre/nummerregistret/
- Nkom Norwegian number series: https://nkom.no/telefoni-og-telefonnummer/telefonnummer-og-den-norske-nummerplan/alle-nummerserier-for-norske-telefonnumre
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

## Ordre d'implémentation recommandé

1. Ofcom Royaume-Uni : intégré.
2. ACM Pays-Bas : ZIP/CSV officiel, CC0 1.0 ; importeur en cours.
3. ČTÚ Tchéquie : CSV + schéma machine-readable officiel, cadence quotidienne.
4. BIPT Belgique : XLSX officiel, à intégrer après validation d'un pipeline XLSX déterministe.
5. Traficom Finlande : API OData v4 et plusieurs catégories de numérotation.
6. NANPA États-Unis : grands volumes mais données structurées et mises à jour fréquentes.
7. CNAC Canada : archive globale et CSV par NPA.
8. NAD Nouvelle-Zélande : registre complet exportable en CSV, attributaire et statut par Code Block.
9. Bundesnetzagentur Allemagne, puis CNMC Espagne, MIMIT Italie, Suisse, Pologne, Danemark et Norvège après qualification de format.

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

## Garde anti-rollback

Les datasets officiels ne doivent plus pouvoir régresser silencieusement. Toute CI d'ingestion doit refuser par défaut :

- un `sourcePublishedAt` antérieur au snapshot courant lorsque ce champ est fourni officiellement ;
- une version de catalogue inférieure ;
- une date maximale d'attribution qui recule sans justification explicite ;
- une chute anormale du nombre d'enregistrements au-delà d'un seuil documenté ;
- un changement de schéma non couvert par les tests.

Pour une source sans date/version intégrée au fichier, Sentinel ne doit jamais inventer une date de publication : il conserve le hash et `fetchedAt`, puis soumet tout changement à revue.

Une diminution du nombre d'enregistrements peut être légitime (retraits/révocations) ; elle doit donc déclencher une revue, pas être interprétée automatiquement comme une corruption.

## Limites produit

L'attribution réglementaire d'un bloc est une donnée de provenance télécom, pas un verdict de réputation. La portabilité peut rendre l'opérateur courant différent de l'attributaire initial. Les informations de numérotation ne doivent donc jamais déclencher seules un blocage d'appel ou une accusation de fraude.

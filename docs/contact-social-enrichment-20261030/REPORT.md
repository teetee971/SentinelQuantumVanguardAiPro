# Contacts D.1 — candidat14, profils sociaux assistés

**Parcours assisté implémenté ; collecte API NON IMPLÉMENTÉE. NOT READY pour installation tant que signature propriétaire et autorisation de mise à jour ne sont pas établies ; validation physique/UI NON EFFECTUÉE.** Le gel de fin de fiche reste ouvert. Aucun réseau ne doit apparaître comme connecté ou vérifié par API.

## Ce que fait réellement l’application

Le propriétaire a confirmé la recherche par nom/entreprise suivie de confirmation et indiqué ne connaître aucun accès développeur/API. Le choix d’implémentation est donc un parcours assisté via navigateur, annoncé explicitement, plutôt qu’un faux connecteur. Ce choix permet de progresser, mais ne satisfait pas encore l’objectif complet de collecte automatique d’un maximum d’informations.

La vraie fiche D1ContactProfileContent appelle ContactSocialProfilesCard, sans composant dormant. Le bloc **Profils liés** expose la recherche LinkedIn/Instagram/X, les liaisons locales existantes et leurs actions. La recherche est masquée par défaut et ne lance aucun réseau à l’ouverture.

1. Le propriétaire examine/modifie le nom et l’entreprise, choisit un réseau, puis ouvre explicitement une recherche DuckDuckGo filtrée sur ce réseau. L’UI nomme le destinataire et les deux champs transmis. Aucun numéro, SMS, journal, note, portrait Android ou carnet complet n’est envoyé par Sentinel.
2. Après consultation externe, il saisit l’URL du profil choisi et, facultativement, les informations publiques qu’il a relevées. Le nom/company/réseau/formulaire utilisent un état sauvegardable par contact pour le retour du navigateur. Le dialog de confirmation n’est pas restauré.
3. Une URL canonique HTTPS de profil du réseau attendu est exigée. Pages de publication/recherche/paramètres, domaines trompeurs, credentials, ports, chemins/contrôles interdits sont refusés. L’existence réelle du profil n’est pas vérifiée par l’application ; elle reste à examiner par l’utilisateur.
4. **Examiner la liaison** ne persiste rien. Un second dialogue montre contact cible/réseau/URL et exige **Confirmer la liaison**. Le statut est **Associé par vous**, sans badge d’identité vérifiée. Les informations supplémentaires portent **renseignées par vous**, pas collectées par API.
5. Ouvrir/retirer sont des actions explicites. Une seule liaison par réseau/contact ; la confirmation d’une nouvelle liaison du même réseau remplace cette entrée. Les autres réseaux/contacts restent inchangés. Aucun champ Contacts Android n’est écrasé.

Les identités sont liées au hash du LOOKUP_KEY Android, pas au seul nom, numéro ou identifiant numérique réutilisable. Le provider du contact sélectionné est relu avant les actions ; permission revérifiée, opérations asynchrones annulables, confirmation invalide au changement de propriétaire. Une agrégation Android changeant le lookup key peut nécessiter une réassociation manuelle : aucune correspondance n’est devinée.

## Stockage et revue

Liaisons dans un fichier de **noBackupFilesDir**, séparé de Room et des autres données existantes. Seuls hash de lookup, réseau, URL canonique, résumé saisi et date réelle d’association sont conservés. App sandbox/protection du système ; aucun chiffrement applicatif supplémentaire prétendu et aucune clé créée/exportée. Limite200liaisons, résumé2000caractères, livre UTF8≤1MiB ; livre corrompu/plein/échec non remplacé par un livre vide. Le module Social Intelligence ne reçoit aucune donnée Phone Core ; pas de jointure ni import Maigret.

Revue indépendante : SharedPreferences.commit peut échouer en disque après changement mémoire, et exclusion root ne couvre pas sharedpref pour tous transferts. Avant livraison, l’implémentation préférences non publiée a été remplacée par fichier borné noBackupFilesDir, écriture temporaire synchronisée et rename atomique au même emplacement. Aucun ancien fichier utilisateur migré/supprimé. Tests avec vrai fichier : échec de la primitive de remplacement conserve l’ancien contenu et supprime son propre temporaire ; dépassement de taille sans remplacement. Il ne s’agit pas de prétendre résister à tout dommage matériel/OS.

## Source et validations

- Dépôt teetee971/SentinelQuantumVanguardAiPro, branche fix/d1-phone-messages-conformity-20261029, PR brouillon1748.
- **Source des APK : cba8767ac46710f432781430b6bf524cee74f510**, base 4bc4f4d2d19558acdd95788c8e6014a1b6e6ee95 ; fichiers exacts dans SOURCE_FILES.txt. Les commits de livraison ultérieurs ne changent que la documentation/preuves.
- VersionCode14, versionName1.0.9, packagecom.sentinel.quantum ; min24/target36/compile37. Rebond désactivé de13 conservé ;13 reste disponible pour comparaison isolée.
- Compilation Kotlin et APK debug/test/unsigned : exit0,2m06. Build post-commit exit0,17s,tâches incrémentales sur mêmes sources.
- **App Android :1434 réussis,0 échec,0 erreur,0 ignoré**,306XML, dont12 tests sociaux. **Wearable :26 réussis,0 échec/erreur/ignoré**, tâche inchangée/cachée. **Node livraison :15 réussis,0 échec/ignoré** ; **Node cohérence produit :69 réussis,0 échec/ignoré**.
- Les Node produit ont d’abord révélé68réussites/1échec `release documentation follows the executable Android build baseline` : documentation non alignée sur versionCode14. Un encart source14/candidat non publié dans RELEASE_STATUS.md a corrigé ce décalage sans affirmer une release validée ; tests inchangés puis69/69.
- RED de validation réellement observé :10tests,2échecs (chemins Instagram encodés et type numérique du timestamp JSON), corrigés puis12/12 et suite1434/1434. API/fichier absent RED et étapes intermédiaires conservés dans evidence.
- **Trois nouveaux tests d’interface compilés, NON EXÉCUTÉS** : aucun appel/recherche avant geste, confirmation/annulation, changement de contact, restauration de brouillon sans confirmation ancienne. Aucun environnement Android cloud opérationnel. Les résultats JVM ne prouvent pas le retour navigateur réel, l’apparence S24+, les permissions physiques ou le transport.
- Aucun test avec personne réelle, aucun compte connecté, aucune requête sociale exécutée depuis le cloud ; aucune dépense ni secret/clé de signature modifié.
- Schéma Room non suivi conservé : SHA256 a7c5b4e1926c4441629734c0703ba6174e6bcf3796394b51302846bac0dae0c4.

## APK et signature

- **unsigned** `Sentinel-D1-v14-UNSIGNED-cba8767a.apk` — 101374983octets, SHA256 `e3c5315b9147478469b06cf17f39d0571f171abf68e46ee68ca9ec5b7d8fc7d4` ; apksigner exit1, alignement4 exit0, alignement16KiB exit0, aapt exit0.
- **cloud_debug** `Sentinel-D1-v14-CLOUD-debug-cba8767a.apk` — 101478009octets, SHA256 `4bd53989a8bfa45cf5c9b423e7cf1eed095309e4e3757c8c8a7ff1546149c62f` ; apksigner exit0, alignement4 exit0, alignement16KiB exit0, aapt exit0.
- **instrumentation** `Sentinel-D1-v14-androidTest-cba8767a.apk` — 1236818octets, SHA256 `8cccbab80d0ffda25914ef017e0199752aaa101d79e5302012c2677e89a82d52` ; apksigner exit0, alignement4 exit0, alignement16KiB exit0, aapt exit0.

**APK14 NON SIGNÉ — NON INSTALLABLE :** https://drive.google.com/file/d/1b5FszXwQhEFJlqjyHfSc_bL8R0BhGZx_/view?usp=drivesdk

apksigner exit1 du fichier unsigned est attendu, pas une signature validée. Cloud debug/test : certificatdee0d0f14e3a211d5ce9adca0280acde443e2312857d33da2258bef0f1b632e1, incompatible avec propriétaire68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2. La clé originale reste uniquement sur le téléphone. Aucun APK14 signé6891 dans le cloud, aucune installation automatique. Les métadonnées Drive/taille sont relues ; hash APK calculé localement, pas de relecture binaire distante revendiquée.

## Ce qui reste à faire

- Pour de vrais connecteurs : obtenir et vérifier droits/API/fournisseur, conditions/quotas/coûts puis implémenter recherche/lecture avec provenance. Une authentification ordinaire n’accorde pas forcément l’accès aux profils tiers. LinkedIn présent dans ChatGPT ne devient pas une API de l’APK. Pas d’API LinkedIn/Meta/X ajoutée ici ; pas de photo/biographie/entreprise automatiquement importée.
- Sur S24+ après signature et autorisation : ouvrir un contact de test, déplier Profils liés, vérifier champs/destinataire, effectuer recherche volontaire, revenir sans perdre le brouillon, annuler puis confirmer un lien, rouvrir la fiche, ouvrir/retirer le lien ; vérifier un deuxième contact et une révocation Contacts. Aucun résultat ne doit se rattacher automatiquement à un homonyme. Utiliser des données synthétiques/autorisées.
- Tester fin de fiche/remontée/Fermer, grands caractères, TalkBack et clavier. La mitigation13 n’a pas encore été validée ; ne pas transformer ce candidat14 en preuve de correction.
- Scénarios généraux S24+26, appels/SMS/MMS nécessitant une seconde ligne et fidélité visuelle D.1 restent à qualifier séparément. Voice inchangé.
- Aucune fusion dans main, schémas et travaux locaux préservés, PR en brouillon, aucun workflow payant ni installation sans autorisation.

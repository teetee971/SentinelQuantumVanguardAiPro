> Candidat historique 067f0f68. Livraison actuelle : [CURRENT_DELIVERY.md](CURRENT_DELIVERY.md).

# Fiche Contacts : relecture du carnet et révocation

## Défaut et correctif

Dans SentinelDialerActivity, refreshContacts rechargeait contactItems mais ne remplaçait pas profileContact, objet mémorisé indépendamment. Une fiche ouverte pouvait donc conserver son ancien nom, numéro ou photoRevision après la modification/suppression du contact dans le fournisseur Android.

ContactProfileSelection réconcilie la sélection par contactId avec le résultat complet READY : remplacement par le nouvel objet, fermeture si absent, permission refusée ou fournisseur indisponible. Une autorisation révoquée efface aussi la sélection et la liste mémorisées. La permission réelle est relue après la requête asynchrone avant publication, afin d'abandonner un résultat devenu non autorisé. Aucun contact, préférence, schéma ou donnée SQLite n'est écrit.

Raccordement réel : SentinelDialerActivity.refreshContacts appelle la politique avant le traitement du résultat ; D1ContactProfileSheet reçoit profileContact. Ce contrôle statique n'est pas une preuve de rendu runtime. Le résultat ne rouvre jamais une fiche fermée pendant la lecture.

## Reproduction et validation

Quatre tests JVM couvrent remplacement (nom/numéro/révision), suppression sans sélection d'un autre contact, révocation malgré un résultat READY et indisponibilité/absence de sélection. La première compilation des tests échoue sur le symbole ContactProfileSelection encore absent (phase rouge, aucune assertion exécutée). Journaux et codes de sortie dans evidence/contact-profile.

## Essais physiques NON TESTÉS

Sur un contact de test autorisé, ouvrir la fiche Sentinel ; modifier nom/numéro/photo dans Contacts Samsung, revenir à Sentinel et vérifier la fiche actualisée. Refaire avec suppression : la fiche doit se fermer, aucun autre contact ne doit la remplacer. Fermer la fiche pendant une relecture : elle ne doit pas se rouvrir. Révoquer READ_CONTACTS via les paramètres Android puis revenir : aucun ancien contact ne doit apparaître ; réattribution n'ouvre pas automatiquement une ancienne fiche.

Le fournisseur peut modifier/agréger ses identifiants : une fiche dont l'identifiant n'existe plus se ferme. Pas de restauration durable de la fiche, ni de nouvelle revendication de conformité visuelle D.1. Pas de test physique, de nouvel AAB, de validation Voice/OSINT ou de publication Play dans cette itération.

## Qualification exécutée

Sources APK : `067f0f68c7d31efd3482585d02834ce586b43b77`. Commande : `python /workspace/d1-evidence/run-gradle.py contact-profile-full :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`. Code de sortie 0. 1 390 tests, 301 suites XML, 0 échec/erreur/ignoré. Quatre tests nouveaux de sélection de fiche. Les 19 tests Node du candidat d7fce52a restent historiques, NON RÉEXÉCUTÉS pour ce changement Android. Tests instrumentés compilés mais NON EXÉCUTÉS.

Les deux APK : apksigner verify --verbose --print-certs et zipalign -c -v 4, codes 0. Avertissements de dépréciation Compose/Gradle préexistants conservés dans le journal. Pas de preuve de lancement sur appareil. Empreintes et tailles dans ARTIFACTS.json.

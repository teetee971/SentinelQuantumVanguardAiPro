# Matrice de validation Galaxy S24+ — candidat du 30 octobre 2026

Matrice initiale : aucun scénario n'a été exécuté directement depuis le cloud. Des preuves utilisateur ultérieures confirment l'installation version11 et plusieurs ouvertures/lectures ; voir OWNER_RUNTIME_OBSERVATIONS.md dans le dossier signature-recovery-20261030. Les lignes indiquées PARTIEL ne valent pas un PASS global. Android17/API37 et modèle S24+ sont déclarés par l'utilisateur, à relever sur le téléphone. Préconditions : propriétaire présent, lignes et contacts de test connus/autorisés, contrôle de signature préalable, autorisation explicite avant toute installation. Aucun appel ou SMS automatisé vers un numéro inconnu/surtaxé.

| ID | Scénario / action manuelle | Résultat attendu et preuve à conserver | Résultat réel |
|---|---|---|---|
| P01 | Précontrôle APK installé et candidat | Package identique, certificats identiques et versionCode non décroissant ; JSON du script en mode appareil, pas une comparaison de fichiers locaux | NON TESTÉ |
| P02 | Après autorisation, mise à jour sur place et lancement | Version11 confirmée et plusieurs écrans ouverts ; préférences/toutes données et journal crash non vérifiés | PARTIEL — preuves utilisateur |
| P03 | Attribution puis refus de ROLE_DIALER | Dialogue système, refus respecté, pas d'appel implicite ; rôle lu et captures | NON TESTÉ |
| P04 | Appel entrant, écran allumé puis verrouillé | InCallService et UI cohérents, identité disponible seulement, réponse/refus ciblés ; observation des deux lignes autorisées | NON TESTÉ |
| P05 | Appel sortant et choix SIM | Numéro confirmé, SIM choisie respectée, retour composeur sans appel automatique ; observations côté correspondant connu | NON TESTÉ |
| P06 | Répondre et raccrocher, côté local/distant | Aucun appel fantôme ni notification persistante après fin ; état opérateur et UI | NON TESTÉ |
| P07 | Deux appels : premier actif, second entrant, fin de l'un | Commandes ciblent le bon appel ; le second n'est pas perdu lors du retrait du premier. Tenue/échange uniquement si exposés et autorisés par Telecom/opérateur ; noter toute indisponibilité | NON TESTÉ |
| P08 | Pavé, effacement, DTMF sur ligne de test connue | Numéro prérempli exact, pas d'appel à l'ouverture, tonalités seulement sur l'appel ciblé | NON TESTÉ |
| P09 | Appel manqué et notifications | État manqué juste, ouverture correcte, confidentialité, notifications retirées après fin ; tester POST_NOTIFICATIONS / accès plein écran | NON TESTÉ |
| P10 | Récents, retrait rôle/READ_CALL_LOG puis réattribution | Cache purgé à pause/perte observée, anciennes générations non republiées ; relire après retour. Transition entièrement entre deux échantillons sans pause : limite connue | NON TESTÉ |
| P11 | Répertoire : recherche, filtres, photo présente/absente | Liste et fiche accessibles ; recherche/filtres et cas avec photo pas validés | PARTIEL — preuves utilisateur |
| P12 | Fiche ouverte, modifier nom/numéro/photo puis supprimer une fixture | Fiche actualisée au retour ; fermeture si identifiant disparu ; révision photo à URI identique et révocation READ_CONTACTS | NON TESTÉ |
| P13 | Message > Appeler/Ajouter aux contacts > annuler | Composeur prérempli sans appel, éditeur Contacts sans écriture automatique, aucune entrée créée après annulation | NON TESTÉ |
| P14 | Attribution/refus/retrait du rôle SMS et READ_SMS/SEND_SMS | Parcours système explicite, envoi/lecture non autorisés bloqués, réattribution cohérente | NON TESTÉ |
| P15 | SMS entrant/sortant simple puis multiparties | Texte exact et bon destinataire/SIM, statut Android/réseau distinct de la soumission ; aucune fausse livraison | NON TESTÉ |
| P16 | A/B : changer de fil pendant callback ou rafraîchissement | Aucune bulle A dans B ; réponse et raccourcis liés au résumé sélectionné ; pas de destinataire de repli si résumé absent | NON TESTÉ |
| P17 | Éditer texte/destinataire/mode ou réponse pendant préparation d'envoi | L'acceptation de l'ancien envoi ne supprime pas le nouveau texte ; autre fil intact ; vérifier messages réellement soumis avant tout renvoi | NON TESTÉ |
| P18 | MMS reçu et envoyé, image valide, trop volumineuse, source refusée | Transport opérateur prouvé séparément, sélection rejetée explicitement, aucune soumission pendant chargement | NON TESTÉ |
| P19 | Modifier/retirer des images pendant envoi MMS et retour tardif | La nouvelle génération n'est pas effacée par l'acceptation de l'ancienne ; nombre et contenu des fixtures cohérents | NON TESTÉ |
| P20 | Recréation Activity : rotation avant/pendant lecture, sélection d'image, envoi | Identité du fil/photo correcte, texte sauvegardable et images de session retenues ; brouillon conservé n'est pas preuve d'échec d'envoi | NON TESTÉ |
| P21 | Fermeture puis arrêt forcé/redémarrage, sans effacement de stockage | Préférences conservées ; images de session non promises après mort du processus ; pas de renvoi automatique. Vérifier statut provider avant de réessayer | NON TESTÉ |
| P22 | Notifications SMS/MMS, aperçu masqué/activé, permission retirée | Confidentialité conforme au choix, aucun contenu personnel dans les preuves partagées, état explicite en cas de blocage | NON TESTÉ |
| P23 | Retirer/réattribuer contacts, téléphone, journal, SMS, micro | Pas de crash ni d'action non autorisée ; pas de restauration d'une ancienne identité sur un autre écran | NON TESTÉ |
| P24 | Navigation rapide, retour système, clavier, texte 200 %, TalkBack | Aucun mauvais fil/contact ni commande superposée ; cible tactile lisible et parcours retour cohérent | NON TESTÉ |
| P25 | Retour Téléphone Samsung et application SMS précédente | Choix via Paramètres > Applications > Applications par défaut ; ne pas désactiver les apps système ; données Sentinel conservées | NON TESTÉ |
| P26 | Comparaison visuelle avec références D.1 | Captures utilisateur examinées qualitativement, écarts visibles ; voir OWNER_RUNTIME_OBSERVATIONS.md et VISUAL_GAPS_D1.md | PARTIEL — conformité NON VALIDÉE |

Pour chaque essai : ID, date UTC, build Android/One UI, SHA de l'APK, préconditions, observations, PASS/FAIL/NON TESTÉ et chemins de preuves expurgées. Ne pas transformer l'absence de crash dans un court journal en preuve d'absence de crash générale.

## Collecte et absence d'installation automatique

Précontrôle en lecture seule, avec outils Android locaux et série explicitement choisie :

```bash
python scripts/check-installed-sentinel-signature.py \
  --candidate /chemin/vers/le-candidat.apk \
  --build-tools /chemin/Android/Sdk/build-tools/36.0.0 \
  --adb /chemin/Android/Sdk/platform-tools/adb \
  --serial VOTRE_SERIE_ADB \
  --output preuves/signature-installee.json
```

Cette commande ne lance aucune installation, suppression, permission ou changement de rôle. `MATCH` ne vaut que pour package/certificats/version ; toute erreur ou incompatibilité bloque la procédure. La compatibilité de lignée différente nécessite une analyse distincte.

Après contrôle favorable **et autorisation explicite**, la commande de mise à jour sur place peut être exécutée par le propriétaire : `adb -s VOTRE_SERIE_ADB install -r /chemin/vers/le-candidat.apk`. Ne pas ajouter de contournement de downgrade, ne pas désinstaller et ne pas effacer les données. En cas de conflit : arrêter et conserver l'application existante.

Collectes ADB lisibles et ciblées (lecture seule) :

```bash
adb -s VOTRE_SERIE_ADB shell getprop ro.product.model
adb -s VOTRE_SERIE_ADB shell getprop ro.build.version.release
adb -s VOTRE_SERIE_ADB shell getprop ro.build.version.sdk
adb -s VOTRE_SERIE_ADB shell cmd role get-role-holders android.app.role.DIALER
adb -s VOTRE_SERIE_ADB shell cmd role get-role-holders android.app.role.SMS
adb -s VOTRE_SERIE_ADB shell cmd package list packages -U com.sentinel.quantum
```

Le dernier relevé donne l'UID à utiliser pour un journal ciblé : `adb -s VOTRE_SERIE_ADB logcat -d --uid=UID_RELEVE -v threadtime -s SentinelPhoneRole:I SentinelDialer:W AndroidRuntime:E`. Inspecter et expurger en mémoire/localement avant tout partage ; ne pas joindre un dumpsys telecom/SMS ou logcat global brut. Les captures peuvent contenir contacts et SMS : privilégier les fixtures puis masquer avant export. Le collecteur existant capture-phone-core-s24.py expurge ses sorties mais nécessite encore une inspection humaine ; il ne marque aucun scénario PASS.

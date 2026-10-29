# Anomalies et limites restantes

| Sujet | Statut | Condition de clôture |
|---|---|---|
| Conformité visuelle D.1 complète | NON VALIDÉE | Captures du candidat sur runtime utilisable, comparaison annotée aux références reçues, correction des écarts ; les décorations générées ne sont pas les maquettes officielles. |
| Galaxy S24+ Android17 : lancement, appels, rôle, notifications, permissions, SMS/MMS, navigation | NON TESTÉ | Exécuter PHYSICAL_TESTS_AND_CAPTURE.md avec lignes de test connues et preuve pour chaque scénario. Installation de l’ancien APK rapportée par l’utilisateur seulement. |
| PTT distant HTTP503 | BLOQUÉ | Ready200, prérequis authentification/LiveKit/Redis vérifiés par propriétaire sans exposer de secret, essai bi-appareil autorisé. Le prérequis exact fautif n’est pas démontré. |
| Transformation vocale d’un appel SIM | NON PRISE EN CHARGE | Nécessite une architecture de téléphonie différente, hors correctif D.1 ; ne pas présenter comme un réglage activable. |
| OSINT sur téléphone | NON TESTÉ après correction | Confirmer récupération réelle des trois sources, cache/hors-ligne, dates et ouverture des liens ; les sondes hôte ne suffisent pas. |
| Contacts Sentinel enrichis et profils sociaux vérifiés | NON DÉVELOPPÉS dans le parcours livré | La fiche actuelle utilise seulement le carnet local et les actions explicites ; aucune synchronisation ou confiance fictive. |
| Antivirus complet, isolement système, quarantaine générale, télémétrie mondiale illustrés | NON QUALIFIÉS / capacités non développées selon surface | Démontrer chaque capacité avant d’afficher les statuts/chiffres des maquettes. Ne pas les assimiler aux inventaires et analyses locaux existants. |
| Bluetooth, VPN, Voice Studio local, Security Lab | NON TESTÉS sur matériel dans cette reprise | Modules préservés ; requalifier permissions, matériels, tunnel et audio avant revendication. |
| Émulateur AOSP17 | BLOQUÉ au démarrage | Journaux conservés, pas de segmentation confirmée ici ; ne bloque pas la qualification statique. |
| Publication Google Play | NON PRÊTE comme version D.1 finale | APK debug non publiable ; qualification physique/visuelle, AAB release signé avec clé autorisée, permissions sensibles, politique de confidentialité/Data Safety et procédure Play à finaliser. Aucun déploiement demandé. |

## Itération photos après audit c3226314

Le défaut d’invalidation à URI inchangée et la course de publication après annulation sont corrigés dans les sources 70d5cecd ; preuve JVM dans PHOTO_REFRESH_FOLLOWUP.md. Validation Compose/provider et S24+ toujours NON EXÉCUTÉE/NON TESTÉE. Cette correction ne clôt pas les écarts de fidélité visuelle ni les autres blocages ci-dessus.

## Itération Messages après candidat 70d5cecd

Navigation interne et externe corrigée dans 8cc1ad4e ; preuves et limites dans MESSAGES_NAVIGATION_FOLLOWUP.md. Les brouillons texte sont conservés en mémoire sauvegardable durant cette Activity, pas dans un nouveau stockage durable. Les pièces jointes MMS sélectionnées restent en remember : elles ne sont pas restaurées après recréation, limitation préexistante non corrigée par cette navigation. Tests réels conversation/IME et tests instrumentés restent NON EXÉCUTÉS.

## Itération MMS après candidat 8cc1ad4e

La limitation de rotation décrite dans la section historique précédente est traitée dans les sources aed338fb : images validées retenues en mémoire par ViewModel durant les changements de configuration de la même session. Le code ne sérialise ni payload ni URI dans le Bundle et ne crée aucun stockage durable. La mort du processus nécessite une nouvelle sélection. Les tests Activity, grants du sélecteur, rotation pendant chargement/envoi et MMS opérateur restent NON EXÉCUTÉS/NON TESTÉS. Voir MMS_DRAFT_FOLLOWUP.md.

## Itération actions de conversation

Appeler ce numéro et Ajouter aux contacts sont implémentés dans l’en-tête d’une conversation ouverte (sources d7fce52a). Ils ouvrent seulement un composeur prérempli ou l’éditeur Android ; application cible indisponible signalée explicitement. Ouverture réelle sur S24+, Contacts Samsung, grands caractères, TalkBack et comparaison R02 restent NON TESTÉS. L’adresse affichée n’est pas une preuve d’identité, de joignabilité ni d’exhaustivité des participants d’un MMS de groupe.

## Itération relecture de fiche Contacts

Sources 067f0f68 : fiche réconciliée avec le carnet rechargé, fermeture si contact absent ou accès refusé/indisponible ; résultat tardif rejeté si permission révoquée. Quatre tests JVM et suite complète réussis. Fournisseur Contacts Samsung, modification/suppression et révocations sur S24+ NON TESTÉS. Voir CONTACT_PROFILE_FOLLOWUP.md.

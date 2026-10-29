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

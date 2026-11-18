# Matrice de fonctionnalités · Sentinel Phone Core V1

Les états de cette matrice se rapportent au profil `-PsentinelCommercialV1=true`. Les tests locaux et la recette terrain ont des colonnes distinctes.

| Fonction | Inclusion | Implémenté | Compilé et testé en local | Qualifié S24+ | Commercialisable |
| --- | --- | --- | --- | --- | --- |
| Installation, rôles, assistant et diagnostic | V1 | Activités et contrats Android conservés. | Voir le build et les tests de contrat. | BLOCKED · installer le candidat, refuser/accorder les rôles, vérifier la reprise. | NO-GO |
| Téléphone, appels et notifications | V1 | Clavier, Telecom, contrôle exact de session et raccrochage de notification. | Contrats et tests de route avec fixtures. | BLOCKED · appels entrants/sortants, double SIM, raccrochage notification et verrouillage. | NO-GO |
| Identification et filtrage local | V1 | Contacts, sources d’attribution publiques, vérification réseau quand disponible, règles locales et abandon des effets tardifs. | Tests d’attribution, purification du chemin Telecom et révocation. | BLOCKED · refus/retrait du rôle, conformité des étiquettes et délais Telecom. | NO-GO |
| SMS et conversations | V1 | Route Android, rôle, AppOps, accusés, reprise des reçus corrélés. | Tests JVM et contrats des frontières de soumission. | BLOCKED · contrôler accusés opérateur, perte rôle/SIM et recréation. | NO-GO |
| MMS et pièces jointes | V1 | Lecture bornée, journal local, récupération et sélection de SIM. | Tests de décodage, durabilité et bornes en local. | BLOCKED · transport réel et conservation après interruption. | NO-GO |
| Contacts | V1 | Consultation, recherche et ouverture de la fiche dans l’éditeur Contacts système. Comptes et synchronisation sont gérés par Android. | Contrats de concurrence et de confidentialité. | BLOCKED · permissions, édition et compte de test autorisé. | NO-GO |
| Journaux locaux et confidentialité | V1 | Journaux privés, export volontaire, effacement confirmé et paramètres essentiels. | Contrats de navigation et de stockage. | BLOCKED · vérifier refus, cache, export et absence de données hors périmètre. | NO-GO |
| IA C11 / Qwen | EXCLUDE | Préservé dans C11 avec son paquet de preuve distinct. | Les preuves C11 restent associées à leurs binaires. | BLOCKED · Qwen ARM64, Keyguard, reboot et scénario C11 dédié. | Hors V1 |
| Wi-Fi, Bluetooth et suivi de traceurs | EXCLUDE | Routes et permissions de scan retirées du profil. Le routage audio Telecom des appels est un parcours distinct. | Manifeste V1 et contrats des workers. | NOT RUN | Hors V1 |
| VPN, Mesh, Wear et Studio voix | EXCLUDE | Entrées et surfaces Android optionnelles retirées du profil. | Inspections de manifeste et de bibliothèques natives. | NOT RUN | Hors V1 |
| Veille, réputation distante, WhatsApp, partage d’e-mail et autre enrichissement | EXCLUDE | NavGraph limité, retour des anciens jobs fermé, ponts optionnels masqués. | Contrats du profil et tests des barrières. | NOT RUN | Hors V1 |

Les aperçus sensibles SMS restent soumis à la préférence de notification. Le masquage des informations sensibles sert la confidentialité locale du Phone Core. [Tests](TEST_EVIDENCE.md) · [Recette S24+](S24_PHYSICAL_ACCEPTANCE.md)

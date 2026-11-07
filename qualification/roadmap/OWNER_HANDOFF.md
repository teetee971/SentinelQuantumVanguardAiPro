# Actions externes à la finalisation logicielle

Ce dossier prépare les décisions et les preuves, sans les inventer. Aucun compte Play, certificat, service de suppression ou appareil n'a été validé ici. Ne transmettre aucune clé privée, mot de passe, jeton ou secret Auth0.

| Responsable | Action concrète | Preuve de clôture |
|---|---|---|
| Propriétaire Play | Identifier application existante, type/date du compte et maximum versionCode de toutes les pistes | Capture/export Console anonymisé ; candidate24 strictement supérieure au maximum, sinon nouvelle version |
| Propriétaire signature | Comparer certificat public d'upload à Play ; construire dans son environnement autorisé avec sa clé existante | AAB signé, jarsigner/keytool, SHA256 et certificat concordant ; aucun keystore transmis |
| Exploitant / identité | Fournir URL HTTPS réelle pour demander suppression du compte et données Auth0/Sentinel | Parcours web et in-app testés, autorisation vérifiée, données associées et délais documentés ; logout insuffisant |
| Exploitant / confidentialité | Renseigner identité/contact et rétentions ; publier une page publique accessible | Test d'accès public réel et revue de conformité au trafic exact de l'AAB ; 403 vu du cloud encore non résolu |
| Propriétaire Play | Compléter Permissions SMS/Call Log, full-screen intent, Data Safety, App Access, contenu/publicité et éventuel VPN | Déclarations sauvegardées et décisions Console ; aucun oui/non de collecte deviné à partir des seules permissions |
| Testeur autorisé | Exécuter le laboratoire Doctor sous .labpreview, sans rôles ni modification de la v22 | Rapport instrumentation réel + confirmation/annulation/rotation ; critères Doctor distincts du certificat Phone Core |
| Testeur opérateur | Qualifier l'APK exact futur sur environnement autorisé | 14 critères Phone Core et scénarios SIM/révocation/notifications/échec transport ; pas de substitution par preuves anciennes |
| Propriétaire Play | Tester sur piste interne puis fermée si applicable | Distribution observée, retours et accès production accordé ; 12 testeurs/14 jours si exigé pour le compte |
| Exploitant services | Provisionner VPN, signaling/LiveKit/PSTN et autres infrastructures conditionnelles | E2E réel, identité/autorisation, révocation et exploitation ; absence d'infrastructure reste un blocage |

## Brouillon de justification des permissions

À transcrire dans les formulaires en se fondant sur la release signée exacte, après essais :

- Téléphone par défaut : appels utilisateur, interface d'appel et historique ; `CALL_PHONE`/`READ_CALL_LOG` sous rôle Téléphone et consentement Android. Pas d'analyse générale du journal pour publicité.
- SMS par défaut : conversations, composition et réception SMS/MMS via les API Android ; `SEND_SMS`, `READ_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` sous rôle SMS. Démonstration vidéo de la sélection du rôle puis des fonctions principales à fournir.
- `READ_PHONE_STATE` : identification des abonnements/SIM sélectionnables. Pas de promesse d'accès aux identifiants interdits.
- `USE_FULL_SCREEN_INTENT` : uniquement interface d'appel entrant éligible ; vérifier le réglage Android et la déclaration Play.
- `READ_CONTACTS`, notifications, Wi-Fi/Bluetooth/localisation : demandes contextuelles liées aux fonctions choisies.
- `RECORD_AUDIO` : aperçu voix local explicite ; ne pas prétendre transformer l'audio d'appels SIM.
- Nettoyeur : aucune nouvelle permission de fichiers ou d'inventaire global ; pas de nettoyage des autres applications, pas de promesse antivirus/accélération/mémoire vive.

## Préparation Data Safety

Mesurer et examiner l'AAB exact et les 187 composants résolus (inventaire précédent, dépendances production inchangées). Compléter au minimum les catégories suivantes ; la présence dans cette table ne signifie pas automatiquement « collected » au sens du formulaire Google :

| Flux | Traitement à vérifier |
|---|---|
| Contacts, historique, SMS/MMS | Stockage/provider Android, accès local sous rôle ; transport opérateur distinct d'un backend Sentinel |
| Veille publique | Requêtes HTTPS, adresse IP/métadonnées de connexion reçues par les sources ; pas de contenu SMS envoyé par ce module |
| Réputation distante facultative | Numéro transmis après opt-in ; vérifier service, conservation, finalité, sécurité et suppression |
| Compte optionnel Auth0 | Identifiant/e-mail, métadonnées et gestion de session ; activation bloquée sans route de suppression, mais configuration ne prouve pas suppression |
| Aperçu vocal | Cache privé temporaire, suppression de l'aperçu ; absence de téléversement à vérifier sur la release |
| Journaux et rapport Doctor | Locaux ; partage seulement sur action utilisateur, pouvant transmettre le rapport à l'application choisie |
| SDK dépendants | Inventaire, initialisation réelle et trafic ; ne pas conclure « aucune collecte » à partir d'un SDK non configuré en debug |

## App Access et captures

Phone Core local ne doit pas nécessiter de compte. Fournir instructions de rôle/permissions, SIM et limites constructeur. Si une fonction de la release nécessite un compte, fournir l'accès de revue par le canal privé Console, jamais dans Git. Captures : clavier, appel entrant, historique, répertoire, conversations et MMS avec données fictives ; Doctor avec copies factices et rapport. Exclure les fonctions non activées, numéros réels, tokens et toute certification non démontrée.

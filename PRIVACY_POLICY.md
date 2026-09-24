# Politique de confidentialité — Sentinel Quantum Vanguard AI Pro

**Dernière mise à jour : septembre 2026**

## Périmètre

Cette politique décrit le périmètre actuellement documenté du dépôt Sentinel Quantum Vanguard AI Pro. Elle ne constitue pas une garantie applicable à une future configuration, à une intégration tierce ou à une version non décrite ici.

## Données

Le dépôt ne doit pas être présenté comme collectant ou stockant systématiquement des données personnelles. Les traitements réels dépendent des fonctionnalités activées, du navigateur, du terminal, des sources consultées et de l'infrastructure de déploiement.

Avant un déploiement réel, l'exploitant doit vérifier les données effectivement traitées, les journaux, le stockage local, les communications réseau, les fournisseurs externes et les durées de conservation applicables.

## Services et communications externes

Le site peut être déployé sur Cloudflare Pages et certains modules de veille peuvent consulter des sources publiques. Une absence de transfert de données ne doit donc pas être affirmée de manière générale sans mesure sur la configuration effectivement déployée.

Sentinel n'a pas de dépendance opérationnelle à Firebase ni à A KI PRI SA YÉ. Les références Firebase conservées dans les contrôles d'isolation et les tests négatifs sont des fixtures de sécurité destinées à vérifier la détection de dépendances interdites.

## Android

Le code Android canonique se trouve dans `native-android-app/`. Aucun APK précompilé et signé n'est actuellement annoncé comme distribué. Les permissions, traitements et communications d'une future application Android devront être vérifiés sur l'artefact réellement construit avant diffusion.

### Application Android (`native-android-app/`)

L'application Android actuelle traite principalement les données Phone Core localement sur l'appareil :

- les numéros filtrés par le service de filtrage d'appels sont stockés sous forme d'empreintes HMAC, sans conservation en clair des numéros dans cette liste de filtrage ;
- lorsque l'utilisateur choisit Sentinel comme application Téléphone par défaut, le Phone Core peut demander les permissions téléphoniques nécessaires au composeur, à l'état téléphonique et à l'historique d'appels ; les appels restent exécutés par les API Telecom d'Android et le choix de ligne/SIM est borné aux comptes téléphoniques actifs exposés par Android ;
- l’accès au répertoire est désactivé tant que l’utilisateur ne l’autorise pas explicitement ; lorsqu’il est accordé, le nom et la société d’un contact sont recherchés localement pour la fiche d’appel et ne sont ni exportés, ni synchronisés par ce composant ; la permission peut être révoquée dans Android ;
- lorsque l'utilisateur choisit explicitement Sentinel comme application SMS par défaut, l'application peut demander `SEND_SMS`, `READ_SMS`, `RECEIVE_SMS`, `RECEIVE_MMS` et `RECEIVE_WAP_PUSH` afin d'envoyer et recevoir les messages et d'afficher les conversations. Les SMS entrants et sortants sont alors enregistrés dans le fournisseur SMS Android conformément au rôle d'application SMS par défaut ;
- l'envoi SMS multi-SIM utilise uniquement les abonnements actifs exposés par Android et lie l'envoi à l'abonnement sélectionné ; les états techniques SENT et DELIVERED sont suivis localement pour refléter le résultat fourni par la pile téléphonie ;
- les MMS entrants sont traités localement avec des limites de taille et une validation d'aperçu ; le contenu non validé reste en quarantaine locale. Les téléchargements MMS utilisent l'API opérateur d'Android et un abonnement/SIM valide ;
- l'analyse locale des liens contenus dans les SMS reçus par Sentinel ou dans des e-mails/messages explicitement partagés reste sur l'appareil ;
- les notifications d'appels et de messages respectent la permission `POST_NOTIFICATIONS` lorsqu'elle est requise par la version Android et l'état des canaux de notification ;
- le scanner Wi-Fi est local et passif. Il dépend des permissions et services de localisation imposés par Android ; les résultats mis en cache ou limités par le throttling Android ne doivent pas être présentés comme un nouveau scan physique ;
- les consultations de sources OSINT publiques se font uniquement en HTTPS et ne transmettent pas de données utilisateur à ces sources ;
- les journaux locaux ne sont exportables que par l'utilisateur, de manière bornée, via le fournisseur de fichiers sécurisé de l'application.

Les rôles et permissions Phone Core sont demandés au moment où la fonction correspondante est activée et restent révocables depuis Android. Leur présence dans le manifeste ne signifie pas qu'ils sont accordés automatiquement. Les fonctions dépendantes doivent rester limitées ou verrouillées lorsque le rôle, la permission, la SIM, le service système ou une autre condition Android requise manque.

Ces descriptions ne valent que pour le code actuellement présent dans le dépôt et doivent être revérifiées sur tout artefact réellement publié et sur appareil physique.

## Sécurité et confidentialité

Les contrôles de gouvernance, d'intégrité et d'isolation réduisent certains risques mais ne prouvent pas l'absence de vulnérabilité ni une conformité réglementaire générale. Les données sensibles ne doivent pas être introduites dans un environnement de test sans analyse préalable du traitement et des garanties applicables.

## Droits et conformité

Lorsqu'un déploiement traite des données à caractère personnel, l'exploitant doit déterminer son rôle, ses finalités, sa base juridique, ses obligations d'information, ses durées de conservation et les modalités d'exercice des droits conformément au cadre applicable.

La présence de cette politique ne constitue pas une certification RGPD, ANSSI, ISO 27001, SecNumCloud ou autre qualification.

## Contact et évolution

Toute modification substantielle du traitement des données ou ajout d'un service externe doit entraîner une révision de cette politique et une vérification de la documentation correspondante.

## Enrichissement Caller ID distant facultatif

L’enrichissement Wangiri/Spoofing est désactivé par défaut. Si l’utilisateur l’active explicitement dans les réglages de filtrage d’appels, Sentinel peut transmettre le numéro entrant normalisé, le pays destinataire et le statut de vérification réseau au moteur Sentinel après que la décision locale de filtrage a déjà été rendue. Ce résultat sert uniquement à afficher un score et des signaux complémentaires ; il ne remplace pas la décision locale. Le composant Android ne transmet pas le répertoire local avec cette requête.

## Contrôle d’exposition de mot de passe

Le contrôle Pwned Passwords est manuel. Le mot de passe est haché localement en SHA-1 ; seuls les cinq premiers caractères du hash sont envoyés au service de recherche par plage. Le suffixe complet est comparé localement et les résultats non correspondants ne sont pas conservés. Sentinel ne transmet ni le mot de passe ni son hash complet. Une absence de correspondance ne garantit pas qu’un mot de passe soit sûr.

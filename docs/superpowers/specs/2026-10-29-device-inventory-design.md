# Inventaire détaillé des appareils — conception proposée

Date : 2026-10-29. État : **proposition à approuver, aucune implémentation**.
Branche documentaire : `design/device-inventory-20261029`, issue de main
`935344423b2af2a4a57c58123f2c5c178b46ec03`.

## Intention et choix de l’utilisateur

L’utilisateur souhaite le maximum de détails utiles, des logos et une fiche
par appareil pour l’ensemble des surfaces : Wi-Fi, Bluetooth et appareils du
réseau local. Il a choisi l’approche 2 : **téléphone + connecteur de box
facultatif**, plutôt qu’un inventaire limité aux observations du téléphone.
Les écrans du build installé fonctionnent et affichent des résultats réels ;
le bouton Scanner recouvre des informations et doit être repositionné.

Le nom commercial et le modèle de la box ne sont pas connus. Le nom d’un SSID
observé ne suffit pas à identifier le matériel. Aucun identifiant ou mot de
passe ne doit être demandé avant de savoir qu’un connecteur compatible existe.

## Approches considérées

1. Téléphone seul : peu de configuration, découverte Android et locale ;
   certains appareils silencieux ne seront jamais visibles.
2. Téléphone + box facultative : même socle, complété par une liste de clients
   fournie par un routeur compatible et explicitement configuré. Plus riche,
   mais dépendant des possibilités de la box. **Choix utilisateur : 2**.

Les choix techniques suivants sont des propositions du concepteur, pas des
préférences attribuées à l’utilisateur : fonctionnement local par défaut,
Android natif Kotlin/Compose existant, aucune nouvelle dépendance payante,
aucun envoi de MAC, IP, noms ou identifiants à un service d’enrichissement.

## Périmètre livré et sens de « complet »

- Trois surfaces : Réseaux Wi-Fi, Appareils du réseau local, Bluetooth.
- Résumé filtrable, fiche détaillée, sources et fraîcheur de chaque information.
- Logos de marque lorsque la marque est suffisamment identifiée et ressource
  utilisable ; sinon icône de catégorie explicite, jamais faux logo.
- Inventaire local et historique borné, annotation personnelle, export choisi.
- Contrat et écran de configuration des connecteurs routeur, état incompatible
  honnête ; un premier adaptateur réel uniquement après identification du
  modèle, des capacités officielles et de la méthode d’accès de la box.

Le socle ne promet ni tous les modèles de box, ni une liste exhaustive de tous
les appareils. Un connecteur générique seul n’est pas un connecteur Livebox
fonctionnel. La qualification de l’approche complète comporte un essai avec
la box réelle compatible ; si elle ne fournit pas d’API utilisable, l’UI expose
la limite et le socle téléphone reste utilisable.

Hors périmètre : appairage automatique, commandes domotiques, contournement
routeur, capture de trafic, lecture de messages, exploitation de services,
scan Internet, localisation précise d’un appareil au RSSI, anti-traceur certifié,
remplacement du VPN/mesh, fusion globale des 85 PR ou changement du coffre.

## Sources et données disponibles

| Source | Informations à exploiter si présentes | Ce qui ne doit pas être déduit |
| --- | --- | --- |
| Résultats Wi-Fi Android | SSID/BSSID, RSSI, fréquence/bande/canal, capacités de sécurité, largeur de canal lorsque disponible, horodatage de scan | Liste des clients de chaque point d’accès, débit réel, modèle exact du routeur |
| Connexion Wi-Fi Android actuelle | Réseau actif, IP/préfixes du téléphone, passerelles/DNS et paramètres de liaison exposés par API | Liste exhaustive des autres clients, absence de risque |
| Annonces Bluetooth LE | Noms exposés, adresse/type lorsque connu, RSSI, identifiants de services, données constructeur bornées, TX power/connectabilité/PHY selon API et annonce | Marque du produit à partir du seul nom, distance exacte, batterie ou firmware non annoncés |
| Découverte Bluetooth classique Android | Nom et adresse exposés, classe/type, RSSI si fourni, état d’association, services déjà en cache | Exhaustivité des appareils non détectables, modèle exact ou batterie |
| Appareils Bluetooth associés / profils accessibles | Nom/alias autorisé, catégorie, association et état des profils que les API publiques permettent d’observer | Tout appareil associé est connecté ; tout appareil connecté diffuse en BLE |
| Découverte LAN | Services DNS-SD/mDNS, noms et TXT bornés, adresses et ports effectivement annoncés ; SSDP si activé | Tous les ports ouverts, système d’exploitation ou modèle exact depuis une simple IP |
| Box compatible | Clients et propriétés réellement renvoyés : nom, IP/MAC si fournis, liaison Wi-Fi/Ethernet, état et dernière présence, fabricant/modèle/firmware lorsqu’exposés | Une location DHCP ancienne prouve une présence actuelle ; privilèges absents |
| Dictionnaires embarqués | OUI publics lorsque pertinents, Bluetooth SIG Company IDs, noms de services et catégories | OUI ou Company ID identifie forcément la marque finale plutôt que le fournisseur du composant |
| Utilisateur | Surnom, pièce, type, confiance personnelle, marque confirmée | Une annotation constitue une mesure réseau ou réduit automatiquement un risque technique |

Détails non disponibles : champ « Non fourni par cet appareil » ou raison
permission/compatibilité. Pas de valeur inventée ni de pourcentage de confiance
arbitraire. Une identification probable montre les indices et éventuels conflits.
Les marques affichées restent distinctes du fournisseur d’un composant.

### Couverture Bluetooth classique et LE

La couverture complète inclut les appareils classiques détectables non associés,
les annonces LE et les appareils déjà associés. Choix du concepteur pour couvrir
la demande « tout », confirmé après la revue de conception. Découverte classique
et scan LE sont sérialisés, jamais concurrents : fenêtre classique 12 secondes
maximum, puis BLE 8 secondes, session totale 25 secondes maximum. Les sources
sont étiquetées séparément et aucun résultat ne suffit à prouver une connexion.
La découverte classique Android peut être refusée ou écourtée par le système ;
ces cas sont affichés avec la couverture partielle, pas comme absence d’appareils.
Annulation utilisateur ou changement de propriétaire arrête aussi startDiscovery
via cancelDiscovery. Les UUID déjà en cache peuvent être lus ; pas de requête
SDP supplémentaire ni de connexion GATT pour extraire plus de données. Une telle
extension nécessite une action distincte et ne fait pas partie du scan initial.
Les permissions sont demandées selon la version Android et la source réelle ;
pas d’API cachée ou de tentative de récupérer des identifiants protégés.

## Modèle de preuve et rapprochement

Chaque champ porte valeur, source, observedAt, statut (observé/déclaré/probable/
confirmé par l’utilisateur) et, si nécessaire, expiration. Conserver plusieurs
assertions contradictoires dans les détails ; ne pas écraser silencieusement.

États séparés : visible pendant ce scan, associé, connecté à un profil Android,
présent sur le LAN, connecté selon la box, anciennement observé. L’UI peut
montrer plusieurs badges. Un échec de scan ou une permission refusée ne
transforme pas un appareil en « disparu ».

- Pas de fusion par simple nom, préfixe MAC, IP réattribuable ou ressemblance.
- Adresse BLE privée/aléatoire : identité de session, sans rapprochement durable
  automatique entre rotations ; type d’adresse inconnu reste inconnu.
- Wi-Fi BSSID désigne un point d’accès/radio, pas un client de la box.
- Plusieurs radios d’une box ne sont regroupées que sur preuve du connecteur
  ou confirmation explicite de l’utilisateur.
- Clé locale pseudonyme par HMAC propre à l’installation ; pas de MAC brute
  dans les journaux, diagnostics de build ou index d’événements existant.
- Historique : première/dernière observation avec source ; un retrait est
  « non revu lors du dernier scan réussi », pas une preuve de déconnexion.

## Découverte LAN et limites de charge

Déclenchement explicite sur le réseau administré par l’utilisateur ; aucun
service de scan permanent ou au démarrage. Session annulable et liée au réseau
Wi-Fi sélectionné. Les sockets et requêtes routeur sont rattachées à ce réseau,
pas à un réseau mobile ou à une route VPN supposée équivalente.

1. Lecture des informations Android du réseau actuel.
2. DNS-SD/mDNS pendant une fenêtre bornée ; SSDP optionnel dans cette session.
3. Résolution limitée des services observés. Pas d’énumération de toutes les IP,
   ni de tous les ports, ni de l’espace IPv6. Un appareil sans annonce peut
   rester inconnu jusqu’à interrogation de la box.
4. Connecteur de box configuré, en lecture seule, s’il existe.

Proposition de limites initiales : session 15 s, 256 observations par source,
16 résolutions simultanées maximum, TXT total 8 Kio par appareil, annonce BLE
brute 1 Kio conservée au maximum en mémoire. SSDP : 3 s de réception dans la
session, 64 réponses, descriptions 64 Kio, 4 requêtes simultanées, délai 3 s.
Dédupliquer, arrêter les callbacks et fermer sockets/locks multicast à
l’annulation, au changement de réseau ou à la fermeture de l’écran propriétaire.
Une source tronquée est indiquée, pas présentée comme inventaire exhaustif.

Les URI de descriptions découvertes sont des entrées non fiables : adresse
locale numérique dans les préfixes du réseau sélectionné, pas de credentials
URI, redirection, résolution vers Internet ni rebinding DNS. XML sans entités
externes/DTD, profondeur et taille bornées. Noms/TXT non interprétés en HTML.
Une passerelle explicitement configurée peut être hors du préfixe client ;
elle exige une preuve de route locale, pas une simple IP privée quelconque.

Sur anciennes API NSD ne permettant pas le rattachement strict à un réseau,
le builder doit employer une découverte mDNS liée au réseau ou désactiver la
source lorsque sa portée n’est pas garantie. Ne pas annoncer un confinement
que l’API utilisée ne garantit pas. IPv6 link-local conserve son scope/interface.

Le scanner TCP AuthorizedLanProbe existant est réservé par la politique du
produit au mode laboratoire autorisé ; **il n’est ni utilisé ni assoupli pour
cet inventaire défensif**. Une interrogation de service annoncé reste limitée
à cette fonction, sans balayage des ports.

## Connecteur de box

Interface commune : compatibilité, connexion explicite, vérification, lecture
clients, état de fraîcheur, déconnexion et effacement des secrets. Implémentations
spécifiques au protocole officiel ou documenté et à la version réellement testée.
Aucun endpoint ou logo de box n’est choisi depuis le SSID seul.

- Adresse vérifiée sur le réseau local choisi ; pas de redirections externes.
- HTTPS avec validation normale ; si une box n’offre que HTTP, aucun envoi
  automatique d’identifiants, limitation exposée et décision spécifique avant
  activation de cet adaptateur. Aucun trust-all TLS ni bypass de certificat.
- Accès minimal, en lecture seule ; aucun changement de configuration réseau.
- Secrets chiffrés via AndroidKeyStore, alias distinct ; jamais dans les logs,
  exports, sauvegardes, télémétrie ou dépôt. Retrait du connecteur efface ses
  identifiants. Changement de réseau révoque la session avant nouvelle requête.
- Résultats capés à 1 000 clients, réponse 512 Kio, timeout 5 s, débit maximal
  un refresh utilisateur par 10 s ; expiration UI 60 s, historique explicitement daté.
- Une authentification expirée produit « Reconnexion nécessaire », pas zéro
  appareil. Les leases DHCP sont montrées séparément des connexions actives.

Question de configuration différée : modèle exact, version et capacités de la
box. Le plan doit contenir ce jalon, sans demander de mot de passe dans le chat.

## Logos et informations constructeur

Ressources embarquées et versionnées avec provenance/licence vérifiée, petite
sélection utile au départ et icônes génériques pour la couverture restante.
Pas de récupération d’image depuis une URL annoncée par un appareil : évite
l’exposition réseau et les ressources malveillantes. Aucune IA ne fabrique un
logo officiel. Base OUI/Company IDs embarquée bornée et révision connue ;
aucune requête distante transmettant les identifiants détectés.

Logo seulement pour une marque identifiée/confirmée ; une correspondance
fournisseur probable garde son libellé de source et ne remplace pas la marque.
Aucune base exhaustive ni identité parfaite n’est promise. L’utilisateur peut
corriger un nom/type/marque ; son annotation est étiquetée comme telle.

## Écrans

Trois onglets explicites : Wi-Fi, Réseau local, Bluetooth. Filtres sur l’état,
la source et la recherche de nom. Résumé du nombre réellement observé et heure
par source ; progression et annulation, état sans permission/offline distinct.

Carte courte : logo/icône, nom principal, catégorie, badges d’état, source,
signal si pertinent et dernière observation. Pas de nom inconnu automatiquement
classé menaçant. Détails en fiche : Identité, Connexion, Services, Historique,
Indices de risque. Champs techniques repliables, formats lisibles, copie locale
et export à la demande. Les raisons de risque existantes restent distinctes
et ne sont pas reclassées globalement dans ce chantier.

Le bouton Scanner ne flotte plus au-dessus du contenu : barre d’action dédiée
avec espace de contenu et insets adaptés, utilisable avec grosse police et
TalkBack. Aucune commande ou donnée masquée sur le S24+ observé. Le scanner ne
déclenche ni appairage, ni connexion à un service GATT, ni action domotique.

## Historique, stockage et confidentialité

Proposition : 30 jours, 1 000 appareils, 5 000 événements, limite stricte du
fichier 2 Mio ; éviction des plus anciens avec compte de troncature visible.
Les détails bruts de scan non nécessaires expirent après session ; seuls les
champs utiles normalisés sont conservés. Action « Effacer l’historique »
limitée à cet inventaire, jamais aux contacts, SMS, coffre ou clés existantes.

Stockage dédié sous noBackupFilesDir, JSON versionné chiffré avec clé Keystore
propre à la fonction ; écriture temporaire puis publication atomique, ancien
état conservé si l’écriture échoue. Pas de migration Room, de modification du
coffre qualifié, ni de dépendance à son application de test. Permissions et
stockage testés sur API24 minimale et API36 réelle. Corruption/clé absente :
indisponibilité visible et proposition de réinitialisation locale, sans perte
silencieuse ni fabrication de données.

Export JSON lisible avec sources, dates et limites. Mode expurgé par défaut
sans MAC/IP/noms personnels, partage via FileProvider à l’initiative utilisateur.
Un export complet exige un choix explicite et n’inclut jamais les credentials.

## Raccordement aux branches existantes

L’audit montre que main, #1699 et #1739 ne sont pas équivalents. #1699 contient
le registre d’intégration et wear-app, mais conserve un journal MMS non protégé
et un conflit de build Wear OS. #1739 contient les corrections MMS/téléphone,
mais pas le registre/wear-app de #1699. Les tests des branches ne s’additionnent
pas pour qualifier une intégration.

Cette conception part d’une branche documentaire issue de main, sans merge.
Avant le code, le plan choisira et fixera une base de travail précise après
comparaison des fichiers du scanner/écran. Les évolutions de l’inventaire
seront isolées ; pas de fusion globale des PR, ni recul sur MMS, Phone Core,
VPN/mesh ou coffre. Le correctif minimal de layout fait partie de la fonction.

## Architecture proposée

Observateurs Android et LAN → observations normalisées bornées → résolution
locale de preuves/identité → agrégateur → store dédié → ViewModel Compose.
Connecteurs routeur derrière la même interface de source, sans déduire un état
CONNECTED depuis une simple découverte. Observateurs annulables, une session
propriétaire, un epoch de réseau ; callbacks obsolètes refusés. Horloge monotone
pour délais et TTL de session, horloge civile pour dates affichées.

Les APIs réseau restent dans les adaptateurs, parseurs et réduction d’identité
sont testables en JVM. Dépendances nouvelles seulement si nécessaires et
explicitement revues ; réemploi des APIs Android/Kotlin existantes par défaut.
Les détails internes peuvent être décidés par le builder tant que ces contrats,
limites et sources ne sont pas affaiblis.

## Vérification et critères d’acceptation

- Tests des parseurs Wi-Fi/BLE/services, bornes, source absente, contenu malformé,
  conflits de marques, MAC aléatoires, IP réattribuées et preuve de connexion.
- Sessions : callbacks tardifs, annulation, changement de Wi-Fi, absence de
  réseau, permissions révoquées ; aucune requête routée hors du périmètre.
- Connecteurs : fixtures authentiques expurgées pour le modèle choisi, erreurs
  auth/TLS, réponse trop grande, délais, leases anciennes, annulation et secrets.
- Historique chiffré : corruptions, clé manquante, remplissage, erreur d’écriture,
  limites et export ; aucune migration destructive d’un autre module.
- UI : grandes polices, TalkBack, bouton ne recouvrant rien, états vides distincts,
  logo générique si identité inconnue, détails sans données inventées.
- Build, lint et tests de la base réellement modifiée ; tests instrumentés
  identifiés comme exécutés seulement avec sortie Android et nombres vérifiés.
- Essai S24+ : scans réels Wi-Fi/Bluetooth, changement de réseau, refus de
  permission, LAN avec un équipement connu, arrêt/relance et historique.
- Box : comparaison des clients affichés avec son administration pour le modèle
  compatible identifié ; sinon statut non pris en charge et réserve explicite.

Un APK compilé ne prouve pas la qualité de découverte, un test simulé ne prouve
pas la radio, et « aucun résultat » ne signifie pas « aucun appareil ».

## Jalons proposés

1. Modèle de preuves et fiches Wi-Fi/Bluetooth, layout corrigé.
2. Découverte LAN liée au réseau et agrégation sans faux rapprochements.
3. Historique chiffré, logos/dictionnaires locaux et export.
4. Connecteur de box identifié et réellement testé, puis qualification complète.

Ces jalons ne réduisent pas le périmètre « tout » ; ils permettent des livraisons
vérifiables. Aucun de ces jalons n’est déclaré implémenté par ce document.

## Revue de conception

Revue par un second agent Android : un point important relevé, couverture des
appareils Bluetooth classiques non associés. Résolu ci-dessus par leur inclusion
explicite avec découverte séquencée et bornée. Modèle de box reconnu comme jalon
de configuration différé et non comme hypothèse permettant d’inventer un adaptateur.

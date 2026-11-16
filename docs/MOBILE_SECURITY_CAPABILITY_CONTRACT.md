# Contrat de capacités — sécurité mobile Sentinel

Révision : 1 octobre 2026. Ce contrat traduit les exigences produit en responsabilités et preuves de sortie. Sentinel reste une application Android ; un futur OS ou produit de gestion de flotte serait un livrable distinct.

## Trois niveaux de réalisation

1. **Application Sentinel :** appels, SMS/MMS, diagnostic local, clés de l'application, client WireGuard et états explicites.
2. **Réglages Android / flotte administrée :** guide vers les réglages disponibles ; politiques Device Owner uniquement sur appareils effectivement enrôlés et sur API compatibles.
3. **OS et matériel :** capacités dépendant du système, du firmware et de l'appareil. Aucun bouton Sentinel ne doit annoncer leur activation sans API autorisée et preuve observée.

| Famille demandée | Fournisseur et périmètre réalisable | Critère de sortie / état actuel |
| --- | --- | --- |
| Chiffrement du stockage, séparation des profils, avant/après premier déverrouillage | Chiffrement par fichiers, Direct Boot et Keystore du système. Un écran verrouillé après usage ne retire pas nécessairement les clés déjà chargées. Les clés ne sont pas simplement toutes dérivées du PIN ; le matériel et le système participent à leur protection. | Relever la posture disponible et ses limites ; ne jamais annoncer une résistance absolue à l'extraction. Sentinel bénéficie des protections fournies par l'appareil, sans les installer. |
| Auto-reboot, PIN brouillé, PIN de contrainte, PIN après empreinte, modes USB-C | OS renforcé, constructeur ou certaines API de flotte ; pas de contrôle global depuis une application ordinaire. Une authentification propre à Sentinel ne remplace pas le verrouillage du téléphone. | Aucun délai 18 h, intervalle 10 min–72 h, mode USB ou effacement eSIM n'est annoncé comme fonction Sentinel. Un projet OS distinct exigerait implémentation, matériel ciblé et tests ; aucun effacement automatique n'est livré ici. |
| Sandbox, allocateur durci, marquage mémoire, JIT et code dynamique par application | Sandbox Android ; durcissement OS/runtime et prise en charge matérielle. Sentinel peut durcir ses propres composants et dépendances. | Manifest, composants exportés, permissions, dépendances, lint et tests vérifiés pour Sentinel ; le durcissement des autres applications reste hors capacités de l'application. |
| Réseau, capteurs, caméra/micro/localisation, fichiers et contacts choisis | Permissions Android ; permission Réseau/Capteurs et Storage/Contact Scopes spécifiques à certains OS. Les sélecteurs Android permettent des partages limités dans les parcours compatibles. | READ_CONTACTS n'est pas un sélecteur de contacts : il autorise l'accès au carnet du profil. Refus/révocation restent testés. Ne pas prétendre contrôler les permissions de toutes les applications. |
| VPN permanent, blocage hors VPN, MAC aléatoire, radio auto-off, LTE et DNS privé | VpnService/WireGuard pour le tunnel ; réglages Android ou gestion de flotte pour Always-On/lockdown et certaines politiques réseau. | Consentement explicite, gateway réelle, DNS/IPv4/IPv6/MTU/coupures testés. Private Mesh et VPN Internet sont des modes distincts ; une seule application VPN est active à la fois dans un profil Android. |
| Google Play sandboxé et installation alternative | Google Play sans privilèges et profils sélectionnés sont des capacités d'un OS adapté ; installation d'APK selon les règles Android. | Aucun remplacement système de Google Play n'est fourni par Sentinel. La distribution Sentinel exige artefact identifié, signature et checksum vérifiés. |
| Verified Boot, bootloader, OTA, rollback et navigateur système renforcé | OS, constructeur, firmware et navigateur. Une mise à jour APK n'est pas une mise à jour système. | Ne pas proposer de reverrouillage sans procédure propre au matériel et à l'OS. Aucun OTA système ni Chromium système Sentinel n'est implémenté par l'application. |
| Profils utilisateurs et arrêt d'un profil | OS et administration de profils selon les API disponibles. Les rôles et permissions Sentinel sont propres au profil d'installation. | Vérifier la portée par profil ; ne pas afficher des droits d'un autre profil comme acquis. Sentinel ne crée pas un cloisonnement système supplémentaire par simple installation. |

## Contrat UX/UI

- Indiquer le fournisseur réel, le prérequis manquant et l'action suivante réalisable.
- Distinguer « prérequis logiciels prêts », « prêt pour les essais », « preuves locales observées », « état dégradé » et « validation terrain terminée ».
- Après retour des réglages, relire les faits Android ; un choix sauvegardé n'est jamais une preuve de permission ou de protection.
- Ne pas confondre compteur de preuves et progression globale vers la distribution. Wi-Fi, Mesh et VPN ne complètent pas le certificat Phone Core.
- Aucun statut « protégé » pour un tunnel seulement configuré ou sans preuve de trafic ; aucune disponibilité universelle ni suppression garantie des points de défaillance sans matrice validée.

## Références et validation

- [Protocole terrain Phone Core](PHONE_CORE_PHYSICAL_VALIDATION.md).
- [Plan Mesh](MESH_DELIVERY_PLAN.md).
- [AOSP : chiffrement par fichiers](https://source.android.com/docs/security/features/encryption/file-based).
- [Android : Direct Boot](https://developer.android.com/privacy-and-security/direct-boot).
- [Android : VPN](https://developer.android.com/develop/connectivity/vpn).
- [GrapheneOS : fonctionnalités du système](https://grapheneos.org/features).

Ces références attribuent les responsabilités techniques ; elles n'attestent ni la présence de ces fonctions sur chaque appareil, ni des essais de Sentinel sur ces appareils.

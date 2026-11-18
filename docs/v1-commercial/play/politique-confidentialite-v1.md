# Politique de confidentialité — Sentinel Phone Core

**Brouillon au 18 novembre 2026. Ne pas publier tel quel.** Les champs `[[À COMPLÉTER]]` doivent être remplacés par l’éditeur. Cette page décrit uniquement le profil Android `-PsentinelCommercialV1=true`. Elle ne décrit pas la vitrine web, ni les modules exclus.

Version de code examinée : `versionCode` 10. Recette debug : paquet `com.sentinel.quantum.v1validation`, `versionName` `1.0.9-v1-validation`. Candidat Play : paquet `com.sentinel.quantum`, `versionName` `1.0.9`.

La page HTML de publication est [politique-confidentialite-v1.html](politique-confidentialite-v1.html).

---

# Politique de confidentialité

Dernière mise à jour : 18 novembre 2026.

Sentinel Phone Core est une application Android de téléphone, de filtrage local des appels et de messagerie SMS/MMS. Cette politique explique quelles données l’application utilise, où elles restent, et comment les effacer.

## Offre

Décision produit confirmée le 18 novembre 2026 pour le lancement public de cette version :

- l’application est gratuite ;
- aucun abonnement ;
- aucun achat intégré ;
- aucun paiement dans l’application ;
- aucune publicité prévue ;
- aucun compte utilisateur prévu.

Cette décision ne crée pas une société, une entreprise individuelle, ni un autre statut professionnel. Aucune immatriculation de ce type n’est déclarée dans ce document.

## Qui est responsable

La personne qui publie l’application est une personne physique. Les éléments ci-dessous reprennent ce qui a été établi le 4 novembre 2026 pour le compte Play déjà ouvert. Ils ne créent pas une société.

| Information | Valeur |
| --- | --- |
| Projet | Sentinel Quantum Vanguard Ai Pro |
| Application de cette politique | Sentinel Phone Core, profil Android V1. Le nom « Sentinel Quantum Pro » a été indiqué pour la fiche Console et n’a pas été relu dans la Console. |
| Paquet de publication | `com.sentinel.quantum` |
| Compte développeur | Personnel, déjà créé. Ne pas en ouvrir un second. |
| Statut | Personne physique, sans entreprise immatriculée. Aucun SIREN ni SIRET n’est déclaré. |
| Statut Play déclaré en novembre 2026 | Non-professionnel, non-trader. Ce statut n’est pas une autorisation permanente. [Déclaration trader](https://support.google.com/googleplay/android-developer/answer/17594752). |
| Éditeur | Thierry Naud |
| Localisation confirmée | Commune de Morne-à-l’Eau, Guadeloupe, France. Le code postal de cette commune est 97111. Aucune voie et aucun numéro de bâtiment n’ont été fournis. |
| Adresse de voie | [[À COMPLÉTER : numéro et nom de voie. Morne-à-l’Eau ne les remplace pas.]] |
| E-mail de contact, support et confidentialité | SentinelQuantumVanguardAiPro@gmail.com |
| Téléphone | [[À COMPLÉTER : numéro réellement utilisé, s’il doit être communiqué. Ne pas l’inventer.]] |
| Délégué à la protection des données | [[À COMPLÉTER : nom et contact, ou la mention exacte « non désigné »]] |

L’adresse `SentinelQuantumVanguardAiPro@gmail.com` est le contact confirmé. Elle reçoit les demandes de confidentialité et le support. Cette revue n’a pas envoyé de message et ne confirme pas que la boîte est surveillée. Gmail ne distingue pas les majuscules : cette écriture désigne la même boîte que la forme entièrement en minuscules.

Google indique qu’un compte personnel affiche le nom légal, le pays et l’e-mail développeur. L’adresse complète est affichée si l’application est monétisée. Cette version n’est pas monétisée. Le nom qui apparaîtra sur la fiche doit être le même dans cette politique. [Informations exigées pour un compte personnel](https://support.google.com/googleplay/android-developer/answer/13628312).

La personne qui publie est responsable des traitements qu’elle réalise. L’opérateur mobile reste responsable de l’acheminement des appels, SMS et MMS. Google est responsable de Google Play et d’Android pour les traitements qui lui sont propres.

## Ce que l’application ne fait pas

Cette version ne fournit pas de compte utilisateur, d’abonnement, d’achat, de paiement, de publicité, d’agent conversationnel, de VPN, de réseau mesh, de montre connectée, de studio voix, ni de scan Wi-Fi, Bluetooth ou de traceurs. Elle n’enregistre pas le son des appels. Elle n’envoie pas le répertoire, le journal d’appels, ni le contenu des SMS ou MMS à un serveur de la personne qui publie.

## Données utilisées sur le téléphone

Ces accès n’ont lieu qu’après une action de la personne et, pour les permissions restreintes, après que Sentinel est devenu le gestionnaire demandé. Ils servent à téléphoner, afficher les conversations, filtrer selon les règles locales et reconnaître un contact.

| Donnée | Quand | Où elle reste | Transmission à l’éditeur |
| --- | --- | --- | --- |
| Rôle Téléphone, puis numérotation et état des lignes SIM | Après choix de Sentinel comme application Téléphone, puis autorisation d’appel et de lecture de l’état du téléphone | Cadre téléphonique Android et opérateur de la ligne | Non |
| Journal d’appels | Après le rôle Téléphone et l’autorisation de lecture du journal | Affichage des appels récents dans le composeur. Lecture plafonnée. | Non |
| Rôle SMS, messages SMS et MMS | Après choix de Sentinel comme application SMS, puis autorisations d’envoi, de lecture et de réception | Fournisseur SMS/MMS d’Android. Les messages envoyés et les MMS passent par l’opérateur. | Non |
| Contacts | Autorisation distincte, refusable | Nom et organisation lus localement pour la fiche d’appel. L’édition d’une fiche ouvre l’application Contacts du téléphone. | Non |
| Filtrage des appels | Rôle de filtrage sur Android 10 et versions suivantes. Sur Android 7 à 9, le filtrage proposé passe par le rôle Téléphone. | Règles locales et historique des décisions dans le stockage privé de l’application | Non |
| Notifications | Autorisation de notification et, pour l’écran verrouillé, autorisation d’intent plein écran limitée aux appels entrants | Système Android | Non |

Le filtrage local ne donne pas accès au journal d’appels. L’historique des décisions conserve au plus 500 entrées : l’action, un motif codé, la source, l’heure, et une empreinte HMAC-SHA256 du numéro. La clé HMAC est une clé non exportable du coffre Android. Le numéro en clair n’est pas écrit dans cet historique. La personne peut effacer cet historique dans l’application. Les SMS du téléphone ne sont pas effacés par cette action.

Les numéros bloqués ou silencieux sont aussi enregistrés sous forme d’empreinte HMAC, dans la limite des règles prévues par l’application.

L’aperçu de l’expéditeur et du texte dans une notification SMS est désactivé par défaut. La personne peut l’activer dans les paramètres.

## Export choisi par la personne

Depuis les conversations, la personne peut préparer un fichier contenant jusqu’à 100 SMS : numéro, texte, date et type. Une confirmation précède le sélecteur Android. Le fichier n’est transmis qu’à l’application choisie. Les fichiers d’export de plus de 24 heures sont supprimés du cache.

Le journal local de diagnostic peut être affiché, effacé, ou partagé après une action explicite. Les entrées sont bornées et passées par un filtre de rédaction. Ce partage utilise aussi le sélecteur Android.

## Catalogues publics de numérotation

Les copies des plans de numérotation publiés par l’ARCEP (France) et la RTR (Autriche) servent à afficher le titulaire publié d’une tranche. Elles ne prouvent pas l’opérateur actuel après portabilité, ni l’identité de la personne qui appelle.

Deux accès réseau existent :

- une actualisation périodique, désactivée tant que la personne ne l’active pas dans les paramètres ;
- une recherche manuelle dans le composeur, qui peut télécharger la copie publique si elle n’est pas déjà sur le téléphone, même lorsque l’actualisation périodique est désactivée.

La requête est un téléchargement HTTPS, sans numéro de téléphone dans l’adresse :

- `https://sentinelquantumvanguardaipro.pages.dev/public/data/arcep-numbering.json`
- `https://sentinelquantumvanguardaipro.pages.dev/public/data/rtr-numbering.json`

L’adresse IP de connexion et les métadonnées techniques habituelles d’une requête HTTPS sont visibles par l’hébergeur de cette page. [[À COMPLÉTER : durée de conservation des journaux de connexion, accès de l’éditeur à ces journaux, et sous-traitant d’hébergement.]] Le numéro recherché est comparé sur le téléphone, après le téléchargement.

## Sécurité

La sauvegarde automatique Android et le transfert d’appareil vers appareil sont désactivés pour les données privées de l’application. Le trafic HTTP en clair est refusé. Les téléchargements de catalogues passent par HTTPS. Les appels, SMS et MMS empruntent le réseau de l’opérateur : l’application ne leur ajoute pas un chiffrement de bout en bout.

Les empreintes de numéros utilisent HMAC-SHA256 et une clé du coffre Android. La base locale des décisions de filtrage n’est pas un coffre chiffré supplémentaire : elle ne contient pas le numéro en clair. Les messages SMS et MMS restent dans le fournisseur Android, protégé par le modèle de sécurité du téléphone.

## Durées et suppression

| Donnée | Durée | Suppression |
| --- | --- | --- |
| Historique de filtrage | 500 décisions les plus récentes | Bouton d’effacement dans l’historique, ou désinstallation |
| Règles locales de blocage et de silence | Jusqu’au retrait par la personne ou la désinstallation | Réglages de filtrage, ou désinstallation |
| Journaux de diagnostic | Taille bornée, rotation locale | Effacement dans le journal, ou désinstallation |
| Préférences | Jusqu’à modification ou désinstallation | Paramètres Android de l’application, ou désinstallation |
| Copies ARCEP et RTR | Tant que la copie locale est conservée par l’application | Désinstallation. Le retrait de l’actualisation périodique stoppe les téléchargements planifiés. |
| SMS, MMS, contacts, journal d’appels Android | Durée choisie dans les applications et réglages du téléphone | Ces données ne sont pas un compte Sentinel. Les supprimer dans Sentinel, lorsqu’une action de suppression existe, ou via les réglages du téléphone. La désinstallation de Sentinel ne vide pas à elle seule le fournisseur SMS d’Android. |

Aucun compte n’est prévu pour cette version. La suppression d’un compte en ligne ne s’applique donc pas. Cette affirmation vient de la décision produit du 18 novembre 2026 et du graphe V1, qui n’ouvre pas d’écran de connexion. Elle doit être revérifiée sur l’AAB signé soumis : le code Auth0 reste compilé dans le module, même si ses activités sont retirées du manifeste V1.

Pour une demande portant sur une donnée que Thierry Naud détiendrait réellement, écrire à SentinelQuantumVanguardAiPro@gmail.com. Il ne peut pas, depuis un serveur, effacer le répertoire, les SMS ou le journal d’appels qui ne sont présents que sur le téléphone.

## Base du traitement

[[À COMPLÉTER : base juridique retenue par l’éditeur pour chaque traitement, par exemple exécution du service demandé pour la téléphonie et la messagerie, et consentement pour l’actualisation des catalogues. Ceci n’est pas un conseil juridique.]]

Les droits d’accès, de rectification, d’effacement, de limitation, d’opposition et de portabilité s’exercent auprès du contact ci-dessus lorsque l’éditeur dispose de la donnée. Une réclamation peut être adressée à l’autorité de protection des données compétente. [[À COMPLÉTER : autorité et pays.]]

## Destinataires

- la personne utilisatrice et les applications qu’elle choisit dans un sélecteur de partage ;
- l’opérateur mobile, pour les appels, SMS et MMS ;
- l’hébergeur de la page des catalogues publics, pour le téléchargement décrit plus haut ;
- Google, pour la distribution Play, les rôles Android et les services du téléphone, selon les conditions de Google et du fabricant.

Les données ne sont pas vendues. Aucune publicité et aucun identifiant publicitaire ne sont prévus. Aucun SDK publicitaire ni bibliothèque de facturation Play n’est déclaré dans `app/build.gradle`. Aucun outil de rapport de plantage n’est intégré dans les dépendances directes du module. Ces trois absences doivent être revérifiées sur l’AAB signé soumis.

## Modifications

Une modification substantielle de cette politique sera publiée sur cette page avec une nouvelle date. L’adresse publique à déclarer dans Play Console et dans l’application est : [[À COMPLÉTER : URL HTTPS publique, sans géoblocage, qui n’est pas un PDF.]]

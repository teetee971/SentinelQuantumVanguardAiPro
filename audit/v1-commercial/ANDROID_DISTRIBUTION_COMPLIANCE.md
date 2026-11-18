# Conformité de distribution Android · V1 commerciale

**État au 18 novembre 2026, Guadeloupe · NO-GO.** Les exigences publiques ci-dessous ont été relues. Le manifeste fusionné, les flux réels, les autorisations et le dossier Play doivent être vérifiés pour le candidat exact. Une approbation de Google Play requiert son propre retour.

## Rôles Android et données sensibles

| Surface V1 | Exigence avant acceptation | Preuve attendue |
| --- | --- | --- |
| Téléphone et historique | Demander le rôle Téléphone avant les accès protégés au journal d’appels. Vérifier le titulaire courant et les autorisations à la frontière de chaque accès. Android gère les appels d’urgence via son dialer préinstallé. Aucun appel d’urgence au banc d’essai. | Départ sans rôle, refus explicite, attribution puis révocation dans les réglages. Provider inaccessible hors rôle et retour explicite à l’ancien titulaire. |
| SMS/MMS | Demander le rôle SMS avant tout accès aux permissions SMS/MMS restreintes. Le produit doit assurer les fonctions essentielles annoncées du gestionnaire par défaut. L’usage cesse quand le rôle est retiré, y compris dans les chemins différés contrôlés par l’app. | Envoi, réception, rapports, deux SIM, retrait en cours de traitement et absence de nouvelle écriture/notification issue d’un traitement devenu non autorisé. Distinguer l’effet Android/opérateur déjà engagé. |
| Filtrage local d’appels | Prouver le rôle de filtrage dédié sur le S24+. Sur API 24–28, le code propose un parcours legacy distinct lié au dialer. Le filtrage n’accorde pas l’accès au journal d’appels. | Rôle autonome sur API 29+, décision de filtrage observée avec un numéro de test autorisé, refus ou retrait visibles. Certification distincte par API et OEM. |
| Contacts | Consulter le fournisseur après autorisation explicite, pour les usages locaux de l’appelant et du carnet. Traiter le refus et le retrait sans déduire une résolution d’identité. | Le parcours reste utilisable sans les contacts. Aucun nom, numéro ou identifiant de contact dans les journaux exportés. |
| Alertes et fenêtre plein écran | Séparer `POST_NOTIFICATIONS`, les réglages de canal et la capacité `canUseFullScreenIntent()` sur Android 14+. La fenêtre plein écran reste réservée aux appels entrants dans le périmètre V1. | Téléphone verrouillé/déverrouillé, plein écran accepté et refusé, notification normale lorsque disponible, révocation et commande de fin d’appel. |
| Export et traitement local | Inventorier les accès au fournisseur, les bases privées, les sauvegardes, les exports manuels, la conservation et les routes d’egress, y compris celles des SDK embarqués. | Tableau des données basé sur le binaire exact, test du sélecteur de partage, observation réseau avec données de test, cohérence avec la politique rendue publique. |

Le formulaire de déclaration Play est requis pour les permissions SMS/Call Log concernées. Elles sont réservées à la fonctionnalité essentielle approuvée, s’utilisent seulement après attribution du rôle et cessent d’être utilisées lorsqu’il est perdu. Le rôle de filtrage et les autorisations de notification ou de contacts ne substituent pas les rôles Téléphone/SMS. [Permissions SMS / Call Log](https://support.google.com/googleplay/android-developer/answer/10208820?hl=en) · [Gestionnaires par défaut](https://developer.android.com/guide/topics/permissions/default-handlers) · [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager) · [Dialer Android](https://developer.android.com/develop/connectivity/telecom/dialer-app) · [Plein écran Android 14](https://developer.android.com/about/versions/14/behavior-changes-14#secure-fsi)

## Paquet, signatures et mises à jour

- Relever l’ID, le `versionCode`, `minSdk`, `targetSdk`, les ABI, les composants exportés et leurs permissions sur les artefacts exacts. À partir du 31 août 2026, les nouvelles apps et mises à jour Play sur téléphone visent au moins Android 16 / API 36. La valeur 36 du source à la baseline satisfait ce seuil sur le papier. [Exigences API Google Play](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- Confirmer que les manifests APK et AAB excluent les surfaces expérimentales du périmètre V1 et que chaque export résiduel a une justification. Examiner services, receivers, providers, intent-filters des SDK fusionnés, politique réseau et sauvegarde. Une exclusion d’interface ou de manifeste ne prouve pas l’absence de dépendances, d’initialisation, d’actifs ou de binaires natifs. Examiner les flux du candidat `release` et la composition native avec des essais ciblés. [Manifeste Android](https://developer.android.com/guide/topics/manifest/manifest-intro)
- Vérifier l’ABI ARM64 et la compatibilité des appareils 64 bits à pages mémoire de 16 Kio, y compris alignement ZIP, segments ELF des bibliothèques conservées et exécution. Android documente la compatibilité Play pour les apps ciblant API 35+ et annonce une restriction de livraison des mises à jour incompatibles à compter du 1er février 2027. Un passage sur un S24+ à pages de 4 Kio ne démontre pas la compatibilité 16 Kio. [Compatibilité pages 16 Kio](https://developer.android.com/guide/practices/page-sizes)
- Le canal Play soumet un AAB signé avec la clé d’upload. Les APK effectivement distribués sont signés par Play App Signing avec la clé d’application. Contrôler le certificat de livraison et la continuité du paquet/version avant d’envisager un deuxième canal. Un APK debug isolé et un APK non signé restent des preuves de validation. [Signature Android](https://developer.android.com/studio/publish/app-signing) · [Guide release du dépôt](../../docs/PRODUCTION_RELEASE_GUIDE.md)
- Un canal direct reste hors de la V1 candidate. Toute étude ultérieure devrait tenir compte de la compatibilité de signature avec Play et de la vérification des développeurs Android selon les régions et appareils visés. [Vérification Android des développeurs](https://developer.android.com/developer-verification)

## Play Console, confidentialité et support

| Pièce | Statut de cette revue | Critère de sortie |
| --- | --- | --- |
| App Content et déclaration des permissions | BLOCKED · accès Play indisponible | Déclarer permissions et usage essentiel, fournir instructions d’accès à la revue, audience, contenu, publicités et autres déclarations applicables. Consigner le retour Play. |
| Data safety | BLOCKED · binaire de distribution et flux exacts à établir | Décrire la collecte/partage des données de l’app et de ses SDK. Les tracks fermés, ouverts et production exigent le formulaire. Une app exclusivement en test interne à Play bénéficie de l’exemption de cette rubrique. |
| Confidentialité | PARTIEL · brouillons source disponibles | Politique accessible depuis la fiche et l’app, URL publique active sans géoblocage, identité éditrice et contact exacts, données, finalités, conservation et suppression. Le cas de création de compte exige aussi la suppression depuis l’app et une ressource Web. Vérifier le périmètre réel de V1 avant de déclarer cette condition applicable ou non. |
| Fiche Play et promesses | BLOCKED · périmètre et captures du build final | Limiter la fiche aux parcours observés. Distinguer l’exclusion éventuelle du transport expérimental C11 des preuves publiées sur C11. Exiger une preuve de distribution et d’activation avant toute mention « disponible ». |
| Support | BLOCKED · adresse de support et accès opérationnel à confirmer | Adresse d’assistance surveillée, responsable d’incident, modèle de diagnostic expurgé et consignes pour rendre les rôles à l’app système. Tester le circuit avant publication. |
| Accès production Play | BLOCKED · type et ancienneté du compte non vérifiés | Pour un compte personnel créé après le 13 novembre 2023, la documentation actuelle exige au moins 12 testeurs inscrits 14 jours consécutifs en test fermé avant la demande d’accès production. Vérifier l’applicabilité dans la Console. |

[Préparer la revue Play](https://support.google.com/googleplay/android-developer/answer/9859455?hl=en) · [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en) · [Politique User Data](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en) · [Coordonnées de support](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en) · [Conditions des nouveaux comptes personnels](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)

La fiche V1 doit correspondre à l’artefact distribué et à ses services actifs. Le brouillon C11 omettait l’exception HTTP exacte de la boucle locale. Si cette exception est retirée du candidat V1, consigner sa suppression sur les manifests et ressources fusionnés. Les preuves et la formulation C11 restent applicables à C11. [Constats produit C11](../cycle11/PRODUCT_READINESS.md) · [Data Safety du dépôt](../../docs/PLAY-DATA-SAFETY.md)

## Brouillon de fiche Play Phone Core V1

**Brouillon de travail.** L’acceptation Android, l’autorisation des permissions et la voie de distribution restent des prérequis à toute soumission.

- Titre proposé · Sentinel Phone Core
- Description courte proposée · Appels, filtrage local et messagerie SMS/MMS sur Android.
- Périmètre de la fiche · Assistant de configuration des rôles Android, téléphonie, règles locales de filtrage, recherche et consultation du répertoire, messagerie SMS/MMS et paramètres de confidentialité. Les attributions de plages de numéros sont des données publiques de référence. Elles peuvent différer de l’opérateur actuel après portabilité.
- Conditions visibles · La téléphonie et la messagerie dépendent des rôles, des autorisations, d’une ligne active et des fonctionnalités prises en charge par Android et l’opérateur du téléphone. Le consentement à l’actualisation des catalogues publics est séparé.
- Captures à produire sur la release candidate · Assistant, clavier, appel actif, conversations, contacts, règles locales et paramètres. Employer uniquement des données de recette.
- Publication · Activer les textes et le téléchargement à l’issue du dossier GO. Le registre commercial reste en attente.

### Notice de confidentialité V1 à vérifier sur le candidat signé

Les accès au journal d’appels, aux contacts et à la messagerie servent aux écrans et actions choisis par l’utilisateur. Les listes et règles locales résident dans le stockage privé de l’application. Les SMS/MMS transitent par la pile téléphonie Android et l’opérateur de la ligne sélectionnée. Le sélecteur système ouvre les fichiers choisis. L’éditeur Contacts système ouvre la fiche existante sur action explicite. Le rythme et le compte de synchronisation des contacts sont réglés dans les applications Android de contacts. Les données de diagnostic exportées quittent le stockage privé selon la destination choisie par l’utilisateur.

La consultation des catalogues ARCEP/RTR utilise des données publiques téléchargées en HTTPS. L’utilisateur contrôle les notifications, leurs aperçus, les rôles et la suppression des journaux locaux adressables. La qualification doit vérifier ces contrôles et les flux des dépendances sur le binaire soumis. Les conditions de conservation, les droits applicables et l’identité du responsable doivent être confirmés par l’exploitant.

### Support et retrait des autorisations

Préparer un canal de support vérifié. Collecter seulement le modèle, la version Android, le SHA du binaire, la catégorie de panne et les étapes anonymisées. Tester la restauration des applications Téléphone/SMS système, la désactivation des aperçus et la reprise après permission révoquée en conservant les données de l’appareil. Réserver la suppression aux actions confirmées dans l’application. Relier chaque incident à un correctif installé avec la même identité de signature et un code de version supérieur. [Recette et retour arrière](S24_PHYSICAL_ACCEPTANCE.md)

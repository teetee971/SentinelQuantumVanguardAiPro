# Dossier Play · Sentinel Phone Core V1

**Statut : brouillon de travail, non soumis.** Date de revue : 18 novembre 2026. Profil Gradle `-PsentinelCommercialV1=true`.

Ce dossier sert à préparer la Play Console pour le Phone Core commercial. Il ne remplace pas une décision de l’éditeur, un avis juridique, ni le retour de Google Play. Aucune pièce n’a été déposée dans la Console pendant cette revue.

Décision produit du 18 novembre 2026 : lancement public gratuit, sans abonnement, sans achat intégré, sans paiement dans l’application, sans publicité et sans compte. Aucune société ni entreprise individuelle immatriculée n’est déclarée. Les nom, adresse et contacts de la personne restent à compléter. Ce dossier ne déclare pas la conformité acquise.

| Pièce | Fichier | Usage |
| --- | --- | --- |
| Politique de confidentialité V1 | [politique-confidentialite-v1.md](politique-confidentialite-v1.md) et [politique-confidentialite-v1.html](politique-confidentialite-v1.html) | Texte à publier sur une URL publique après remplacement des placeholders |
| Déclaration de permissions | [declaration-permissions-play.md](declaration-permissions-play.md) | Réponses proposées pour le Permissions Declaration Form |
| Data safety | [data-safety-v1.md](data-safety-v1.md) | Réponses proposées pour le formulaire Data safety |
| Divulgations visibles | [divulgations-visibles-v1.md](divulgations-visibles-v1.md) | Textes de divulgation et de consentement, avec ce qui est déjà dans l’application |
| Vérification de l’offre | [verification-offre-v1.md](verification-offre-v1.md) | Paiement, publicité, compte, fiche, permissions et deux écarts techniques |
| Compte développeur personnel | [compte-developpeur-personnel.md](compte-developpeur-personnel.md) | Obligations lues pour un compte personnel, sans supposer leur applicabilité |

## Identité du build examiné

| Élément | Valeur lue dans `native-android-app/app/build.gradle` |
| --- | --- |
| Propriété | `-PsentinelCommercialV1=true` |
| Paquet de recette debug | `com.sentinel.quantum.v1validation` |
| `versionName` de recette | `1.0.9-v1-validation` |
| `versionCode` | `10` |
| Paquet release, sans suffixe debug | `com.sentinel.quantum` |
| `versionName` release | `1.0.9` |
| `minSdk` / `targetSdk` | 24 / 36 |

Le paquet `v1validation` est l’APK debug de recette. Le canal Play prévu par le dossier V1 est l’AAB `com.sentinel.quantum`. Les deux partagent le même manifeste commercial. Ne pas déposer le paquet debug.

## Périmètre retenu, et périmètre exclu

Retenu : téléphone par défaut, filtrage local d’appels, contacts, historique d’appels, SMS/MMS, notifications d’appel, catalogues publics ARCEP et RTR.

Exclu du profil, et donc absent de ces pièces comme fonction livrée : agent IA C11, Qwen, VPN, Mesh, Wear, Studio voix, scanners Wi-Fi, Bluetooth et traceurs, veille OSINT, réputation d’appelant distante, WhatsApp, partage d’e-mail, compte Auth0.

## Ce qui existait déjà

Aucun dossier `store/` ni `legal/` n’est présent. Aucun brouillon nommé « Permissions Declaration Form » n’a été trouvé. Les textes ci-dessous couvrent un autre périmètre ou restent au stade de préparation.

| Chemin | Dernier commit | Date dans le texte | Ce qu’il couvre |
| --- | --- | --- | --- |
| `PRIVACY_POLICY.md` | `07392da4f`, 16 novembre 2026 | septembre 2026 | Dépôt entier : Phone Core, SMS par défaut, OSINT, Wi-Fi, Studio voix, WebRTC/LiveKit, réputation distante, contrôle de mot de passe. Pas le profil V1 isolé. |
| `public/privacy.html` | `07392da4f`, 16 novembre 2026 | septembre 2026 | Vitrine web. La section Android dit qu’aucun APK signé n’est annoncé. C’est la page ouverte par l’application V1. |
| `public/terms.html` | `07392da4f`, 16 novembre 2026 | non daté dans l’en-tête relu | CGU de la vitrine et du logiciel licencié. Le téléphone Android est décrit comme futur et gratuit lors d’une distribution vérifiée. |
| `public/legal.html` | `07392da4f`, 16 novembre 2026 | non repris ici | Mentions légales de la vitrine. |
| `public/cgv.html` | `07392da4f`, 16 novembre 2026 | non repris ici | Conditions de vente de la vitrine. |
| `docs/LEGAL.md` | `07392da4f`, 16 novembre 2026 | 3 septembre 2026 | Cadrage juridique du dépôt, pas une politique d’application. |
| `docs/COMPLIANCE.md` | `07392da4f`, 16 novembre 2026 | septembre 2026 | Référentiels du dépôt. Aucune certification n’y est revendiquée. |
| `docs/PLAY-DATA-SAFETY.md` | `07392da4f`, 16 novembre 2026 | non daté | Feuille interne du dépôt entier, y compris Studio voix et appels VoIP non commercialisables. |
| `docs/ANDROID_SMS_DEFAULT_MIGRATION.md` | `07392da4f`, 16 novembre 2026 | non repris ici | Migration vers `ROLE_SMS`. Exige une politique et une déclaration alignées, sans les rédiger. |
| `native-android-app/PLAY_STORE_LISTING.md` | `e609ca8d0`, 18 novembre 2026 | non daté | Fiche produit du code général. Elle cite encore Wi-Fi, Bluetooth, `RECORD_AUDIO` et le Studio voix. |
| `audit/v1-commercial/ANDROID_DISTRIBUTION_COMPLIANCE.md` | `8ea201987`, 18 novembre 2026 | 18 novembre 2026 | Exigences Play du profil V1, notice courte, fiche encore bloquée. Pas de réponses de formulaire. |
| `audit/v1-commercial/FEATURE_MATRIX.md` | même journée V1 | 18 novembre 2026 | Inclusion et exclusion des fonctions. |
| `audit/v1-commercial/validation/check_merged_manifest.py` | journée V1 | — | Contrat du manifeste fusionné attendu. |

Les CGU, CGV et mentions de la vitrine n’ont pas été réécrites : Play n’en fait pas la déclaration de permissions, et leurs conditions commerciales ne sont pas établies dans cette revue.

## Écart corrigé par ce dossier

Les politiques générales décrivent des traitements que le profil V1 coupe (réputation distante, Studio voix, WebRTC, scanners radio) et ne décrivent pas, comme politique d’application publiable, les rôles Téléphone, SMS et filtrage réellement livrés. Le lien intégré ouvre encore `public/privacy.html`. Les réponses de formulaire et les textes de divulgation manquaient.

Les bannières ajoutées en tête de `PRIVACY_POLICY.md` et `docs/PLAY-DATA-SAFETY.md` empêchent de recopier ces fichiers dans la Console V1. Leur contenu de dépôt est conservé.

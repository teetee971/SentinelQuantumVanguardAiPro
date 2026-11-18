# Vérification de l’offre · Sentinel Phone Core V1

**18 novembre 2026. Ce n’est pas une attestation de conformité.** Le manifeste fusionné de l’AAB signé et les parcours physiques ne sont pas rejoués ici. Il n’y a pas de SDK Android dans l’environnement de cette revue.

Décision produit confirmée le même jour : lancement public gratuit, sans abonnement, sans achat intégré, sans paiement dans l’application, sans publicité et sans compte. Aucune société ni entreprise individuelle immatriculée n’est déclarée.

## 1. Paiement, publicité, SDK publicitaire et compte

| Contrôle | Résultat sur les sources | Limite |
| --- | --- | --- |
| Bibliothèque Billing ou achat Play | Aucune occurrence dans les fichiers Gradle du module `native-android-app`. | Le binaire signé n’a pas été inspecté. |
| SDK publicitaire | Aucune dépendance `play-services-ads` ni nom usuel d’SDK publicitaire dans ces Gradle. | Même limite. |
| Écran de paiement ou de publicité dans le graphe V1 | Le graphe `CommercialPhoneCoreNavGraph` ouvre l’accueil, le téléphone, les SMS, les contacts, la recherche, le filtrage, les listes, l’historique et les paramètres V1. | Un code compilé mais hors graphe peut rester dans l’APK. |
| Compte | `MainActivity` utilise le graphe V1 quand `COMMERCIAL_PHONE_CORE_V1` est vrai. Ce graphe n’ouvre pas `SettingsScreen`, qui contient « Se connecter avec Auth0 ». Le manifeste V1 retire les activités Auth0. | Les classes Auth0 restent dans les sources communes et donc dans la compilation. À confirmer injoignables sur l’AAB. |
| Prix dans l’interface V1 | L’écran de recherche affiche le badge « Gratuit » pour tous les profils. | Ce badge ne prouve pas le prix Play. |

Réponses proposées, non soumises : annonces = non ; fonctions financières = aucune ; création de compte = non.

## 2. Fiche Google Play et fonctions livrées

Le brouillon de `audit/v1-commercial/ANDROID_DISTRIBUTION_COMPLIANCE.md` décrit l’assistant, les appels, le filtrage local, les contacts, les SMS/MMS et les paramètres. Il inclut maintenant le prix gratuit et l’absence de publicité et de compte.

Il ne doit pas reprendre `native-android-app/PLAY_STORE_LISTING.md`, qui documente le profil élargi, ni `public/terms.html`, `public/cgv.html` et `public/pricing.html`, qui décrivent encore des offres payantes de la vitrine. Ces pages n’ont pas été réécrites.

La fiche reste bloquée tant que les captures du binaire final et le nom de la personne ne sont pas fournis.

## 3. Permissions SMS, MMS, appels et contacts

Relecture inchangée sur le fond, rappelée pour cette décision :

- `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, `RECEIVE_MMS` et `RECEIVE_WAP_PUSH` : gestionnaire SMS par défaut. Le bouton de permission runtime n’est proposé qu’après le rôle.
- `READ_CALL_LOG` : gestionnaire Téléphone par défaut, pour les récents. Le filtrage utilise un rôle distinct et ne justifie pas cette permission.
- `CALL_PHONE` et `READ_PHONE_STATE` : placer l’appel et choisir la SIM après le rôle concerné.
- `READ_CONTACTS` : lecture locale du nom et de l’organisation, refusable.
- Aucune de ces permissions ne sert un paiement, une publicité ou un compte.

Le formulaire n’est pas soumis. L’acceptation de Google reste ouverte.

## 4. Politique et Data safety

La politique et le formulaire Data safety décrivent l’offre gratuite, l’absence de publicité et l’absence de compte. Le nom légal, le pays, l’adresse, les e-mails, le téléphone et le délégué à la protection des données restent des champs vides. La base juridique aussi.

L’application ouvre encore `https://sentinelquantumvanguardaipro.pages.dev/public/privacy.html`, qui ne contient pas cette politique.

## 5. Deux écarts techniques

Ils sont toujours ouverts. Cette revue ne modifie pas le code.

| Écart | Preuve relue | Effet |
| --- | --- | --- |
| Lien de confidentialité | `CommercialPhoneCoreScreens.kt` lignes 134-137 ouvrent `public/privacy.html`. | La politique User Data n’est pas satisfaite tant que l’URL publiée et le bouton ne montrent pas la politique V1. |
| Téléchargement des catalogues | L’interrupteur ne commande que WorkManager. `SentinelDialerActivity.lookup()` et, sans geste de recherche, `CallerIdActivity` lignes 172-179 appellent `ArcepDirectoryClient.lookup()`, qui télécharge la copie si elle n’est pas déjà en mémoire. | Le premier appel français affiché peut provoquer un GET sans le consentement de l’interrupteur. |

## 6. Compte développeur personnel

Les obligations lues sont dans [compte-developpeur-personnel.md](compte-developpeur-personnel.md). La date de création du compte, le pays et le fait que les vérifications soient déjà faites restent à compléter. Un compte organisation n’est pas décrit comme disponible : aucune organisation immatriculée n’est déclarée.

## Ce qui reste hors conformité acquise

- manifeste fusionné de l’AAB signé ;
- relevé réseau du premier lancement ;
- recette physique des rôles, SMS, MMS, appels et contacts ;
- retour de la Play Console.

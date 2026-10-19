# Sentinel D.1 — signature persistante depuis Android (Termux)

Ce guide concerne uniquement l'APK isolé `com.sentinel.quantum.d1test`, jamais la signature de production.

## Situation initiale

L'APK D.1 déjà installé a pour certificat SHA-256 :
`7b5d350b33dc06c8d6c2057fd8e8957e8acf161c0888af63f5a2115ec59c7e8d`.

Le build Codemagic n° 9 utilise :
`2b305a42cdf4929d88ccfcf2b698903748b0432332b1edc7cb6a66a5b2a69e67`.

Ces certificats différents **ne permettent pas une mise à jour en place**. Une nouvelle clé persistante ne récupère ni les données ni les autorisations d'une ancienne application. Ne jamais supprimer l'installation existante avant d'avoir choisi une stratégie de migration/backup sûre.

## Créer une clé de qualification sur un appareil contrôlé

Dans Termux, utiliser une installation Java avec `keytool` disponible (p. ex. `pkg install openjdk-17` si ce paquet est accessible). Éviter l'historique shell pour les secrets.

```sh
umask 077
mkdir -p "$HOME/sentinel-signing"
keytool -genkeypair -v \
  -keystore "$HOME/sentinel-signing/sentinel-d1-qualification.jks" \
  -alias sentinel-d1 \
  -keyalg RSA -keysize 3072 -validity 3650 \
  -storetype PKCS12 \
  -dname "CN=Sentinel D1 Qualification, O=Sentinel"
```

Saisir les mots de passe dans les invites de `keytool` ; ne jamais les publier. Pour un PKCS12, utiliser le même mot de passe pour le magasin et la clé si l'outil le demande.

Calculer l'empreinte publique :

```sh
keytool -list -v \
  -keystore "$HOME/sentinel-signing/sentinel-d1-qualification.jks" \
  -alias sentinel-d1
```

Dans Termux : `base64 -w 0 "$HOME/sentinel-signing/sentinel-d1-qualification.jks"` produit la valeur de `SENTINEL_D1_KEYSTORE_B64` ; copier celle-ci **directement** dans un secret Codemagic, jamais dans GitHub, une capture ou un message. Éviter tout journal ou transfert intermédiaire. Prévoir une sauvegarde chiffrée du fichier JKS et des identifiants, hors du téléphone.

## Secrets Codemagic

Dans les paramètres de l'application Codemagic, ajouter des variables chiffrées réservées au workflow :

- `SENTINEL_D1_KEYSTORE_B64` : contenu Base64 du fichier privé JKS
- `SENTINEL_D1_KEYSTORE_PASSWORD` : mot de passe du magasin
- `SENTINEL_D1_KEY_ALIAS` : `sentinel-d1`
- `SENTINEL_D1_KEY_PASSWORD` : mot de passe de la clé
- `SENTINEL_D1_EXPECTED_CERT_SHA256` : valeur publique SHA-256 affichée par `keytool`

Masquer les valeurs confidentielles et limiter l'accès aux branches de qualification lorsque Codemagic le permet. La valeur SHA-256 est publique, mais les autres restent sensibles.

Le pipeline stoppe si le JKS est absent, si son certificat diffère de l'empreinte attendue, ou si l'APK construit présente une autre signature. Une compilation réussie **ne certifie pas** la sécurité Play Protect ni l'installation effective.

## Vérification et migration

L'ancienne application `com.sentinel.quantum.d1test` reste associée à son ancien certificat. Tant que la clé privée précédente n'est pas disponible, une installation parallèle sous un **autre applicationId de qualification** permet de conserver l'ancienne application sans désinstallation ; cette solution exige une modification dédiée de Gradle, des gardes APK et des URI d'authentification, puis une vérification de sécurité. Ne pas improviser un changement d'identifiant dans la pipeline.

Les rôles Android Téléphone/SMS doivent demeurer désactivés pour l'édition isolée, et le comportement sur Galaxy S24+ doit être testé après compilation.

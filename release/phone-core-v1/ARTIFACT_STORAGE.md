# Récupération et stockage des binaires

Les trois binaires existent dans ce dossier ; tailles, SHA-256 et chemins réels : `ARTIFACTS.json`. Ils ne sont pas committés dans Git. Les liens de fichiers du workspace fournis dans la conversation correspondent aux fichiers effectivement vérifiés, disponibles tant que ce workspace est conservé.

## Stockage distant tenté, non disponible

Un brouillon de release GitHub a été créé :
https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/tag/untagged-749c39c7074ebd9991bc

Tag préparé : `phone-core-v1-qualification-20261029-846ed793`. **Ce brouillon est vide : ce n’est pas un lien de récupération de l’APK.**

La commande d’upload a été réellement exécutée ; retour **1 / HTTP 401 Bad credentials** sur uploads.github.com (journal `logs/artifact-upload.log`). L’identité disponible permet l’API GitHub mais pas cet upload binaire. Aucun token n’a été lu, extrait, demandé dans la conversation ni remplacé ; aucun contournement de l’authentification n’est tenté. `REMOTE_ARTIFACTS.json` atteste la liste vide à la vérification. Aucun lien de téléchargement distant n’est inventé dans `ARTIFACTS.json` (`download_url: null`).

## Dernière action propriétaire pour un lien permanent

Depuis sa machine, après téléchargement du dossier local, utiliser son installation GitHub CLI déjà authentifiée avec accès au dépôt. Ne pas transmettre de token au chat. Exécuter depuis la racine du dépôt :

```bash
gh release upload phone-core-v1-qualification-20261029-846ed793 \
  release/phone-core-v1/Sentinel-PhoneCore-V1-debug.apk \
  release/phone-core-v1/Sentinel-PhoneCore-V1-androidTest.apk \
  release/phone-core-v1/Sentinel-PhoneCore-V1-release-unsigned.aab \
  release/phone-core-v1/SHA256SUMS.txt \
  release/phone-core-v1/Sentinel-PhoneCore-V1-delivery.zip \
  --repo teetee971/SentinelQuantumVanguardAiPro
# Vérifier ensuite le téléchargement dans un dossier neuf :
gh release download phone-core-v1-qualification-20261029-846ed793 \
  --repo teetee971/SentinelQuantumVanguardAiPro --dir ./verification-telechargement
cd verification-telechargement
sha256sum -c SHA256SUMS.txt
```

Le brouillon doit rester non publié tant que la qualification n’est pas soldée. Ne pas lancer de workflow Actions ni créer un job Codemagic payant pour transférer les fichiers. Alternative : le stockage d’artefacts déjà autorisé de l’équipe, puis vérifier un téléchargement par empreinte ; aucune connexion de stockage alternative n’est disponible ici.

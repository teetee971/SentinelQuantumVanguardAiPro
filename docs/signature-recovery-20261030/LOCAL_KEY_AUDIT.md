# Audit local de la piste Termux / Ubuntu proot

Lecture seule : aucune compilation, signature, installation, génération ou copie de clé. Le script n'imprime que le certificat public et l'état d'une entrée. Son rapport est créé exclusivement : choisir un nouveau nom si le JSON existe déjà ; il ne l'écrasera pas.

Télécharger uniquement le script public depuis le commit de code vérifié (pas de checkout des anciens scripts Termux) :

```bash
curl -fL 'https://raw.githubusercontent.com/teetee971/SentinelQuantumVanguardAiPro/50c66852c13d6ed4354d2a818277a7241ef45c4b/scripts/audit-sentinel-keystore.py' -o "$HOME/sentinel-keystore-audit.py"
```

Puis lancer Python et keytool dans l'Ubuntu existant pour éviter le crash natif ProtoLogNative observé dans Termux :

```bash
proot-distro login ubuntu -- bash -c '
set -eu
tool="$(command -v keytool)" || { echo "BLOQUE : keytool absent dans Ubuntu"; exit 2; }
python3 "$1" --keytool "$tool" --keystore /root/.android/debug.keystore --alias androiddebugkey --debug-default-password --output "$2"
' _ "$HOME/sentinel-keystore-audit.py" "$HOME/storage/downloads/Sentinel-keystore-public-audit-01.json"
```

Le flag --debug-default-password essaie uniquement le mot de passe conventionnel du store debug Android, pas une recherche/brute force. S'il échoue, ne pas recréer le store. Pour un store personnalisé, son propriétaire peut fournir le mot de passe dans une variable locale et utiliser --password-env NOM_DE_VARIABLE ; ne jamais mettre sa valeur dans la commande, le journal ou la conversation.

Résultats :

- MATCH_PRIVATE_ENTRY/code0 : certificat6891 attendu et entrée privée présente ; signature réelle encore nécessaire.
- MISMATCH_OR_NO_PRIVATE_ENTRY/code4 : ne pas signer ni installer avec cette entrée.
- UNVERIFIED/code2 : store/alias/outil/accès non établi ; aucune déduction que la clé est perdue.

Si le store Ubuntu est absent, seconde piste : le store Termux existant `$HOME/.android/debug.keystore`. Il peut être lu depuis Ubuntu au chemin `/data/data/com.termux/files/home/.android/debug.keystore` avec le même script et un autre nom de rapport (02). Aucun déplacement du fichier. D'autres backups sécurisés ou le groupe Codemagic doivent être examinés par leur propriétaire de la même façon, uniquement avec une empreinte publique en retour.

Envoyer seulement le contenu du JSON public. Aucun secret ni keystore à transmettre. Si aucun proot/keytool n'est disponible, indiquer cette limite ; ne pas installer de nouvel outil automatiquement dans cette procédure.

# Sentinel Local AI Lab v1

Laboratoire indépendant pour qualifier des moteurs IA locaux avant toute
intégration Kotlin. Ce répertoire ne fait pas partie du chemin de production et
ne lit ni SMS, ni MMS, ni contacts, ni appels, ni clés Sentinel.

## État initial

- Runtime prioritaire : `llama.cpp` avec GGUF.
- Commit épinglé : `b79696f1f556cb30b0c184838115007d929baca8`.
- Modèles candidats : Qwen2.5 1.5B, Qwen2.5 3B et SmolLM2 1.7B.
- Poids non téléchargés.
- Manifestes candidats : `UNVERIFIED` et volontairement refusés.
- Aucun résultat Galaxy S24+ n'est présent dans ce dépôt.

Un manifeste ne devient utilisable que lorsque la révision exacte, le fichier,
la taille, le SHA-256, la licence, le tokenizer et les conditions commerciales
sont vérifiés. Le laboratoire refuse tout manifeste `UNVERIFIED`.

## Tests locaux

Depuis la racine du dépôt :

```bash
python3 -m unittest discover -s research/local-ai-lab/tests -v
```

Les tests n'exécutent pas de modèle et ne téléchargent rien.

## Préparer Termux

Dans Termux, sans root :

```bash
pkg update
pkg install git cmake clang make sha256sum
research/local-ai-lab/scripts/check_termux.sh
```

Le contrôle exige ARM64. Il ne vérifie pas la présence d'un GPU ou d'un NPU.

## Compiler llama.cpp

Obtenir séparément une copie du dépôt officiel au commit épinglé, après
autorisation, puis vérifier son identité :

```bash
git clone https://github.com/ggml-org/llama.cpp.git "$HOME/src/llama.cpp"
git -C "$HOME/src/llama.cpp" fetch --depth=1 origin b79696f1f556cb30b0c184838115007d929baca8
git -C "$HOME/src/llama.cpp" checkout --detach b79696f1f556cb30b0c184838115007d929baca8
research/local-ai-lab/scripts/build_llama_termux.sh "$HOME/src/llama.cpp"
```

La commande de build ne télécharge rien et ne configure aucun serveur réseau.
Pour une compilation Android JNI ultérieure, utiliser séparément le NDK et le
chemin Android officiel de llama.cpp ; ce script ne qualifie que Termux natif.

## Enregistrer un modèle après autorisation

Ne pas remplir les champs avec des valeurs supposées. Après obtention d'un
fichier exact et de sa provenance :

```bash
python3 research/local-ai-lab/scripts/validate_manifest.py \
  research/local-ai-lab/manifests/<manifest>.json \
  --model /chemin/vers/model.gguf
```

Un manifeste `VERIFIED` est requis avant tout benchmark.

## Benchmark

Le benchmark exige au moins cinq exécutions, utilise `shell=False`, ne lance
aucun outil du modèle et ne persiste jamais le texte généré :

```bash
python3 research/local-ai-lab/scripts/benchmark.py \
  --runtime "$HOME/src/llama.cpp/build-sentinel-termux/bin/llama-cli" \
  --manifest research/local-ai-lab/manifests/<manifest>.json \
  --model /chemin/vers/model.gguf \
  --prompt 'Analyse ce texte synthétique et retourne un JSON borné.' \
  --repeats 5 \
  --output research/local-ai-lab/reports/s24-<model>.json
```

Les champs appareil, batterie et température restent `NOT_CAPTURED` tant que
le lancement n'est pas fait depuis le Galaxy S24+ et complété par une collecte
ADB/Termux contrôlée. Le benchmark ne constitue pas une mesure d'intégration
Android Kotlin.

## Limites

- Pas de téléchargement automatique de modèle.
- Pas de serveur `llama-server`.
- Pas d'accès réseau demandé au moteur.
- Pas de privilège Android.
- Pas de mesure physique dans l'environnement Codex.
- Pas de conclusion sur le NPU avant lecture des propriétés du téléphone et
  test réel du delegate/runtime.

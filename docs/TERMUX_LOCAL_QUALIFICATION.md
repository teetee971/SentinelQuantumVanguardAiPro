# Qualification gratuite Sentinel via Termux (Samsung S24+)

**Statut : procédure préparée, non exécutée sur le téléphone pour les commits actuels.**

L'objectif est de compiler **séparément** les PR #1718, #1719 et #1720 avec le script `scripts/termux-network-qualify.sh`, sans déclencher GitHub Actions, sans ADB et sans installer d'APK.

## Prérequis réutilisés
- Termux avec `git`, `bash`, `proot-distro` et Ubuntu.
- Ubuntu : JDK 17, Android SDK 37, Gradle via wrapper.
- AAPT2 compatible via `$HOME/sentinel-aapt2-amd64/gradle-wrapper/aapt2`.
- Chemin SQLite natif dans Ubuntu : `/root/sentinel-sqlite-linux`.
- Prévoir stockage et batterie suffisants ; lancer manuellement, jamais en boucle horaire sur batterie.

## Exécution par PR et SHA

Depuis Termux, dans un répertoire de travail dédié :

```bash
cd "$HOME/SentinelQuantumVanguardAiPro"
git fetch origin docs/network-center-beyond-wifiman-20261020
git show origin/docs/network-center-beyond-wifiman-20261020:scripts/termux-network-qualify.sh > "$HOME/sentinel-termux-qualify.sh"
bash -n "$HOME/sentinel-termux-qualify.sh"
# Ne lancer que si la vérification de syntaxe réussit.
```

**Attention :** la branche du dashboard doit être récupérée depuis la PR #1718 et remplacée par sa vraie référence Git, pas par une valeur supposée. Pour éviter les erreurs de frappe, la méthode recommandée est d'utiliser directement une référence PR GitHub :

```bash
bash "$HOME/sentinel-termux-qualify.sh" "$HOME/sentinel-qa-d1" "refs/pull/1718/head" "915eccfcb0112a98c12ca7cfdceb40e629a18cf7"
bash "$HOME/sentinel-termux-qualify.sh" "$HOME/sentinel-qa-bt" "refs/pull/1719/head" "5624aa4f618df5935aa1824735624936af96e25e"
```

Pour #1720, actualiser son SHA depuis GitHub **juste avant** d'exécuter le script, car il inclut lui-même des modifications du script.

## Critères de preuve
- `bash -n` sur le script.
- `compileDebugKotlin`, `testDebugUnitTest`, `assembleDebug` : codes retour 0.
- Journal de build et SHA de l'APK, conservés localement.
- Un résultat pour chaque SHA et branche, ne jamais réutiliser les résultats d'une autre PR.
- APK produit : **debug uniquement**. Ne pas l'installer sur Sentinel de production.
- Une PR ne devient pas verte automatiquement sur GitHub. Les règles de fusion restent actives et l'échec des runners doit être résolu ou remplacé par une procédure de validation explicitement approuvée.

## Limites
Les tests unitaires ne qualifient ni les permissions réelles Android, ni le Phone Core, ni une liaison Watch6, ni l'installabilité d'un APK signé production. Aucune signature ni suppression de données n'est faite.

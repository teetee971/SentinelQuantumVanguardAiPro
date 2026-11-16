# Feuille de route réaliste — Sentinel Quantum Vanguard AI Pro

**Révision :** 1 octobre 2026  
**Statut :** plan de finalisation consolidé ; validation terrain et release à démontrer

La référence détaillée est [docs/ROADMAP.md](docs/ROADMAP.md). La version publique est [public/roadmap.html](public/roadmap.html). Ces documents distinguent code présent, contrôles exécutés, infrastructure requise et preuve physique.

## Ordre de réalisation

1. **Clôturer le logiciel Phone Core v5.** Examiner et finaliser la PR #1497, corriger le nom de test obsolète et résoudre la divergence du contrôle Android. Exiger les contrôles requis réussis sur le dernier commit avant intégration.
2. **Prouver Phone Core sur appareil.** Obtenir les 14 preuves de l’installation courante ; vérifier aussi multi-SIM, variantes Android/constructeur, notifications, retrait des rôles/permissions et réversibilité. Le scanner Wi-Fi reste entièrement hors certificat.
3. **Préparer la distribution signée.** Suivre [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) et [RELEASE_STATUS.md](RELEASE_STATUS.md), vérifier APK/AAB, signatures, SHA-256, SBOM et preuves de release. Décider la stratégie Play/canal direct avant distribution.
4. **Valider le diagnostic Wi-Fi séparément.** Documenter scan frais, cache, permissions, localisation et throttling sur appareil, sans impact sur le compteur Phone Core 14/14.
5. **Rendre le VPN démontrable.** Une passerelle réelle et les essais tunnel/DNS/IPv4/IPv6/MTU/coupures sont requis avant toute disponibilité revendiquée.
6. **Étendre les programmes selon leurs preuves.** Signature Ed25519 de production pour la veille ; périmètre, sources autorisées et critères de sortie explicites pour les autres modules. CTEM attend la validation physique Phone Core.

## Maintenance transversale

Maintenir `native-android-app/` comme unique source Android, le pinning SHA des Actions, l’isolation Sentinel, les tests de gouvernance et le fuzzing autorisé. Vérifier le frontend et ses liens, l’interface mobile et les contrôles réellement utilisables. Ne jamais affaiblir un gate pour obtenir un résultat vert.

## État des blocages

Les runners exécutent désormais les workflows : l’ancien blocage général des runners n’est plus le jalon courant. Au commit `9f7103d227f3a5bd8ca0afbdcd1019dffe0e262a` de la PR #1435, 17 contrôles sont réussis et un contrôle Android reste en échec malgré un build natif ensuite réussi. Cette divergence reste à examiner. Les preuves physiques, la release signée et la passerelle VPN ne sont pas attestées par cette revue.

## Critère de clôture

Chaque jalon conserve le commit, la date, l’environnement, le scénario, le résultat observé et les liens vers les preuves. Il est terminé après examen de ces éléments, jamais parce qu’une interface ou un workflow existe.

Aucun ancien arbre Android, artefact APK committé, secret, keystore, pipeline supprimé ou dépendance d’un autre projet ne doit être réintroduit. Aucune promesse de production, conformité ou sécurité absolue sans preuve actuelle.

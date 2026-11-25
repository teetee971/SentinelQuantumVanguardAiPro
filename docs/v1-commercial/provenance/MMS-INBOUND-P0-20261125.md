# MMS entrant — correctif P0 du 2026-11-25

Statut : **NO-GO commercial** et **NO-GO installation V26**. `versionCode` inchangé (10). Aucune exécution Android, aucun APK, aucune ouverture de la base du S24+.

Référence précédente : `3da5f9ab22a0b7407591e61857ee1f0869832eab`.

## Ce qui change

- Un journal absent ou illisible ne renvoie plus `TERMINAL` tant que le staging contient des octets. Le brut illisible est copié sous `quarantine.` puis, après deux observations stables au temps monotone, copié dans l'inbox privée. Le staging n'est pas effacé par ce chemin.
- Un fichier vide n'est plus détruit parce que l'horloge murale a sauté. L'échéance vide utilise `elapsedRealtime` sur 24 h.
- La stabilité du staging ne compare plus `currentTimeMillis` et `lastModified`.
- Les lignes indécodables, en échec de transport, ou déjà diagnostiquées (`private_miss_count` ≥ 8) ne comptent plus dans le quota de 64.
- `mms-inbox/retention-v1` épingle la copie privée tant qu'elle n'est pas projetée. Le 51e MMS ne détruit pas ces copies. `releaseProjected` ne court qu'après un `ProjectResult.Ready`.
- Le WAP direct passe par cette épingle avant la projection. Le redémarrage rejoue les entrées qui ont encore un abonnement. Une quarantaine retire l'abonnement et garde le fichier.
- `confirmDurableCopy` libère l'épingle mémoire si la relecture échoue.

## Suite du même jour

L'épingle `retention-v1` est remplacée en trois noms (`tmp`, fichier vivant, `.bak`) sans `java.nio.file`, donc compatible `minSdk` 24. Si le nouveau nom n'est pas pris, l'ancien est remis. Une lecture restaure le `.bak` si le fichier vivant manque. Un `retain` sans abonnement ne retire pas un abonnement déjà écrit. `deleteInternal` refuse d'unlink un staging non vide tant qu'une copie privée de même empreinte n'a pas été relue, sauf échec de transport déjà journalisé.

## Non qualifié

Pas de suite instrumentée. Pas de téléphone. L'horloge, le provider Android et la pression cache ne sont pas prouvés sur un S24+.

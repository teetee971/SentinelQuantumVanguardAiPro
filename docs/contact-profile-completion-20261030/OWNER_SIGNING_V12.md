# Signature locale version12 — preuve propriétaire

Le propriétaire a fourni une capture du terminal après le bloc de signature contrôlée, exécuté dans Ubuntu proot sur son téléphone. Ce sont des observations issues de la capture utilisateur, **pas une signature ni une vérification binaire exécutée par le cloud**. L’APK signé et la clé privée n’ont pas été reçus ; aucune copie de clé ni installation n’a été réalisée par le cloud.

- APK d’entrée : Sentinel-D1-v12-UNSIGNED-06f913c0.apk, SHA256 e0d779cf5601fae9ceeecb609d5d38bc497d09a671f47d2c6a73d3f33efa021b, vérifié par sortie utilisateur précédente.
- Source : 06f913c057b9b1814f4dea7c436047df3dd2dfd4.
- Sortie locale dans Téléchargements : **Sentinel-D1-v12-SIGNED-68915958.apk**.
- SHA256 signé transcrit de la capture : **261d85b479d58256135d9b79b319c5564b17453a728d4ca915e7578c1315f255**.
- Certificat : **68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2**, identique au certificat installé précédemment établi.
- Package com.sentinel.quantum ; versionCode12 ; versionName1.0.9. Le code12 est supérieur au code11 installé précédemment observé.
- Marqueur final visible : **SIGNATURE_ET_INTEGRITE_VERIFIEES — NON INSTALLE**.

Le script fourni est fail-fast : avant ce marqueur, apksigner verify doit réussir, un seul certificat6891 doit être trouvé, zipalign4 et aapt/package/code12 doivent réussir, chaque entrée ZIP applicative source doit rester identique, seuls les ajouts de signature sont autorisés, et la copie de sortie exclusive doit être identique. La capture montre le bloc et les sorties finales ; elle ne donne pas de nouveau téléchargement du binaire signé au cloud. SHA et exécution restent donc attribués au propriétaire.

L’avertissement proot relatif à /proc/self/fd/0 n’a pas empêché d’atteindre le marqueur et les résultats finaux. Il ne constitue pas une erreur de signature observée. Aucun réalignement après signature ni nouvelle clé demandé.

**Compatibilité cryptographique et métadonnées de mise à jour établies selon ces preuves locales. Installation de la version12 : NON EFFECTUÉE / autorisation explicite en attente.** Ceci ne vaut pas validation fonctionnelle ou conformité D.1. Aucun verdict global de projet terminé. Tests instrumentés et physiques12 NON EXÉCUTÉS ; les six nouveaux tests d’interface sont compilés seulement. Les éléments de preuve version11 ne sont pas transférés à la version12.

Le propriétaire doit conserver le fichier signé sur son appareil. Aucun lien Drive vers cet APK signé n’existe. Les liens Drive précédents restent le fichier **unsigned** et l’audit figé avant cette preuve. Le prochain point de décision est une mise à jour sur place explicitement autorisée, sans désinstallation ni effacement ; vérifier ensuite versionCode12 et la fiche Contacts avant les autres scénarios.

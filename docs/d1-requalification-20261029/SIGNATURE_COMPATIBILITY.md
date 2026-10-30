> Rapport historique : le certificat installé a depuis été confirmé différent par l’utilisateur. Voir [réparation de signature](../signature-recovery-20261030/README.md). Aucun MATCH contre une référence locale ne démontre une mise à jour compatible du S24+.

# Signature : candidat et com.sentinel.quantum installé

**Signature installée sur S24+ : NON VÉRIFIÉE. Aucun appareil connecté. Aucune installation effectuée.**

Le certificat SHA-256 du candidat et de l'ancien fichier Phone Core V1 disponible localement est identique :

`dee0d0f14e3a211d5ce9adca0280acde443e2312857d33da2258bef0f1b632e1`

Les deux déclarent com.sentinel.quantum, versionCode10. Cela établit une compatibilité de certificat entre ces fichiers, pas l'identité du fichier effectivement installé. Le SHA-256 de l'APK et le SHA-256 du certificat sont deux mesures distinctes.

`scripts/check-installed-sentinel-signature.py` vérifie d'abord les signatures réelles avec apksigner et les métadonnées avec aapt. En mode appareil il exige --serial, lit l'utilisateur Android courant, récupère uniquement le base.apk public installé dans un répertoire temporaire, puis compare package/certificats/versionCode. Il ne lit aucune clé, base privée, SMS ou contact ; il ne réalise aucune mutation de l'appareil. La série n'est pas conservée dans le JSON. Le mode --installed-apk est explicitement marqué LOCAL_APK_ONLY_NOT_LIVE_DEVICE.

Exécutions locales conservées : comparaison avec ancien APK = MATCH/code0 ; appareil indisponible = UNVERIFIED/code2 ; référence non signée = rejet/code2. Le rejet d'une lignée différente est conservateur, pas une implémentation de rotation de clés. Aucun test de certificat différent réel n'est inventé, aucune clé de test créée.

Seuls un MATCH en mode CONNECTED_DEVICE_BASE_APK et une autorisation d'installation permettent d'envisager la mise à jour sur place ; Android reste l'autorité finale. Un MATCH ne promet ni toute compatibilité de plateforme ni l'absence de crash. Version installée supérieure, certificat différent ou lecture impossible : **arrêt**, conservation de l'application et de ses données. Ne pas désinstaller, ne pas effacer le stockage, ne pas forcer un downgrade ou signer avec une autre clé pour contourner l'erreur. Le propriétaire devra fournir/autoriser la chaîne de signature d'origine ou un candidat correctement signé hors Git si nécessaire.

Verdict actuel : **NOT READY pour installer sur l'application existante**, en attente de la preuve de signature de l'appareil et de son autorisation. Candidat techniquement construit pour cette qualification ; aucune prétention de validation physique ou de publication Google Play.

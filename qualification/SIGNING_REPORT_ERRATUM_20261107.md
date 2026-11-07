# Erratum du rapport de signature v22

Le premier script v22 vérifiait correctement candidate_versionCode22 et les empreintes des APK, mais écrivait encore21 dans le rapport final. aapt2 confirme versionCode22 / versionName1.0.9 dans l’APK cloud. Le rapport utilisateur confirme signature v2, certificat existant, payload ZIP identique et offsets natifs conservés. SHA signé utilisateur : `10e70d21409daabcee0ba898703a1cb60897d127d22afffc84b6ca79f94390c8`.

Le champ provient désormais des métadonnées validées. Le test exécute l’instruction réelle de construction du rapport avec les versions22 et23 : deux échecs avant correction, aucun après. Un autotest complet de signature avec la seule clé cloud produit correctement22. La clé privée de l’utilisateur n’a pas été transmise.

Les APK restent identiques aux fichiers qualifiés sur `51acdb15abd03aff0988e237e88fdadf1d077e49`, arbre Android `00d1a0295ba57334c9421c81ff52bda204d7ca29`. Aucune source Android modifiée ; aucune nouvelle signature de l’APK utilisateur nécessaire. Conserver son rapport original sans correction silencieuse. Observations normalisées et journaux : evidence/20261107-signing-report-fix.

Le kit corrigé est publié à un nouveau lien dans le ledger ; l’ancien est conservé comme historique. Seuls le script de rapport et le README changent dans le kit. Le SHA final de cet erratum est requalifié séparément avec les commandes obligatoires et comparaison des empreintes APK.

BLOCKED. Galaxy S24+ : NOT_TESTED pour v22. Signature locale confirmée ; installation et essais physiques en attente. La cause du MMS v21 échoué reste non établie.

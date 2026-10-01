# Revue Phone Core — 1 octobre 2026

## Périmètre et verdict

Référence du code examinée : `8da13f492dff16cbb3d64f931e8edd3298ddc1be` après intégration de la PR #1435. Revue des sources du centre d'activation, composeur, InCall, diagnostics, permissions/rôles, timeline/provenance, callbacks SMS et chaîne MMS ; contrôles statiques du manifeste, de vérité produit et des surfaces UI. Cette revue ne constitue pas un audit de sécurité exhaustif ni une validation visuelle ou terrain.

Le jalon est mesurable : prérequis logiciels réellement activés + APK candidate vérifiée et installable → 13 preuves sur appareil → matrice de compatibilité → statut fonctionnel. Le code ne permet pas de promettre 100 % de prérequis activables sur un appareil dont Android refuse les rôles nécessaires.

## Défauts corrigés par cette revue

| Constat | Correction | Vérification |
| --- | --- | --- |
| Avec preuves 13/13 et logiciel prêt, le résumé pouvait annoncer une validation complète après perte de la ligne mobile, contrairement au badge. | Résumé dérivé de la même Readiness que le badge ; état suspendu si les conditions courantes ne sont plus réunies. | Tests unitaires de perte de ligne, SIM et notifications. |
| Le composeur pouvait garder « validé » sur la seule combinaison logiciel + preuves après disparition de l'environnement opérateur. | Le collecteur partagé vérifie comptes d'appel et SIM ; le composeur dérive un état dégradé. | Régression du modèle UI pour preuves complètes sans environnement opérateur. |
| Le calcul Compose de readiness n'avait pas l'autorisation plein écran comme clé de mémorisation. | Ajout de cette clé ; un retour des réglages doit réévaluer la readiness même si les autres champs n'ont pas changé. | Relecture des dépendances ; contrôle du retour des réglages encore à exécuter sur appareil. |
| La jauge utilisait 33/66/100 % pour des étapes ; elle pouvait suggérer une progression mesurée indépendante du compteur. | Jauge liée au nombre réel de preuves locales, à côté du compteur ; aucun pourcentage de production. | Relecture du calcul et contrôles de cohérence UI. |
| Si les callbacks DELIVERED précédaient le dernier SENT, le reducer avait bien une livraison complète mais le receiver n'émettait que la preuve SENT. | Émission des signaux agrégés depuis l'Outcome, indépendamment du type du dernier callback. | Tests multiparties livraison-avant-envoi et livraison en erreur. |
| README Android limitait aussi FINE_LOCATION à API 32 alors que le manifeste et le scanner ne le font pas. | Description alignée ; COARSE_LOCATION seule reste limitée à API 32. | Comparaison manifeste/documentation. |
| La page mobile nommait des contacts « choisis » tout en utilisant READ_CONTACTS et mélangeait Wi-Fi et gate Phone Core. | Portée du carnet du profil explicitée ; Wi-Fi séparé des 13 preuves. | Relecture des états publics et test de vérité du certificat. |

## Frontières examinées

- **Activation :** faits Android relus au retour du système ; historique de l'assistant distinct de la readiness ; indisponibilité des rôles explicitée. L'activation guidée de Call Screening reste limitée à API 29+.
- **Appels :** service système protégé par BIND_SCREENING_SERVICE/BIND_INCALL_SERVICE ; réponse de filtrage locale avant persistance ; dégradation ouverte si classification d'urgence indisponible. Aucun cloud sur la réponse critique.
- **Providers :** contacts après READ_CONTACTS ; historique après rôle Téléphone et READ_CALL_LOG ; vérification du provider distincte d'une permission déclarée.
- **SMS :** rôle SMS et permissions relus ; choix de souscription explicite ; callbacks privés/immutables et identités URI vérifiées ; agrégation multiparties et tombstones bornés.
- **MMS :** réception système protégée, callback privé, chemins canoniques et taille bornée, décodage sécurisé et quarantaine ; un aperçu reçu ne prouve pas l'envoi MMS sortant.
- **Preuves :** schema v4, 13 critères, provenance installation/version/mise à jour, écriture durable et verrou partagé ; timeline minimisée sans numéro ou corps de message.
- **Notifications :** autorisations, canaux et publication acceptée distingués d'une notification visuellement observée.
- **Réseau :** absence de cleartext applicatif déclaré ; VPN Internet et Mesh distincts ; ni gateway réelle ni disponibilité universelle déduites de la présence de WireGuard.

Ces observations décrivent les chemins lus et les guards exécutés ; elles ne prouvent pas l'absence de toute vulnérabilité, de race ou de comportement constructeur inattendu.

## Vérifications et limites

Contrôles locaux exécutés avec succès après corrections :

- `node scripts/check-android-manifest.js` : 24 permissions déclarées, exceptions bornées/rôles vérifiés.
- `node scripts/check-android-product-truth.js` : contrat de vérité produit réussi.
- `node scripts/check-android-ui-consistency.js` : 21 écrans découverts, 5 activités à barre commune et 2 surfaces immersives.
- Suites `check-android-product-truth.test.js`, `phone-core-certification-truth.test.js` et `android-install-smoke-policy.test.js` réussies.

Les nouveaux tests Kotlin, lint, compilation et installation de l'APK doivent encore être exécutés par GitHub Actions sur le commit final de cette correction. La CI de #1435 était réussie sur son dernier commit avant intégration ; elle ne valide pas les nouveaux changements.

Aucun émulateur Android, appareil physique ni outil de capture n'est disponible dans cette session. La revue UX concerne donc les états et parcours dans le code. Lisibilité, contraste rendu, zones tactiles, TalkBack, clavier, très grandes polices, fenêtres système et rotations restent à observer ; aucune conformité visuelle/accessibilité n'est prononcée.

## Gate avant remise de l'APK

1. Contrôles requis réussis sur le SHA exact incluant les corrections ; tests Kotlin et lint réellement exécutés.
2. APK non vide, package/version attendus, signature et alignment vérifiés ; absence de secrets embarqués ; installation et lancement contrôlés.
3. Fournir l'identité du commit, du canal et le checksum. Un APK debug est une candidate d'essais et reste distinct d'une release de production.
4. Exécuter [le protocole physique](PHONE_CORE_PHYSICAL_VALIDATION.md) ; garder non exécuté tout résultat absent.
5. Après essais, vérifier matrice Android/constructeur, double-SIM, refus/révocation, réversibilité et règles de distribution/signature.

La remise d'une candidate pour essais ne prononce ni succès terrain ni disponibilité publique. La release signée et la passerelle VPN restent des gates séparés.

## Cahier des charges complémentaire

- [Contrat de sécurité mobile et dépendances OS](MOBILE_SECURITY_CAPABILITY_CONTRACT.md).
- [Plan de connectivité Mesh et intégrations](MESH_DELIVERY_PLAN.md).

Les caractéristiques d'un OS renforcé ne sont pas attribuées artificiellement à une application Android.

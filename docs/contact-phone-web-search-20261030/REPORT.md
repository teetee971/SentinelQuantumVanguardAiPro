# Candidat15 — recherche web explicite d’un numéro

**Implémenté dans la vraie fiche ; résultat internet et navigation physique NON VALIDÉS. NOT READY pour installation tant que signature propriétaire et autorisation ne sont pas établies.** Ni recherche réelle d’un numéro privé ni attribution d’identité exécutées depuis le cloud.

## Fonctionnement

À côté d’Appeler/Message de chaque numéro frais valide, **Rechercher ce numéro sur le web** ouvre un aperçu. Celui-ci affiche le numéro normalisé et DuckDuckGo, explique la transmission de ce seul champ et l’absence de garantie d’identité. Annuler ne déclenche rien. **Ouvrir la recherche** exige un second geste puis un contrôle local avant ouverture HTTPS dans le navigateur.

La requête porte uniquement sur le numéro choisi, entre guillemets et encodé UTF8. Séparateurs visuels retirés, indicatif fourni conservé ; aucun pays deviné ni fusion national/international.5–15chiffres, +initial éventuel ; extensions, commandes*,#, expressions libres, contrôles et valeurs hors borne refusés. La recherche peut ne rien trouver si le numéro n’est pas indexé, être affectée par le format ou trouver une page obsolète/un autre titulaire. Le propriétaire peut adapter sa recherche dans le navigateur. Aucun compte social ou identité confirmé automatiquement.

La permission Contacts, le LOOKUP_KEY du contact sélectionné et la présence actuelle de ce même numéro sont relus sur IO ; retour sur main avec contrôle d’annulation, permission et lifecycle RESUMED. Scope et état liés au contactId/lookup/numéro, confirmation non sauvegardée. Pas d’Intent CALL/SMS, pas d’historique de recherche dans Sentinel, pas de recherche de tout le carnet ou en arrière-plan. Le navigateur/moteur a son propre fonctionnement et historique ; aucun anonymat garanti.

Cette action séparée transmet un numéro seulement après consentement explicite. Le parcours social14 continue de ne transmettre que nom/entreprise quand sa recherche spécifique est choisie. Aucun résultat de la recherche de numéro n’est joint automatiquement au carnet ou au module Social Intelligence. Les liaisons sociales restent une confirmation manuelle distincte.

## Source et tests

- Dépôt teetee971/SentinelQuantumVanguardAiPro ; branche fix/d1-phone-messages-conformity-20261029 ; PR brouillon1748.
- **Source APK15 : 7c679377e4f509870dd55e6745d8e8cf8422e472**, base c8c740cdc6ea1f3dfec47571d28b0ab1e6682fcd ; liste des fichiers dans SOURCE_FILES.txt. Commits de livraison ultérieurs documentaires uniquement.
- VersionName1.0.9, versionCode15, packagecom.sentinel.quantum ; min24/target36/compile37.
- Kotlin, suite app et APK debug/test/unsigned : exit0,1m41. Build post-commit exit0,18s,125tâches incrémentales sur sources identiques.
- **App :1437 réussis,0échec,0erreur,0ignoré**,307XML ;3nouveaux tests de normalisation/encodage/rejets, RED absent API puis GREEN et suite complète. **Wearable :26/26**,0échec/erreur/ignoré, tâche inchangée/cachée. **Node produit69/69**, **Node livraison15/15**,0échec/ignoré.
- **Trois nouveaux tests instrumentés compilés, NON EXÉCUTÉS** : aperçu/annuler/confirmation, changement de contact/numéro, vrai guard avec fournisseur synthétique et révocation pendant lecture. Aucun runtime Android cloud disponible, aucun résultat réseau réel évalué.
- Revue indépendante ciblée : aucun défaut important relevé ; revue statique, pas une preuve runtime. Aucun nouveau secret, dépendance, service ou dépense ; schémas Room non suivis préservés,1.json SHA256 a7c5b4e1926c4441629734c0703ba6174e6bcf3796394b51302846bac0dae0c4.

## APK, alignement et signature

- **unsigned** `Sentinel-D1-v15-UNSIGNED-7c679377.apk` — 101374983octets ; SHA256 `a6dbf298c10c7e16adafb940128a069ea9defecef7aa174f263f0aed8550263c` ; apksigner exit1, zipalign4 exit0, zipalign16KiB exit0, aapt exit0.
- **cloud_debug** `Sentinel-D1-v15-CLOUD-debug-7c679377.apk` — 101494393octets ; SHA256 `4459e381b2cf8f791eda7eb1fa0a6eacdb49864e5bf67fa6c6b045dfd1efe010` ; apksigner exit0, zipalign4 exit0, zipalign16KiB exit0, aapt exit0.
- **instrumentation** `Sentinel-D1-v15-androidTest-7c679377.apk` — 1239931octets ; SHA256 `16ae14b9c72da03f91518f88c6eab3962c645348f0b06fd11f77c735e0561146` ; apksigner exit0, zipalign4 exit0, zipalign16KiB exit0, aapt exit0.

[APK15 NON SIGNÉ — NON INSTALLABLE](https://drive.google.com/file/d/15rE9RM0LNXTUBPAPb6r9cqSOCZMDZEMN/view?usp=drivesdk)

L’exit1 de vérification unsigned est attendu, pas une signature validée. Cloud debug/test portent le certificatdee0d0f14e3a211d5ce9adca0280acde443e2312857d33da2258bef0f1b632e1, incompatible avec le propriétaire68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2. La clé reste sur le téléphone ; signer/vérifier localement avant mise à jour expressément autorisée, sans désinstallation ni effacement. Aucun APK15 signé6891 reçu dans le cloud. SHA local ; métadonnées Drive et taille relues, pas de hash distant intégral revendiqué.

## Essais physiques requis

Après signature et mise à jour autorisée : contact de test autorisé, aperçu du numéro, Annuler sans navigateur ; confirmer le même numéro puis contrôler champ/requête externe, retour à la fiche, contact différent et révocation. Aucune recherche de numéro privé d’un tiers pour générer des preuves publiques ; préférer un numéro professionnel de test publié et autorisé. Résultats traités comme indices, pas comme preuve d’identité.

Le candidat15 inclut la mitigation13 du rebond et le parcours14 assisté ; **gel de fin de fiche encore OUVERT**, API sociales/collecte automatique non implémentées, conformité D.1/non-régression physique générale non validées.13 reste disponible pour comparaison isolée. Scénarios S24+26/appels/SMS/MMS/permissions/notifications et Voice gardent leurs limites antérieures. Aucune fusion, installation automatique ou workflow payant.

# Sentinel — scénario une seule application après réinstallation propre

État : **préparation technique uniquement**, aucune installation physique ni APK final validé.

Le propriétaire a confirmé ne pas avoir de données Sentinel à sauvegarder. Il souhaite supprimer les deux anciennes éditions uniquement après disponibilité d'un nouveau candidat vérifié.

## Candidat principal — signature durable

Le build Gradle `./gradlew :app:assembleDebug -PsentinelCleanInstallDebug=true` garde l'identifiant principal `com.sentinel.quantum` et utilise **uniquement** les variables privées de signature de production :
`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Ne pas utiliser la clé `SENTINEL_D1_*` pour cette application principale : une clé de test rendrait les futures mises à jour signées par la vraie clé de production incompatibles. Le fichier keystore de production reste hors dépôt et doit être conservé avec une sauvegarde chiffrée. Ce mode candidat est **debug**, pas une release certifiée.

Le mode `-PsentinelIsolatedDebug=true` conserve `com.sentinel.quantum.d1test`, désactive les composants de téléphonie sensibles et utilise la clé D.1 distincte. Les deux propriétés ne peuvent pas être activées ensemble.

## Ordre obligatoire de qualification

1. Configurer les secrets de signature de production chez le fournisseur CI ; ne pas les envoyer dans GitHub ni dans une conversation.
2. Construire le candidat principal, vérifier le `applicationId`, le certificat du signataire, les permissions/roles déclarés et les rapports de tests.
3. Qualifier sur émulateur l'installation, le premier lancement, la navigation, l'assistant d'autorisations et les comportements de base sans jamais déclarer les appels/SMS réels « PRÊTS » sur cette seule base.
4. Vérifier que l'APK est disponible et que la clé employée sera conservée pour les mises à jour futures.
5. Sur le téléphone, s'assurer que les applications Samsung/Google gèrent les rôles Téléphone et SMS, puis retirer les anciennes éditions **uniquement après confirmation utilisateur** et disponibilité du nouvel APK.
6. Installer normalement le candidat et contrôler l'APK ainsi que les modules autorisés sur Galaxy S24+ ; ne pas contourner Play Protect.
7. Effectuer séparément les tests physiques appels, SMS/MMS, lignes SIM, contacts, VPN, réseau et conformité D.1. Le module talkie-walkie n'est pas opérationnel à ce stade.

**Échec de signature, Play Protect ou installateur = STOP** : ne pas désinstaller davantage, diagnostiquer. Même un APK compilé sans erreur n'est pas une preuve de fonctionnement.

## Critère de livraison

Une seule application principale installée, identifiant stable, signature conservée, assistant d'autorisations utilisable, résultats de tests techniques et physiques distincts, aucune fausse certification D.1/Phone Core/PTT.

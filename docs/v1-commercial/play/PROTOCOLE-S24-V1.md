# Protocole Galaxy S24+ · Phone Core V1

**Aucun de ces essais n’a été exécuté sur un téléphone dans cette session.** Un résultat ne devient PASS que lorsqu’un appareil réel le produit.

Build : `-PsentinelCommercialV1=true`, paquet de recette `com.sentinel.quantum.v1validation`, `versionName` `1.0.9-v1-validation`, `versionCode` 10. Ne pas installer cet APK debug à la place du paquet Play `com.sentinel.quantum`.

## Préparation

1. Compiler l’APK debug ARM64 et relever son SHA-256.
2. `adb devices -l` doit montrer un S24+. Noter le numéro de série, la version Android et le niveau d’API.
3. Installer uniquement le paquet `com.sentinel.quantum.v1validation`.
4. Partir sans rôle Téléphone, sans rôle SMS, sans rôle de filtrage. L’interrupteur « Mettre à jour les plans ARCEP et RTR » est coupé.

## Rôles

| Étape | Action | Résultat attendu |
| --- | --- | --- |
| R1 | Ouvrir l’assistant, refuser le rôle Téléphone | Aucun appel placé par Sentinel |
| R2 | Accorder le rôle Téléphone, puis les permissions d’appel, d’état de ligne et de journal | L’historique n’est demandé qu’après le rôle |
| R3 | Retirer le rôle dans les réglages Android | Les récents ne sont plus relus |
| R4 | Accorder puis retirer le rôle SMS | Envoi et lecture cessent après le retrait |
| R5 | Accorder puis retirer le filtrage | Une décision n’est plus appliquée après le retrait |

## Appels, SMS, MMS

| Étape | Action | Résultat attendu |
| --- | --- | --- |
| C1 | Appel sortant vers un numéro de test | L’appel passe par Android. Pas d’enregistrement audio |
| C2 | Appel entrant français, interrupteur catalogues coupé | La fiche s’affiche. Aucun GET vers `arcep-numbering.json` ni `rtr-numbering.json` |
| C3 | Activer l’interrupteur, relancer un appel français sans copie locale | Un GET HTTPS part. Le numéro n’est pas dans l’URL |
| C4 | Couper l’interrupteur pendant ce GET | Le document reçu après la coupure n’est pas réutilisé. Le filtrage de l’appel suivant reste local |
| C5 | SMS sortant et SMS entrant sur la SIM de test | Visibles dans Sentinel seulement avec le rôle SMS |
| C6 | MMS court et contenu hors format | Aperçu borné, ou quarantaine |

## Politique

Ouvrir Paramètres, « Politique de confidentialité ». Tant que la politique Phone Core V1 n’est pas publiée, la page servie est celle de la vitrine, et l’écran le dit. Noter l’URL réelle et le titre de la page.

## Preuve

Conserver le SHA de l’APK, `adb shell dumpsys package`, une capture réseau des hôtes contactés, et le journal des rôles avant et après retrait. Ne pas y mettre de numéro réel hors ligne de test.

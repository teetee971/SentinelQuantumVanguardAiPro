# Stabilisation Phone Core — 6 octobre 2026

Référence initiale vérifiée : PR #1589 ouverte/draft, source
`5ad1eb4df693509949b796a1d91b3ddd0008c54e`, base
`25bf6a36bd4c5cb8299be238797538bc72f1fcc2`. Aucun commit nouveau depuis la
référence transmise. Le merge testé `b55eaae5c1aba8823030430196b23c99490840a8`
a exactement ces deux parents et le même arbre que le source PR.
Ce constat historique ne qualifie jamais les commits suivants.

## Passe fonctionnelle ciblée

Examen du composeur, InCallService/UI, wizard, screening, SMS/MMS, scripts runtime
et artefacts du run 37401925271. API 37 a conservé un FAIL légitime sur révocation.
Le dumpsys révèle deux appels sortants pour le même numéro synthétique : le harnais
répétait placeCall avant persistance du premier INCALL_ACTIVE. L'UI termine le
second, laissant le premier ACTIVE ; les changements de rôle/processus font ensuite
basculer l'InCallService sur le dialer système. Ce n'est pas un manque de capacité
SIM : le PhoneAccount Telecom expose bien `SimSub`.

Correction : une seule soumission, fenêtre d'observation bornée conservée, teardown
vérifié avec le modem **et** la section live mCalls de Telecom. Un simple `OK`, un
historique ou un dump inconnu ne prouve pas la fin d'appel. Les régressions exécutent
les oracles avec appel ACTIVE résiduel, DISCONNECTED non retiré, dump tronqué,
ambigu, illisible et UI traduite. Aucun test/règle de production n'est neutralisé.

## Passe sécurité / architecture ciblée

Relecture de l'autorité SIM immédiatement avant placeCall, routage urgence explicite,
CallScreening avant/après réponse, composants du manifeste, PendingIntent SMS/MMS,
signature/provenance des preuves, politiques Wear et capture LiveKit. Le chemin
PSTN accepte FRAMEWORK_SIM uniquement ; l'ancien oracle isOutgoingCallPermitted
et le workflow temporaire auto-écrivant sont interdits par régression. Les callbacks
SMS/MMS utilisent des PendingIntent immuables ; réseau cleartext désactivé ; composants
privilégiés protégés par permissions système. Screening relit le rôle et reste
fail-open sans moteur lorsque le rôle/oracle urgence est absent ou inconnu.

Ces deux passes sont ciblées, pas une certification exhaustive de tous les modules.
Les scans automatisés et les résultats exact-HEAD doivent encore confirmer le HEAD
final. Une durée <500 ms sur S24+ ne se déduit ni du code mémoire-only ni de la durée
globale de filtrage Telecom : mesure callback → respondToCall physique obligatoire.

## Inventaire i18n et testabilité

Commande reproductible :

```sh
python3 scripts/phone-core-string-inventory.py > phone-core-string-inventory.json
```

Le générateur énumère tous les candidats littéraux Kotlin dans app/src/main (y
compris chaînes techniques/commentaires, sans filtre linguistique susceptible de
perdre des textes) et toutes les ressources string/plurals/string-array. Ce n'est
pas une promesse que chaque candidat doit être traduit. Baseline après migration :
6177 candidats dans 244 fichiers Kotlin, 373 ressources. Principales surfaces :
composeur 191, InCall 61, SMS 209, activation 101, Caller ID 117 candidats.

Sept actions sont maintenant des ressources Android françaises. Le brouillon anglais
`docs/i18n/phone_core_actions.en.xml` reste hors des ressources livrées : une locale
partielle provoquait 366 erreurs MissingTranslation légitimes et ne constitue pas
un support de langue utilisable. Le catalogue complet est requis avant activation. Les actions d'automatisation
Clavier/Appeler/Décrocher/Raccrocher/Répondre/Envoyer utilisent des testTag exportés
en resource-id app-owned. Les libellés d'accessibilité restent traduits séparément.
Restent : migration des autres libellés/états, descriptions d'accessibilité,
pluriels, choix utilisateur de langue, sélecteurs des diagnostics/statuts encore
français et des surfaces entrantes. Le thème D.1 reste inchangé.

## Dépendances restant à réaliser

- Wear : politiques et tests cryptographiques présents ; pas de
  WearableListenerService/MessageClient/Data Layer ni application Watch démontrée.
  Préparer téléphone + module Watch, discovery Node/CapabilityClient, transport
  authentifié/session, challenge signé + anti-replay monotone, capability negotiation,
  listener à validation stricte, actions identifiées/idempotentes et tests deux nœuds.
  Une association Bluetooth ne donne jamais AUTHENTICATED/ACTIVE. wearable_basic
  reste indisponible jusqu'aux preuves transport/runtime/physiques.
- Voice : capture LiveKit/FloatS16/DSP/client room présents. Token issuer éphémère,
  signaling et gateway VoIP/PSTN réels restent à provisionner ; preuves bidirectionnelles,
  latence, écho/audio, interruption/reconnexion et conformité restent obligatoires.
  Aucun jeton statique, aucune interception arbitraire du média des appels SIM.
- Release : workflow protégé android-production, keystore externe et secrets dédiés
  déjà prévus. Ne pas lancer de release signée sans secret et validation physique.
  Archiver aussi mapping R8/symboles (si natifs), attestation, release notes et plan
  rollback avant publication ; confirmer versionCode croissant/certificat compatible.
  Rollback Play = arrêt du rollout et build correctif à versionCode supérieur, pas
  installation forcée d'une ancienne version. Signatures debug CI variables : vérifier
  la compatibilité de certificat avant tout essai d'upgrade.

La prochaine frontière matérielle est la campagne
[Samsung S24+ / Watch6](PHONE_CORE_PHYSICAL_VALIDATION.md). Chaque test reste
NON EXÉCUTÉ jusqu'à preuve réelle. implemented/configured/runtime_verified ne donnent
jamais physically_validated/release_signed/customer_available.

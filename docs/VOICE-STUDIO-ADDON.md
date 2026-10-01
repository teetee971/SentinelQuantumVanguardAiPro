# Studio voix — transformation obligatoire des appels Sentinel

## Exigence produit

La transformation de voix pendant un **appel Sentinel compatible** est une exigence de livraison de l’add-on. Elle ne doit pas être remplacée par un simple aperçu local.

Le dépôt contient maintenant trois briques distinctes :
- `LiveVoiceTransformEngine` : transformation PCM16 temps réel, bornée en mémoire ;
- `SentinelVoipVoicePipeline` : traitement de chaque trame microphone sortante avant encodage/packetisation ;
- `SentinelVoipCallSession` : session d’appel qui impose le passage par le pipeline avant remise au transport média détenu par Sentinel.

Effets intégrés :
- Naturelle : pitch 1.0 ;
- Grave : pitch 0.72 ;
- Aiguë : pitch 1.35.

## Aperçu local

Le Studio voix conserve un aperçu local pour tester le rendu avant un appel :
- déclenchement explicite par l’utilisateur ;
- permission microphone demandée au moment de l’essai ;
- court fichier stocké dans le cache privé ;
- aucun téléversement ;
- suppression du fichier à la fermeture de l’écran ;
- blocage de l’aperçu si un appel mobile natif est détecté comme actif.

L’aperçu n’est pas une preuve qu’un appel de production a traversé le chemin transformé.

## Limite Android des appels SIM natifs

Le rôle Téléphone / `InCallService` permet à une application tierce de fournir l’interface d’appel et de gérer le cycle de vie exposé par Telecom. Il ne fournit pas de pipeline public permettant à une application ordinaire de capturer, transformer puis réinjecter l’audio uplink d’un appel opérateur/SIM.

Conséquence : Sentinel ne doit pas prétendre modifier directement le média d’un appel SIM natif. Aucun contournement par API cachée, privilège système ou dépendance OEM n’est accepté comme solution produit standard.

## Chemin retenu pour les appels transformés

Pour appeler un numéro téléphonique classique avec une voix transformée, le chemin cible est :

`microphone → SentinelVoipCallSession → SentinelVoipVoicePipeline → LiveVoiceTransformEngine → codec VoIP → transport Sentinel → passerelle VoIP/PSTN → correspondant`

La transformation se produit avant l’encodage du média sortant. Le client WebRTC concret est maintenant intégré via LiveKit : `LiveKitVoiceAudioProcessor` modifie le PCM de capture et `SentinelLiveKitCallTransport` établit une room `wss://` avec jeton éphémère puis publie le microphone. Restent à provisionner le serveur LiveKit/token issuer et la passerelle PSTN, puis à valider le trajet de bout en bout.

## État de réalisation

- [x] moteur DSP PCM16 temps réel intégré ;
- [x] post-processeur de capture LiveKit/WebRTC intégré ;
- [x] transport Android LiveKit fail-closed avec endpoint `wss://` et jeton éphémère ;
- [x] transformation appliquée avant transmission WebRTC côté client ;
- [ ] serveur LiveKit/signaling de production provisionné ;
- [ ] émetteur de jetons éphémères provisionné et audité ;
- [ ] passerelle VoIP/PSTN provisionnée pour les numéros classiques ;
- [ ] appel réel transformé validé de bout en bout ;
- [ ] validation physique latence/écho/Bluetooth/haut-parleur/écouteur ;
- [ ] gestion mute/hold/reconnexion/interruption validée ;
- [ ] confidentialité et Data Safety Play finalisées pour le flux audio réseau ;
- [ ] entitlement, restauration d’achat et remboursement validés.

## Traitement des données vocales

L’aperçu du Studio reste local. Un appel Sentinel VoIP réel est différent : pour transporter la conversation, le microphone doit être envoyé sur le chemin WebRTC après transformation. Le client Android ne doit pas persister le jeton LiveKit ni enregistrer silencieusement l’audio. Pour un appel vers le réseau téléphonique classique, la passerelle PSTN devient également un sous-traitant/maillon technique à documenter avant lancement.

## Gates avant commercialisation

Le checkout reste verrouillé tant que toutes les cases non cochées ci-dessus ne sont pas levées par des preuves réelles. Le moteur DSP et le client WebRTC étant intégrés, la dette principale est désormais l’infrastructure de service, la validation physique et la conformité du flux média réseau.

Le service d’appel transformé n’est donc pas encore déclaré opérationnel tant que le transport réel et la validation physique ne sont pas terminés.

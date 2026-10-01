# Studio voix — transformation obligatoire des appels Sentinel

## Exigence produit

La transformation de voix pendant un **appel Sentinel compatible** est une exigence de livraison de l’add-on. Elle ne doit pas être remplacée par un simple aperçu local.

Le chemin Android concret contient quatre briques reliées :
- `LiveKitVoiceAudioProcessor` : point d’entrée des trames microphone de capture LiveKit ;
- `SentinelVoipVoicePipeline` : traitement borné du canal de capture exposé par le bridge WebRTC ;
- `LiveVoiceTransformEngine` : transformation Float32 temps réel ;
- `SentinelLiveKitCallTransport` : création de la room sécurisée et publication du microphone traité.

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

`microphone → capture LiveKit → LiveKitVoiceAudioProcessor → SentinelVoipVoicePipeline → LiveVoiceTransformEngine → WebRTC → room Sentinel → passerelle VoIP/PSTN → correspondant`

La transformation se produit avant l’encodage du média sortant. Le pont natif WebRTC expose au processeur Java un `ByteBuffer` direct adossé à des échantillons **Float32 dans le domaine d’amplitude FloatS16 de WebRTC** (pleine échelle proche de ±32768), et non à des valeurs normalisées ±1 ; `numFrames` représente la trame complète de 10 ms et `numBands` le découpage interne WebRTC. `LiveKitVoiceAudioProcessor` lit donc des Float32 — jamais du PCM16 — puis délègue explicitement la trame au `SentinelVoipVoicePipeline` avant que LiveKit ne l’encode et la transmette. Si la forme du callback média est invalide, sous-dimensionnée ou multi-canal alors que le bridge n’expose qu’un canal transformable, le chemin échoue en fermeture : la trame accessible est silencée ou l’initialisation est refusée, afin de ne jamais transmettre la voix brute à la place de la voix transformée. `SentinelLiveKitCallTransport` établit une room `wss://` sans credentials dans l’URL, avec jeton éphémère fourni séparément, puis publie le microphone uniquement après le préflight de permission et la connexion. Restent à provisionner le serveur LiveKit/token issuer et la passerelle PSTN, puis à valider le trajet de bout en bout.

## État de réalisation

- [x] moteur DSP Float32/FloatS16 temps réel intégré ;
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

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

La transformation se produit avant l’encodage du média sortant. Le transport concret WebRTC/SIP et la passerelle PSTN restent à raccorder et à valider de bout en bout.

## Gates avant commercialisation

Le checkout reste verrouillé tant que toutes les preuves suivantes ne sont pas réunies :
1. transport VoIP Sentinel réellement connecté à `SentinelVoipCallSession` ;
2. appel pair-à-pair ou PSTN réellement transporté de bout en bout ;
3. validation audio sur appareils physiques : latence, intelligibilité, écho, haut-parleur, écouteur, casque filaire et Bluetooth ;
4. gestion mute/hold/reconnexion/interruption ;
5. consentement, confidentialité, Data Safety Play et information utilisateur validés ;
6. entitlement, restauration d’achat et remboursement validés ;
7. tests anti-régression empêchant tout statut « prêt » ou checkout sans preuves.

Le moteur DSP est donc **intégré**, mais le service d’appel transformé n’est pas encore déclaré opérationnel tant que le transport réel et la validation physique ne sont pas terminés.

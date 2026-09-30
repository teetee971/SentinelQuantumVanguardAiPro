# Studio voix — add-on optionnel

## Position produit

Le Studio voix fournit aujourd’hui un **aperçu local** de rendus vocaux. Il ne modifie pas le flux audio d’un appel mobile/SIM.

L’aperçu :
- est déclenché explicitement par l’utilisateur ;
- demande le microphone uniquement au moment de l’essai ;
- enregistre un court fichier dans le cache privé de l’application ;
- ne téléverse pas cet échantillon ;
- supprime le fichier à la fermeture de l’écran ;
- est bloqué si Sentinel ne peut pas vérifier qu’aucun appel mobile n’est actif.

## Limite Android actuelle

Le rôle Téléphone / `InCallService` permet à une application tierce de fournir l’interface d’appel et de gérer le cycle de vie exposé par Telecom. Il ne donne pas à Sentinel un pipeline public permettant de capturer, transformer puis réinjecter l’audio uplink/downlink d’un appel SIM.

En conséquence :
- aucun bouton de modulation n’est ajouté dans l’interface d’appel SIM ;
- aucun achat n’est proposé pour une fonction d’appel que la plateforme ne permet pas de fournir proprement ;
- aucun contournement par API cachée, privilège système ou dépendance OEM n’est utilisé.

## Add-on payant cible

Une option payante pourra être activée uniquement pour un futur appel VoIP Sentinel où Sentinel contrôle réellement le média de bout en bout.

Le checkout reste bloqué tant que les trois preuves suivantes ne sont pas toutes vraies :
1. pipeline média VoIP possédé et contrôlé par Sentinel ;
2. validation audio réelle sur appareils physiques (latence, casque, Bluetooth, haut-parleur, interruption/reconnexion) ;
3. revue confidentialité et information utilisateur validées.

## Effets d’aperçu actuels

- Naturelle : pitch 1.0
- Grave : pitch 0.72
- Aiguë : pitch 1.35

Ils servent à tester l’expérience et ne constituent pas une preuve de compatibilité avec un appel.

## Gates avant commercialisation

- [ ] Appels VoIP Sentinel fonctionnels via un chemin Android supporté.
- [ ] Traitement vocal temps réel borné et stable.
- [ ] Latence mesurée et acceptable sur appareils physiques.
- [ ] Tests écouteur, haut-parleur, casque filaire, Bluetooth.
- [ ] Gestion propre mute/hold/reconnexion.
- [ ] Politique de confidentialité et Data Safety Play alignées.
- [ ] Prix, entitlement, restauration d’achat et remboursement validés.
- [ ] Tests anti-régression empêchant l’activation du checkout sans preuves.

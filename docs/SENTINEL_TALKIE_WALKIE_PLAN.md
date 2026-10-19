# Sentinel — Talkie-walkie sécurisé (après qualification D.1)

Statut : **planifié, non implémenté ni validé**.

Ordre de livraison :
1. Stabiliser l'assistant d'autorisations Android, rôles Téléphone/SMS explicites et réversibles.
2. Finaliser la fidélité D.1 aux 13 références approuvées et qualifier sur Galaxy S24+.
3. Développer et qualifier le talkie-walkie.

## MVP PTT (push-to-talk)
- Appui-maintenu pour parler, relâchement pour arrêter ; état TX/RX lisible et accessible.
- Contacts Sentinel et groupes privés sur invitation ; refus et blocage des utilisateurs.
- Microphone : permission demandée uniquement à l'activation de la fonction ; comportement clair en cas de refus.
- Transport IP sécurisé et authentifié ; chiffrement de bout en bout comme exigence à concevoir et à auditer, pas une promesse déjà réalisée.
- Protection contre l'écoute involontaire et les sessions micro persistantes ; indicateurs audio et arrêt immédiat.
- Cas réseau : Wi-Fi/cellulaire, perte de connexion, latence, reconnexion, sortie de groupe.
- Respect des restrictions Android sur microphone en arrière-plan, services foreground et notifications.
- Distinguer PTT sur Internet du mode hors Internet. Ni Bluetooth, ni Wi-Fi Direct, ni mesh ne garantissent une communication à longue distance hors réseau ; qualification dédiée nécessaire.

## Critères de validation
- Deux appareils physiques minimum, preuve de communication bidirectionnelle et d'absence d'écoute hors appui.
- Essais en changement d'application, écran verrouillé, Bluetooth, pertes réseau, permission retirée.
- Journal des résultats sans enregistrer de contenu vocal sans consentement.
- Statuts stricts : NON_CONFIGURÉ / LIMITÉ / PRÊT / CONNECTÉ / ERREUR ; pas de « prêt » sans preuves.

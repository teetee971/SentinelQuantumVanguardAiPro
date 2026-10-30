# Fonctionnalités absentes et limites de portée

Ce document distingue absence de développement, présence du code et validation matérielle manquante.

- Contacts Sentinel enrichis indépendants, profils sociaux et identités vérifiées : NON DÉVELOPPÉS dans le parcours livré. Le carnet Android et les photos locales ne prouvent pas l'identité.
- Transformation vocale d'appel SIM : NON PRISE EN CHARGE par l'architecture courante ; voir VOICE_LIMITATIONS.md.
- Restauration durable des images de brouillon MMS après mort du processus : NON DÉVELOPPÉE. La rétention mémoire de session traite les changements de configuration, pas un nouveau processus.
- Isolement système, quarantaine générale, antivirus exhaustif et télémétrie mondiale illustrés : capacités non développées ou NON QUALIFIÉES selon surface ; ne pas assimiler inventaire local, heuristique et interfaces à ces promesses.
- SMS/MMS, rôle Téléphone/SMS, InCallService, photos/contacts, notifications : implémentés en code, transport opérateur et One UI NON TESTÉS pour ce candidat.
- VPN, BLE, Voice Studio local, Security Lab et OSINT : modules préservés, validation matérielle/service NON EFFECTUÉE ici. La correction antérieure d'URL OSINT ne démontre pas 3/3 sources disponibles sur le téléphone aujourd'hui.

Détruire/recréer l'Activity pendant un envoi peut annuler le nettoyage UI après acceptation Android : un brouillon resté visible ne prouve pas l'échec de la soumission. Aucun renvoi automatique n'est ajouté ; consulter le fil et le statut Android avant tout nouvel essai. Les nouveaux gardes empêchent seulement une ancienne acceptation d'effacer un brouillon modifié dans la session courante.

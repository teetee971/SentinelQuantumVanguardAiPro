# Réputation communautaire téléphonique — frontière de confiance

Statut : socle Redis atomique, pas de service communautaire public modéré.

## Transaction de signalement

Le script Lua exécute comme une seule opération Redis :

1. rejet d'un nonce déjà vu ;
2. rejet d'une seconde contribution du même rapporteur pseudonymisé pour le même numéro et la même catégorie pendant sept jours ;
3. création des gardes anti-doublon ;
4. définition de la première observation si absente ;
5. mise à jour de la dernière observation ;
6. incrément du total et de la catégorie ;
7. expiration de la réputation après 180 jours.

Les clés utilisent des HMAC. Aucun numéro ou adresse IP brute n'est inscrit dans Redis.

## Ce que ce contrôle ne résout pas

- appareils multiples ou réseaux de robots ;
- collusion et brigading ;
- faux signalements coordonnés ;
- attribution de l'identité ;
- portabilité de l'opérateur ;
- modération, contestation et suppression RGPD ;
- biais des réseaux partagés, susceptibles de sous-compter plusieurs utilisateurs.

## Condition de publication

Les compteurs ne deviennent une réputation publique qu'après mise en place d'une modération humaine, de seuils multi-sources, d'un droit de contestation, d'un journal auditable et d'une publication signée. Un rapport isolé ne doit jamais bloquer automatiquement un appel.

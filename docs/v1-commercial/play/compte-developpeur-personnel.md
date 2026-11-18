# Compte développeur personnel · obligations lues

**18 novembre 2026. Document de préparation.** La personne a confirmé qu’elle n’a ni société ni entreprise individuelle immatriculée. Déclarations inscrites : Thierry Naud, 227 rue du Docteur Ledentu, Morne-à-l’Eau, Guadeloupe, France, téléphone +33 6 52 21 58 72, e-mail SentinelQuantumVanguardAiPro@gmail.com. La date de création du compte Play n’est pas dans le dépôt. Ne pas l’inventer.

La situation décrite correspond au type de compte personnel de Google Play. Un compte organisation est prévu pour une organisation, et il est exigé pour certaines applications financières, de santé, gouvernementales ou qui utilisent `VpnService`. Le profil V1 retire le service VPN. S’il revenait dans l’application publiée, le type de compte devrait être revu. [Choisir le type de compte](https://support.google.com/googleplay/android-developer/answer/13634885).

## Identité affichée

Pour créer un compte personnel, Google demande un nom de développeur, un nom légal, une adresse légale, un e-mail de contact, un numéro de téléphone et un e-mail de développeur affiché sur Play. Le nom légal et l’adresse viennent du profil Google Payments lié au compte. Google affiche le nom légal, le pays et l’e-mail développeur. L’adresse complète est affichée si l’application est monétisée. Cette version n’est pas monétisée : aucun abonnement, aucun achat intégré, aucun paiement dans l’application. [Informations pour créer le compte](https://support.google.com/googleplay/android-developer/answer/13628312).

Le profil Payments sert ici à l’identité du compte. Un compte marchand, nécessaire pour encaisser des ventes Play, n’est pas exigé par l’offre gratuite confirmée. [Lier un profil de paiement pour vendre](https://support.google.com/googleplay/android-developer/answer/3092739).

Des frais d’inscription du compte développeur peuvent être demandés par Google au moment de l’ouverture. Ce n’est pas un prix de l’application. [[À COMPLÉTER : le compte existe-t-il déjà, et ces frais ont-ils été payés ?]]

## Vérifications avant publication

Pages lues le 18 novembre 2026 :

- pièce d’identité officielle et profil Payments pour le nom légal et l’adresse : [vérifier l’identité](https://support.google.com/googleplay/android-developer/answer/10841920) ;
- e-mail développeur confirmé par code ;
- accès à un appareil Android réel via l’application Play Console, pour un compte personnel : [vérification de l’appareil](https://support.google.com/googleplay/android-developer/answer/14316361) ;
- pour un compte personnel créé après le 13 novembre 2023, test fermé avec au moins 12 testeurs inscrits sans interruption pendant les 14 jours précédant la demande d’accès production : [exigences de test](https://support.google.com/googleplay/android-developer/answer/14151465).

[[À COMPLÉTER : date de création du compte. Si elle est antérieure au 13 novembre 2023, l’obligation des 12 testeurs ne s’applique pas d’après la page citée. Le confirmer dans la Console.]]

[[À COMPLÉTER : identité, e-mail, téléphone et appareil déjà vérifiés, ou non.]]

## Déclarations de l’application gratuite

Même sans paiement ni publicité, la Console demande des formulaires. Réponses proposées pour V1, à ne soumettre qu’après contrôle de l’AAB signé :

| Formulaire | Réponse proposée |
| --- | --- |
| Annonces | Non. [Préparer la revue](https://support.google.com/googleplay/android-developer/answer/9859455). |
| Fonctions financières | L’application n’en fournit aucune. [Financial features](https://support.google.com/googleplay/android-developer/answer/13849271). |
| Compte dans l’application | Aucun. Pas d’URL de suppression de compte tant que cela reste vrai. |
| Politique de confidentialité | URL publique de la politique V1, après avoir inscrit le nom légal, le pays et l’e-mail qui seront affichés. |
| Data safety | Voir [data-safety-v1.md](data-safety-v1.md). Les pistes de test fermé, ouvert et production exigent le formulaire. |

Le nom légal affiché par Google doit apparaître dans la politique, ou la politique doit nommer l’application. Les brouillons nomment Thierry Naud et Sentinel Phone Core. Le nom affiché dans la Console n’a pas été relu.

## Ce que ce document ne fait pas

Il ne crée pas un statut d’entreprise. Il ne choisit pas une base juridique. Il ne déclare pas que le compte est déjà vérifié, ni que les 12 testeurs sont réunis, ni que Google a accepté l’application.

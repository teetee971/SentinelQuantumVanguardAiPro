# Google Play Data Safety — feuille de préparation Sentinel

> Statut : document de préparation interne. Ce fichier n’est pas une déclaration Play Console soumise et ne doit pas être présenté comme telle.

## Principe

La déclaration finale doit être faite à partir de l’APK/AAB exact soumis au Play Store et des services réellement activés en production. Un composant présent dans le code mais non provisionné ne doit pas être décrit comme un flux de production déjà actif ; inversement, tout flux réseau réellement activé doit être déclaré avant publication.

## Données et flux actuellement prévus

| Domaine | Données | Traitement | Réseau | État avant lancement |
| --- | --- | --- | --- | --- |
| Contacts / Caller ID local | contacts sélectionnés/consultés via Android | local, rôle/permission Android | non, sauf enrichissement Caller Reputation opt-in distinct | vérifier sur artefact final |
| SMS/MMS | contenu, numéros, métadonnées SMS/MMS lorsque Sentinel détient `ROLE_SMS` | fournisseur SMS Android + traitement local | MMS peut utiliser le réseau opérateur ; autres enrichissements seulement si explicitement activés | tests physiques + formulaire Play requis |
| Journal d’appels | métadonnées exposées par Android avec rôle/permission | local | non par défaut | vérifier sur artefact final |
| Veille OSINT | requêtes vers sources publiques | cache local | HTTPS vers sources publiques | actif |
| Caller Reputation opt-in | numéro entrant normalisé + contexte strictement nécessaire | service distant facultatif | oui après activation explicite | déclarer si présent dans la release |
| Studio voix — aperçu | court échantillon microphone | cache privé temporaire, supprimé à la fermeture | non | intégré |
| Appel Sentinel VoIP | audio microphone transformé | traitement temps réel puis transmission WebRTC | oui, vers infrastructure d’appel Sentinel/LiveKit réellement provisionnée | **non commercialisable tant que service non provisionné et déclaration non finalisée** |
| Appel vers numéro classique | audio transformé + métadonnées techniques nécessaires à l’établissement d’appel | WebRTC puis passerelle VoIP/PSTN | oui | **non commercialisable tant que fournisseur/gateway/retention/contrats non finalisés** |

## Exigences avant activation du service d’appel

- documenter l’opérateur LiveKit/RTC réellement utilisé en production et sa région d’hébergement ;
- documenter la passerelle VoIP/PSTN, les métadonnées d’appel traitées et les éventuels journaux de facturation ;
- définir explicitement si un enregistrement serveur est impossible/désactivé ; s’il devient possible, l’ajouter à la politique et au consentement avant activation ;
- confirmer les durées de conservation des métadonnées côté backend et fournisseurs ;
- confirmer le chiffrement en transit réellement fourni par l’architecture déployée ;
- mettre à jour la politique de confidentialité publique et le formulaire Data Safety Play avec le comportement réellement déployé ;
- refaire la revue à chaque changement de SDK RTC, fournisseur PSTN ou politique de rétention.

## Interdictions de vérité produit

- ne pas déclarer qu’un appel SIM natif est transformé ;
- ne pas déclarer que l’audio d’appel reste entièrement local lorsqu’un appel Sentinel VoIP est activé ;
- ne pas déclarer « aucune donnée partagée » si l’audio ou les métadonnées nécessaires traversent un fournisseur RTC/PSTN ;
- ne pas ouvrir le checkout tant que l’infrastructure, la documentation, la Data Safety et les tests physiques ne sont pas validés.

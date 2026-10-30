# Services : disponibilité constatée et limites

## OSINT : régression reproduite et correction bornée

Les sources de `OsintSource` pointaient vers `https://www.cert.ssi.gouv.fr/{alerte,avis,cti}/feed/`. La configuration OkHttp d’`OsintRepository` refuse les redirections HTTP et HTTPS pour rester sur les origines explicitement choisies. Les sondes publiques de cette session donnent **302 sur les trois URL** avec Location vers `https://cert.cyber.gouv.fr/...` : les trois réponses sont donc rejetées par le client, ce qui explique un 0/3 sans erreur réseau détaillée.

Les trois nouvelles URL canoniques sont utilisées directement, sans activer `followRedirects` ni élargir les sources à des hôtes arbitraires. Chaque URL répond **200**, racine XML `rss`, **40 items** à la sonde hôte. Les deux tests d’endpoint conservent schéma HTTPS, origine canonique et chemins distincts. Les protections de taille, de nombre d’entrées, de cache complet/partiel, de dates inconnues et de liens sûrs ne sont pas modifiées.

Preuves : `evidence/services/probes.json`, `canonical-probes.json`, en-têtes et corps RSS publics ; test rouge sur l’ancienne origine, test corrigé et suite app complète. **Ces sondes hôte ne prouvent pas que le téléphone obtient 3/3 sur son réseau** : reprendre le test hors-ligne/cache, nouvelle récupération, filtrage et ouvertures de liens sur S24+.

## PTT : vivant ≠ prêt

Sondes réellement exécutées sans jeton ni création de session :

| Endpoint | HTTP | Sens |
|---|---:|---|
| https://sentinel-voice-api.onrender.com/health/live | 200 | Le processus HTTP répond |
| https://sentinel-voice-api.onrender.com/health/ready | 503 | Les prérequis runtime ne sont pas satisfaits ; service PTT non prêt |

Le corps ready reste volontairement générique (`Voice service unavailable`). **Aucune connexion PTT, création de session ou transformation distante réussie n’est revendiquée.**

Après confirmation utilisateur de « My Workspace », le plugin Render a identifié `sentinel-voice-api`, branche de déploiement `main`, plan courant `free`, endpoint de healthcheck `/health/live`, pas de prévisualisation de PR. Ce healthcheck explique qu’un processus soit considéré vivant alors que sa readiness retourne 503. La lecture des journaux niveau error n’a retourné **aucune ligne** dans la fenêtre fournie ; l’absence de ligne ne prouve pas l’absence de défaut. Aucune valeur de secret n’a été demandée/imprimée ; aucun changement d’environnement, redéploiement, redémarrage ou montée de plan n’a été effectué.

Le code `require_runtime()` renvoie 503 si le service est désactivé, si la configuration comporte une erreur, si identité/émetteur LiveKit manquent ou si Redis n’est pas disponible ; `/health/ready` vérifie ensuite Redis. **Ces branches sont des causes possibles, pas une preuve de laquelle se produit dans le déploiement.** Les outils connectés n’exposent pas ici une lecture des variables limitée aux seuls noms ; ne pas inventer qu’une clé précise manque. Le propriétaire doit vérifier les noms/prérequis du runbook sans publier leurs valeurs.

Le backend a été testé localement : **23 tests réussis**, avec doubles Redis explicites. Ce résultat valide les contrats de code, pas Redis/LiveKit/Auth0 en production. Le client conserve son rejet des réponses non réussies. L’interface traduit maintenant HTTP503,401,403 en messages lisibles et ne montre plus un texte d’exception arbitraire pour l’échec de connexion.

## Appel mobile natif et aperçu local

- L’aperçu Voice Studio utilise un enregistrement/traitement audio local sur action explicite : implémentation présente, écoute et matériel non validés dans cette session.
- La session PTT est un transport LiveKit séparé avec jetons éphémères et un bouton push-to-talk ; elle n’est pas l’audio de Telecom.
- `carrierCallActive()` bloque l’ouverture de la session pendant un appel mobile natif. **Transformer ou réinjecter l’audio d’un appel SIM n’est pas pris en charge par l’architecture livrée**. Ce n’est pas une option serveur à déverrouiller. Aucune passerelle PSTN/VoIP ni contournement d’Android n’est ajouté pour prétendre corriger ce point.
- Le droit PTT, l’authentification, la transformation bi-appareil et la fermeture du microphone doivent encore être testés sur un environnement autorisé réellement prêt, sans appel vers un numéro inconnu ni dépense automatique.

## Critères de reprise

1. Vérifier le runbook `docs/VOICE-PRODUCTION-READINESS-RUNBOOK.md` et la configuration autorisée (service activé, identité/JWKS, émetteur LiveKit, Redis joignable, droits).
2. Obtenir ready200 avant de qualifier une session. Garder des preuves d’état sans jetons ni identifiants de personne.
3. Avec deux comptes de test autorisés, exercer connexion/PTT bidirectionnel transformé, relâchement, interruptions, expiration et fermeture ; aucun PASS n’est fourni ici.
4. Les réparations de configuration qui changent un déploiement ou nécessitent un service payant demandent une décision explicite distincte ; cette mission reste en diagnostic et code client.

## Nouvelle sonde en fin de qualification

À 2026-10-29T20:08:04 UTC : live200 et ready503 à nouveau constatés, sorties curl0. Réponses et horodatages : `evidence/services/voice-final-probes.json`. Aucun POST/session ni changement Render effectué.

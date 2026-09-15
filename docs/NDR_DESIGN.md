# NDR Sentinel : conception défensive supervisée

Statut au 15 septembre 2026 : **conception, non opérationnel**. Suivi : [issue #438](https://github.com/teetee971/SentinelQuantumVanguardAiPro/issues/438). Aucun capteur réseau, modèle de détection NDR entraîné ou mécanisme de quarantaine n’est livré par ce document.

## Objectif et périmètre

Détecter plus tôt des comportements suspects sur les réseaux explicitement autorisés, notamment les déplacements entre machines, identités, services et segments. Le NDR complète l’EDR et le SIEM ; il ne garantit pas la visibilité sur tous les mouvements latéraux. Les techniques de cette famille sont décrites par [MITRE ATT&CK, TA0008](https://attack.mitre.org/tactics/TA0008/).

Premier produit envisagé : add-on B2B local ou auto-hébergé. Il ne conditionne pas la gratuité de l’annuaire téléphonique. Les premiers essais doivent pouvoir utiliser des traces de laboratoire et des règles locales, sans abonnement LLM. Render gratuit et Upstash ne sont pas dimensionnés ici pour recevoir les flux réseau bruts : l’architecture de départ reste locale. Aucun coût nul d’exploitation à grande échelle n’est garanti.

## Détecteurs spécialisés et fenêtres de temps

Les fenêtres ci-dessous sont des choix de conception à mesurer, pas des promesses de latence.

| Échelle proposée | Observations | Sortie attendue |
|---|---|---|
| 5 secondes | rafales, nouvelles connexions, scans observés | événement borné avec indices et qualité de collecte |
| 5 minutes | séquences DNS, authentification, administration distante | hypothèse corrélée et alternatives explicites |
| 1 heure et référence historique | nouveaux chemins inter-segments, écarts aux habitudes | anomalie contextualisée, couverture et incertitude |

Commencer par des règles déterministes et statistiques compréhensibles. Ajouter des modèles approuvés seulement si leurs gains sont démontrés sur des traces distinctes de l’entraînement. La fusion conserve les preuves, leur dépendance, la fraîcheur et les désaccords : plusieurs modèles alimentés par la même observation ne comptent pas comme plusieurs preuves indépendantes.

Un LLM éventuel rédige une synthèse à partir de références vérifiables ; il ne décide ni de la sévérité finale à lui seul, ni d’une action système. Les champs textuels provenant du réseau sont non fiables et ne doivent jamais devenir des instructions d’outils.

## Collecte et frontières de confiance

- Capteurs passifs autorisés : métadonnées de flux, DNS, TLS, DHCP et journaux d’authentification. Aucun scan actif implicite, interception TLS ou collecte généralisée de contenu.
- Schéma d’événement borné : identifiant, organisation, capteur, horodatage, séquence, type, entités pseudonymisées, mesures, référence de preuve et version du schéma. Limites de taille, signature, anti-rejeu et attribution au bon locataire avant corrélation.
- Graphe d’entités pour relier appareil, identité, service et segment. Tenir compte de DHCP/NAT, dérive d’horloge, pertes et changements d’adresse ; s’abstenir d’attribuer quand la résolution est insuffisante.
- Files bornées, backpressure et budgets CPU/mémoire. Une perte de télémétrie produit une alerte de couverture dégradée, jamais un état « réseau sûr ».
- Minimisation, chiffrement, contrôle d’accès, séparation des organisations et rétention configurable. Définir les durées et habilitations avec l’organisation avant tout pilote ; pas d’envoi de traces client à un service IA externe par défaut.

## Réponse et contrôle humain

Réutiliser, après validation d’intégration, les frontières de `security/soc/passive-pipeline.js` et du Decision Plane. Le socle existant n’est pas une preuve de NDR déployé.

Par défaut : observation, dossier SOC et proposition de playbook. Pour une réponse perturbatrice, exiger une politique explicite, une preuve fraîche, une simulation, une approbation humaine liée au plan exact, une cible vérifiée, une durée limitée, un mécanisme d’arrêt et un retour arrière testé. Aucune contre-attaque ni quarantaine déclenchée par un LLM. Une approbation manquante ou un service de vérification indisponible interdit l’action.

## Validation avant toute disponibilité commerciale

1. Laboratoire reproductible : trafic normal, administration légitime, incidents simulés autorisés, horloges décalées et collecteurs indisponibles.
2. Détection multi-échelle : règles de référence, jeu de test séparé par période et environnement, calibration et abstention hors distribution.
3. Mesures par scénario et organisation : précision, rappel, faux positifs par jour, délai entre événement et alerte, couverture, consommation et volume de données. Fixer les seuils d’acceptation avant le pilote.
4. Pilote fermé en observation : revue des alertes par analystes, désaccords tracés, sans blocage réseau.
5. Réponse assistée : tests de refus, permissions, rejeu, approbation expirée, changement de cible, arrêt et restauration réelle.

L’IA n’est pas intrinsèquement impossible à contourner. Le [NIST AI 100-2 E2025](https://csrc.nist.gov/pubs/ai/100/2/e2025/final) couvre notamment évasion et empoisonnement. Prévoir des évaluations adversariales, la provenance des données, un registre de modèles signés, une validation avant promotion et une détection de dérive ; la confidentialité de l’architecture n’est pas une garantie de sécurité.

La mention « NDR opérationnel » restera interdite tant que collecte réelle autorisée, mesures du pilote, exploitation, contrôles de confidentialité et essais de réponse ne sont pas établis.

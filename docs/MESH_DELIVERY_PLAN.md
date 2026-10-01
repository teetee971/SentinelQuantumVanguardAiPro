# Plan de livraison — connectivité sécurisée et VPN Mesh

Révision : 1 octobre 2026. Architecture détaillée : [SENTINEL-MESH-ZERO-TRUST.md](security/SENTINEL-MESH-ZERO-TRUST.md). Ce plan distingue les fondations du dépôt d'un service effectivement déployé.

## Objectif

Relier personnes, appareils, services, workloads et agents IA par un overlay WireGuard. L'autorisation associe une identité vérifiée, groupes, tags, posture, environnement et ressource ; une adresse IP seule n'autorise rien. L'accès à Internet exige en plus un nœud de sortie : un Private Mesh n'est pas un VPN Internet complet.

## Fondations et dépendances

| Composant | Source / état du dépôt | Validation restant à produire |
| --- | --- | --- |
| Autorisation et enrôlement | network/mesh : politique, control plane, credentials nœuds, invitations bornées et révocation | Déploiement avec identités réelles, scénarios autorisés/refusés, vol/révocation de credential et persistance. |
| Transport | Négociation de chemin, probe NAT et relay UDP pair-scoped | Deux clients réels derrière NAT distincts ; mapping du socket WireGuard, passage direct/relais, expiration, IPv4/IPv6. Le client Android ne raccorde pas encore le relay applicatif comme endpoint WireGuard. |
| Android Private Mesh | Identité WireGuard protégée au repos, plan de routes hôtes et arbitrage du mode VPN | Enrôlement, tunnel, trafic autorisé et arrêt/révocation sur appareil réel. Les routes par défaut et l'injection DNS restent exclues du Private Mesh actuel. |
| Identité humaine | OIDC/PKCE et vérification de tokens présents ; SCIM en lecture seule | Échange de code, sessions, réauthentification, révocation et interop réelle. SAML2 reste planifié ; une mention au registre ne prouve pas une intégration compatible. |
| Identité workloads / IA | Fondations SPIFFE, vérification X.509, bundles et transport Workload API | Interop SPIRE réelle, rotation, révocation/CRL, reconnexion et refus d'identités hors confiance. |
| Exploitation | deploy/mesh : HTTPS, service interne, probe et relay | DNS/domaine réel, secrets hors dépôt, tests externes, sauvegarde/restauration, supervision et procédures d'incident. |

## Jalons de livraison

1. **Contrat d'identité et de politique :** enrôlement après authentification, refus par défaut, moindre privilège, durée et portée bornées ; retirer les droits après révocation et changement de groupe/tag.
2. **Pilote privé à deux clients :** HTTPS vérifié, transport direct puis fallback mesuré, persistance et coupure testées. Conserver commit, configuration sanitisée, scénarios et résultats.
3. **Pilote Android :** consentement VPN, exclusivité Mesh/VPN Internet, absence de route ou DNS inattendu, arrêt effectif et révocation testés.
4. **Haute disponibilité :** panne d'un relais, du control plane et d'une instance de stockage ; mesurer interruption, récupération et comportement de politiques expirées. Ne pas annoncer « aucun point de défaillance unique » avant cette preuve.
5. **Intégrations :** valider un manifeste par fournisseur/version et par scénario. Linux/Windows/macOS, routeurs, Kubernetes, clouds, IoT et CI/CD demandent leurs clients/adaptateurs et une matrice matérielle/logicielle ; la compatibilité avec tous les matériels n'est pas revendiquée.
6. **Sortie Internet et offre entreprise :** traiter séparément exit nodes, DNS, isolation des tenants, observabilité, quotas, mises à jour, rotation de clés et réversibilité avant distribution.

## Usages et limites

| Usage | Condition de livraison |
| --- | --- |
| Domicile / accès distant / entreprise | Client compatible, ressources autorisées, identité et révocation testées ; accès Internet via exit node validé. |
| Multi-cloud / inter-régions / Kubernetes | Identités workloads, routes, conflits de sous-réseaux, isolation et policies testés. Un registre d'intégrations ne suffit pas. |
| PAM | Couche réseau identitaire ; gestion des secrets, enregistrement de sessions, accès d'urgence et approbation nécessitent un PAM ou des capacités supplémentaires validées. |
| Edge / IoT | Agent ou routeur compatible, rotation/révocation, mises à jour et reprise après déconnexion testées. |
| CI/CD | Fédération de workload à portée et durée bornées ; aucun credential administrateur permanent dans un job. |
| Agents IA | Identité propre au workload/agent, ressources bornées, journalisation et révocation. L'accès réseau ne remplace pas l'autorisation applicative ni les gates humains des actions critiques. |

## Gate d'état

« Configuré » signifie paramètres présents ; « authentifié » signifie identité vérifiée ; « connecté » signifie tunnel établi ; « trafic vérifié » exige un échange autorisé observé. « Disponible en production » exige aussi exploitation, récupération et matrice validées. Les latences, débits et délais de révocation sont mesurés, sans promesse universelle.

Le premier déploiement est bloqué tant que l'hôte, le domaine, les identités et les secrets de service n'ont pas été fournis via le mécanisme de configuration approprié. Aucune infrastructure payante ou clé ne peut être déduite du nom du projet.

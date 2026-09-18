# Sentinel Mesh Zero Trust — architecture cible

## Statut

FOUNDATION. Ce document définit l'architecture et les invariants de sécurité. Il ne prétend pas qu'un service mesh de production, un control plane multi-tenant ou plus de 100 intégrations sont déjà opérationnels.

## Objectif

Transformer Sentinel en plateforme de connectivité sécurisée capable de relier, sur un même modèle de politique :

- utilisateurs et appareils ;
- réseaux domestiques et petites structures ;
- sites d'entreprise et accès distant ;
- workloads VM, conteneurs et Kubernetes ;
- environnements cloud et on-premise ;
- Edge et IoT ;
- pipelines CI/CD ;
- accès privilégiés (PAM) ;
- services et agents IA.

WireGuard constitue le data plane chiffré. L'autorisation ne doit pas être dérivée d'une adresse IP seule : le control plane associe identité, groupes, tags, posture appareil, identité de workload, environnement et ressource à une décision explicite.

## Plans séparés

### Identity plane

Humains :
- OIDC en priorité ;
- SAML 2.0 pour compatibilité entreprise ;
- SCIM 2.0 pour provisioning/deprovisioning ;
- MFA et signaux de posture fournis par l'IdP ou le device-management quand ils sont disponibles.

Workloads et agents IA :
- SPIFFE ID comme identité portable ;
- SVID courts et rotatifs ;
- SPIRE comme implémentation possible, sans dépendance propriétaire obligatoire ;
- séparation stricte des trust domains production, staging, régions ou organisations.

### Control plane

Responsabilités :
- inventaire nodes/users/workloads/resources ;
- attribution de tags ;
- compilation des politiques ;
- distribution de configurations WireGuard ;
- rotation/révocation des clés ;
- découverte des peers ;
- audit append-only ;
- health et état de connectivité ;
- coordination NAT traversal/relay lorsque le direct peer-to-peer échoue.

Le control plane ne doit jamais transporter les clés privées WireGuard d'un device en clair.

### Data plane

- WireGuard maintenu par plateforme/bibliothèque reconnue ;
- peer-to-peer quand possible ;
- relay chiffré comme fallback ;
- full tunnel pour VPN Internet quand demandé ;
- split routing par ressource pour accès privé ;
- IPv4 et IPv6 traités explicitement ;
- DNS lié à la politique et protégé contre les fuites ;
- aucun déchiffrement HTTPS par Sentinel.

## Modèle d'accès

Une règle peut utiliser :
- type de sujet : user, device, workload, agent ;
- subject ID ;
- groupes ;
- tags ;
- niveau de confiance/posture appareil ;
- tags de ressource ;
- environnement ;
- action.

Invariants :
1. default deny ;
2. deny explicite prioritaire ;
3. aucune règle implicite "même LAN = confiance" ;
4. aucune autorisation uniquement parce qu'une IP appartient à une plage ;
5. révocation rapide des identités et clés ;
6. chaque décision importante est auditable ;
7. un agent IA ne reçoit jamais plus de privilèges que le tool/resource policy lui accorde.

Le moteur de fondation est dans `network/mesh/policy-engine.js`.

## Architecture mesh

Le modèle cible est un overlay mesh au-dessus du réseau existant. Le déploiement ne nécessite donc pas de remplacer les switches, routeurs ou clouds existants.

Topologies :
- endpoint <-> endpoint direct ;
- endpoint <-> subnet router ;
- workload <-> workload ;
- site <-> site ;
- mobile <-> exit gateway ;
- Kubernetes <-> cloud/on-prem ;
- agent IA <-> outils autorisés.

Un relay n'est utilisé que lorsque le direct path n'est pas possible. Le control plane ne doit pas devenir un point de passage obligatoire des paquets applicatifs.

## VPN Internet et choix de pays

Le VPN grand public/entreprise réutilise le même data plane mais avec une ressource de type exit gateway.

Un pays n'est "disponible" que si au moins une passerelle réelle a :
- health réseau vert ;
- full tunnel IPv4/IPv6 vérifié ;
- DNS épinglé ;
- tests de fuite réussis ;
- IP de sortie géolocalisée dans le pays annoncé ;
- clés et révocation opérationnelles.

La UI doit continuer d'afficher PLANNED tant que ces preuves ne sont pas présentes.

## Home / entreprise / multi-cloud

### Maison
- téléphone, ordinateur, NAS, Home Assistant, routeur OpenWrt ;
- accès privé sans ouvrir de ports publics ;
- exit node domestique optionnel ;
- segmentation IoT.

### Entreprise
- groupes IdP ;
- tags d'appareils ;
- posture et révocation ;
- subnet routers ;
- accès privilégié SSH/RDP ;
- journalisation et séparation des rôles.

### Cloud / Kubernetes
- identité workload via SPIFFE ;
- agents/routers par cluster/VPC/VNet ;
- policies inter-environnements ;
- pas de secret statique dans les images de conteneur ;
- federation entre trust domains uniquement lorsqu'elle est explicitement configurée.

### CI/CD
- identités éphémères ;
- OIDC workload federation quand supporté ;
- accès aux ressources de build limité dans le temps ;
- pas de clé VPN longue durée dans les logs ou variables publiques.

### IA
- chaque agent est une identité distincte ;
- accès réseau découplé de l'autorité d'outil ;
- policy explicite par ressource/action ;
- aucune capacité d'administration réseau déduite du simple fait qu'un modèle est approuvé ;
- kill/revoke indépendant d'une session modèle.

## PAM

La connectivité ne remplace pas à elle seule un PAM complet. Sentinel peut fournir la couche réseau identitaire et :
- limiter SSH/RDP/admin aux groupes/tags autorisés ;
- exiger une posture de device ;
- délivrer une fenêtre d'accès limitée ;
- journaliser la décision.

La gestion de secrets, l'enregistrement de sessions et les workflows d'approbation doivent rester des capacités séparées ou être intégrés à un PAM externe.

## Edge et IoT

Le support matériel doit être basé sur des exigences techniques, pas une promesse "tout matériel" :
- WireGuard ou routeur/subnet-router Sentinel disponible ;
- CPU/OS capables de maintenir le tunnel ;
- stockage de clés protégé autant que la plateforme le permet.

Les équipements incapables d'exécuter Sentinel peuvent être représentés derrière un subnet router, sans leur attribuer une identité cryptographique qu'ils ne possèdent pas.

## Intégrations

Objectif d'échelle : dépasser 100 intégrations répertoriées à terme, sans créer 100 implémentations d'authentification différentes.

Standards pivots :
- OIDC ;
- SAML 2.0 ;
- SCIM 2.0 ;
- SPIFFE/SPIRE ;
- WireGuard ;
- REST/webhooks ;
- OIDC workload federation.

Le registre initial `network/mesh/integration-registry.js` contient uniquement des entrées FOUNDATION ou PLANNED. Aucune entrée n'est VALIDATED dans cette première passe.

Catégories cibles :
- IdP/SSO et annuaires ;
- MDM/UEM ;
- AWS, Azure, GCP et clouds européens ;
- Kubernetes et distributions ;
- GitHub/GitLab/Jenkins/CI ;
- Terraform/IaC ;
- SIEM/SOC ;
- PAM ;
- virtualisation ;
- NAS/home lab ;
- routeurs/edge ;
- IoT/MQTT ;
- observabilité ;
- secrets/KMS/HSM ;
- AI gateways et runtimes.

## Fournisseurs d'identité

La compatibilité avec "des dizaines d'IdP" doit être obtenue d'abord via OIDC/SAML/SCIM et testée fournisseur par fournisseur. Une mention commerciale de compatibilité ne doit apparaître qu'après test interop réel.

## Haute disponibilité

Pour éviter un nouveau point de défaillance unique :
- control plane répliqué ;
- base avec stratégie de quorum/backup adaptée ;
- relays multiples par région ;
- clés et metadata distribuées de façon bornée ;
- les tunnels déjà établis doivent survivre autant que possible à une panne temporaire du control plane ;
- la révocation doit redevenir effective dès que la connectivité de contrôle revient.

## Phases

### Phase 1 — fondation
- policy engine identité-aware ;
- integration registry ;
- architecture et contrats ;
- aucun claim de mesh production.

### Phase 2 — control plane minimal
- enroll device ;
- keys publiques uniquement ;
- tags/groupes ;
- policy compiler ;
- peer discovery ;
- audit ;
- révocation.

### Phase 3 — premier mesh réel
- deux appareils + relay fallback ;
- NAT traversal ;
- tests Linux/Android ;
- mesure latence/reconnexion ;
- tests de perte du control plane.

### Phase 4 — identité entreprise
- OIDC ;
- SCIM ;
- mapping groupes ;
- session/reauth ;
- device posture ;
- tests avec plusieurs IdP.

### Phase 5 — workloads
- SPIFFE/SPIRE ;
- Kubernetes ;
- cloud federation ;
- agents IA ;
- credentials courts.

### Phase 6 — catalogue
- certification interne connecteur par connecteur ;
- matrice versions/capacités ;
- plus de 100 entrées seulement quand les statuts et tests sont traçables.

## Critère "réellement activé"

Une capacité ne peut passer à ACTIVE que si :
- code fusionné ;
- tests unitaires/intégration verts ;
- preuve réseau réelle ;
- sécurité des clés vérifiée ;
- comportement de révocation testé ;
- documentation/roadmap alignée ;
- aucune dépendance à des secrets fictifs ou endpoints placeholders.


## Persistance du control plane

Le service `npm run mesh:serve` fonctionne sans persistance par défaut. Pour un état durable :

- `MESH_STATE_PATH` définit le fichier d'état local ;
- `MESH_STATE_SECRET` doit contenir au moins 32 caractères et rester hors dépôt ;
- chaque snapshot est authentifié par HMAC-SHA256 ;
- l'écriture utilise un fichier temporaire puis un renommage atomique ;
- les permissions du fichier sont limitées à l'utilisateur du service ;
- un état falsifié, corrompu ou incohérent provoque un refus de démarrage ;
- aucune clé privée WireGuard ne peut être importée ou restaurée dans le control plane.

Cette persistance locale n'est pas encore une base distribuée multi-instance. La réplication, le consensus/quorum et les sauvegardes distantes restent des étapes de production distinctes.

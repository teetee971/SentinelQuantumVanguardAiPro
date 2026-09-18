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


## Coordination de transport Mesh

Le control plane intègre désormais un coordinateur de transport borné.

Fonctions disponibles :
- annonces d'endpoints par nœud avec TTL court ;
- priorité au chemin direct lorsque les deux annonces sont fraîches ;
- sélection d'un relay disponible lorsque le direct est indisponible ;
- préférence régionale facultative ;
- exclusion des relays `offline`, `draining` ou non déclarés ;
- refus de tout chemin si le nœud source ou cible est inconnu/révoqué ;
- refus de tout chemin lorsque la policy Zero Trust n'autorise pas `connect`.

Les annonces d'endpoints sont actuellement administrées via l'API authentifiée du control plane. Elles ne constituent pas encore un protocole NAT traversal autonome sur Internet.

### Ce qui reste à implémenter pour un vrai NAT traversal

- authentification par nœud distincte du token administrateur ;
- observation serveur de l'adresse source externe ;
- échanges de candidats directs ;
- keepalive borné ;
- tentative de hole punching UDP ;
- détection de NAT symétrique ;
- service relay chiffré réellement déployé ;
- mesures de latence et de santé ;
- tests réseau réels Android/Linux/IPv4/IPv6 ;
- rotation et révocation des credentials de relay.

Aucun statut `ACTIVE` ne doit être affiché pour le transport P2P ou relay tant que ces preuves réseau ne sont pas réunies.


## Authentification des nœuds et candidats NAT

Les nœuds Mesh disposent désormais d'un credential distinct du token administrateur du control plane.

Invariants :
- credential aléatoire de 256 bits ;
- seul le hash SHA-256 du credential est persisté ;
- le token brut n'est retourné qu'à l'émission ;
- rotation = invalidation immédiate du token précédent ;
- révocation du nœud = révocation du credential ;
- authentification comparée en temps constant ;
- un nœud ne peut annoncer des candidats ou demander un chemin qu'en son propre nom.

Endpoints nœud :
- `POST /v1/node/transport/candidates` : annonce des endpoints locaux et ajout de l'adresse source observée par le serveur avec le port WireGuard déclaré ;
- `GET /v1/node/peers` : liste des peers autorisés par la policy Zero Trust ;
- `GET /v1/node/transport/path?target=...` : chemin direct ou relay uniquement si la cible est autorisée.

Cette couche ne constitue pas encore un STUN/ICE complet. L'adresse externe est observée sur la connexion de contrôle ; le port WireGuard reste déclaré par le nœud. Le prochain niveau de preuve réseau exige un service UDP dédié capable d'observer le mapping NAT réel du socket WireGuard et de tester le hole punching.


## Probe UDP de mapping NAT

Sentinel dispose désormais d'un service UDP de probe authentifié, distinct du control plane HTTP et distinct d'un futur relay de données.

Activation :
- `MESH_NAT_PROBE_ENABLED=true`
- `MESH_NAT_PROBE_HOST` définit l'adresse d'écoute ;
- `MESH_NAT_PROBE_PORT` définit le port UDP, par défaut 3479 ;
- un bind non loopback exige aussi `MESH_ALLOW_REMOTE_BIND=true`.

Le probe :
- accepte uniquement des paquets JSON bornés à 1024 octets ;
- authentifie le nœud avec son credential propre ;
- observe l'adresse IP et le port source UDP vus par le serveur ;
- associe ce mapping au nœud avec TTL court ;
- rejette l'usurpation de node ID ;
- n'utilise pas le token administrateur ;
- n'agit pas comme relay de trafic ;
- n'injecte pas automatiquement le mapping observé comme endpoint WireGuard.

Le nœud peut consulter son mapping via `GET /v1/node/nat-mapping` après authentification.

Important : ce mapping prouve le NAT du socket de probe. Il ne prouve le mapping du socket WireGuard que si le client utilise effectivement le même socket/port UDP ou une technique explicitement validée. Le vrai hole punching WireGuard reste à démontrer par tests réseau multi-NAT.


## Négociation de chemin direct / relay

Sentinel intègre désormais une machine d'état de négociation de chemin, séparée du transport WireGuard lui-même.

États :
- `NEGOTIATING` : candidats directs disponibles et essais en cours ;
- `DIRECT_ESTABLISHED` : un candidat autorisé a réussi ;
- `RELAY_REQUIRED` : tous les candidats directs ont échoué et un relay disponible existe ;
- `UNAVAILABLE` : aucun chemin direct réussi et aucun relay disponible.

Invariants :
- une session est créée uniquement pour une cible autorisée par la policy `connect` ;
- seuls les candidats inclus dans la session peuvent être déclarés comme testés ;
- un autre nœud ne peut pas piloter la session ;
- le fallback relay n'est sélectionné qu'après échec de tous les candidats directs ;
- les sessions sont bornées en nombre et en durée ;
- un keepalive maintient l'état de coordination, mais ne constitue pas lui-même un keepalive WireGuard ;
- aucun succès direct n'est déduit du simple fait qu'un endpoint existe : le client doit remonter un résultat réel de tentative.

Cette couche prépare le hole punching, mais n'exécute pas encore elle-même les paquets WireGuard ou le relay de données.


## Relay UDP borné — data plane de secours

Sentinel intègre désormais un relay UDP de secours strictement limité à deux nœuds déjà autorisés par la négociation Mesh.

Propriétés :
- aucune destination arbitraire fournie par le client ;
- une session lie exactement un nœud source et un nœud cible ;
- tokens relay distincts pour chaque pair ;
- tokens relay remis séparément, une seule fois, après authentification du nœud ;
- le relay ne connaît pas les clés privées WireGuard ;
- les payloads sont traités comme opaques et ne sont pas déchiffrés par Sentinel ;
- protection anti-rejeu par séquence monotone par direction ;
- TTL de session ;
- quota global par session ;
- limites paquets/seconde et octets/seconde ;
- taille maximale de datagramme/payload ;
- aucun mode proxy Internet générique ;
- relay désactivé par défaut.

Activation runtime :
- `MESH_RELAY_ENABLED=true`
- `MESH_RELAY_HOST` et `MESH_RELAY_PORT` configurent l'écoute UDP ;
- `MESH_RELAY_PUBLIC_ENDPOINT` publie l'endpoint externe annoncé aux pairs ;
- un bind non loopback exige `MESH_ALLOW_REMOTE_BIND=true`.

Quand tous les candidats directs ont échoué et que la négociation passe à `RELAY_REQUIRED`, le control plane crée une session relay. Chaque nœud récupère ensuite son propre credential via `POST /v1/node/relay/claim`.

Le transport relay implémenté est fonctionnel au niveau UDP applicatif et testé en boucle locale. Il ne constitue pas encore une preuve de fonctionnement multi-réseaux Internet, ni une preuve de débit/latence de production. Ces validations exigent deux clients réels derrière des NAT distincts et une instance relay déployée.


## Enrôlement Android sans secret administrateur

Le control plane fournit désormais un flux d'enrôlement one-shot afin qu'un appareil Android puisse recevoir son credential nœud sans jamais connaître `MESH_ADMIN_TOKEN`.

Flux :
1. un administrateur crée d'abord le nœud avec sa clé publique WireGuard ;
2. l'administrateur appelle `POST /v1/enrollment-invitations` ;
3. le serveur génère un code aléatoire de 256 bits, lié au node ID et au fingerprint de la clé publique ;
4. seul le hash SHA-256 du code est conservé côté serveur ;
5. le code expire rapidement et est limité à cinq essais ;
6. l'appareil appelle `POST /v1/enroll` avec son node ID, son code one-shot et le fingerprint attendu ;
7. après validation, le serveur émet le credential nœud normal ;
8. l'invitation est consommée et ne peut plus être rejouée.

Les invitations sont volontairement éphémères et non persistées : un redémarrage du control plane invalide les invitations encore en attente plutôt que de risquer de restaurer un secret d'enrôlement ancien.

Ce mécanisme réduit l'exposition du token administrateur mais ne remplace pas une preuve cryptographique de possession de la clé WireGuard. Une future évolution pourra ajouter une attestation d'appareil ou une preuve de possession séparée sans relâcher le caractère one-shot de l'invitation.


## OIDC générique — identité humaine

Le registre Mesh expose désormais un connecteur OIDC générique fondé sur Authorization Code + PKCE S256.

Contrôles actuellement implémentés :
- issuer HTTPS obligatoire ;
- userinfo, fragment et query interdits dans l'issuer configuré ;
- discovery sans suivi automatique des redirections ;
- metadata bornée en taille ;
- issuer retourné par la discovery strictement identique à l'issuer configuré ;
- authorization endpoint, token endpoint et JWKS URI en HTTPS ;
- hôtes explicitement allowlistés ;
- support `response_type=code` requis ;
- support PKCE `S256` requis ;
- génération cryptographique de `state`, `nonce` et `code_verifier` ;
- redirect URI HTTPS et allowlistée ;
- scope `openid` obligatoire.

Ce connecteur ne valide pas encore les ID Tokens et n'exécute pas encore l'échange de code contre token. Il reste donc au statut `foundation`, pas `validated`. Les fournisseurs individuels ne pourront être marqués compatibles qu'après tests d'interop réels avec leurs metadata, JWKS, claims et comportements de session.


## Validation OIDC ID Token

Le noyau OIDC dispose désormais d'un vérificateur cryptographique d'ID Token.

Contrôles implémentés :
- JWT borné en taille ;
- algorithmes explicitement autorisés uniquement (`RS256` et `ES256`) ;
- sélection de clé par `kid` ;
- JWKS HTTPS et hôte explicitement allowlisté ;
- réponse JWKS bornée et nombre de clés limité ;
- signature cryptographique vérifiée avec la clé JWK ;
- `iss` strictement lié aux metadata OIDC ;
- `aud` lié au client ID ;
- `azp` exigé lorsque plusieurs audiences sont présentes ;
- `nonce` strictement vérifié ;
- `sub` obligatoire et borné ;
- `exp`, `nbf` et `iat` validés avec une dérive d'horloge bornée ;
- durée incohérente `exp <= iat` refusée ;
- substitution d'algorithme refusée avant traitement de signature.

Le vérificateur ne persiste pas le token brut. Il retourne uniquement une identité normalisée et quelques claims bornés utiles à la politique.

Le flux OIDC reste au statut `foundation` tant que l'échange du code d'autorisation, la gestion de session/reauth, la révocation et les tests d'interop avec des fournisseurs réels ne sont pas terminés.

## Adressage overlay Mesh

Chaque nœud peut recevoir des adresses overlay explicites via le control plane.

Invariants :
- maximum 4 adresses par nœud ;
- IPv4 uniquement sous forme d'adresse hôte `/32` ;
- IPv6 uniquement sous forme d'adresse hôte `/128` ;
- canonicalisation avant stockage ;
- adresses non spécifiées, loopback, link-local, multicast et IPv4-mapped IPv6 refusées ;
- aucune adresse overlay ne peut être attribuée à deux nœuds différents ;
- les adresses sont persistées dans l'état du control plane ;
- un nœud authentifié peut lire ses propres adresses via `GET /v1/node/self` ;
- les peers autorisés exposent leurs adresses Mesh dans la réponse de peer discovery ;
- la mise à jour administrative passe par `POST /v1/node-mesh-addresses` et reste auditée/persistée.

Le control plane n'impose pas encore un pool IPv4/IPv6 global ni une allocation automatique. Ce choix évite d'introduire silencieusement un espace d'adresses pouvant entrer en collision avec un réseau domestique, un opérateur mobile ou un autre overlay. L'allocation automatique ne devra être activée qu'avec des pools explicitement configurés et vérifiés.


## Android — identité WireGuard et tunnel Private Mesh

L'application Android dispose désormais d'un chemin distinct pour le Private Mesh, séparé du VPN Internet défensif.

Invariants :
- la clé privée WireGuard Mesh est chiffrée au repos par une clé AES-GCM Android Keystore ;
- si la clé privée devient irrécupérable après invalidation/reset du Keystore, l'identité publique orpheline est supprimée et une nouvelle paire est générée ;
- le credential nœud reste dans son store chiffré séparé ;
- le runtime vérifie que le node ID et la clé publique retournés par `/v1/node/self` correspondent à l'identité locale ;
- les peers proviennent uniquement de `/v1/node/peers` après policy Zero Trust ;
- le chemin direct provient de `/v1/node/transport/path` ;
- les AllowedIPs Mesh sont uniquement des host routes IPv4 `/32` ou IPv6 `/128` ;
- les routes par défaut `0.0.0.0/0` et `::/0` sont interdites en mode Private Mesh ;
- aucun DNS n'est injecté par le mode Private Mesh ;
- loopback, link-local, multicast, non spécifié et IPv4-mapped IPv6 sont refusés ;
- deux peers ne peuvent pas partager le même node ID, la même clé publique ou la même route overlay dans un même plan ;
- Android ne peut pas activer simultanément le VPN Internet et le Private Mesh : un arbitre de mode les rend mutuellement exclusifs.

Le `MeshRuntimeCoordinator` est la façade destinée à l'application : identité, enrôlement, état local, découverte des peers, négociation, construction du plan direct et démarrage/arrêt du tunnel.

Le relay UDP applicatif Sentinel reste un data plane séparé. Un chemin `relay` n'est jamais injecté comme endpoint WireGuard direct. Le raccord Android au protocole relay nécessitera un client relay dédié.

## SCIM 2.0 générique — synchronisation lecture seule

Le registre Mesh expose désormais un connecteur SCIM 2.0 générique en lecture seule.

Fonctions actuellement disponibles :
- lecture de `ServiceProviderConfig` ;
- pagination explicite des `Users` ;
- pagination explicite des `Groups` ;
- filtre SCIM borné transmis explicitement ;
- normalisation minimale des identités utilisateurs ;
- normalisation des groupes et membres ;
- HTTPS obligatoire ;
- hôte SCIM allowlisté ;
- redirections interdites ;
- bearer token fourni à l'exécution par un `tokenProvider`, jamais persisté par le connecteur ;
- réponses bornées en taille ;
- taille de page et nombre de ressources bornés.

Aucune opération distante de création, modification, désactivation ou suppression n'est exposée dans cette première version. Le connecteur sert à synchroniser des observations d'identité avant d'autoriser des effets de provisioning.

`generic-scim2` passe donc au statut `foundation`, pas `validated`. Chaque fournisseur devra encore faire l'objet d'un test réel de schémas, pagination, filtres, groupes et comportement d'authentification.

## SPIFFE — identités workloads et agents IA

Le registre Mesh expose désormais une fondation SPIFFE pour mapper des identités de workloads et d'agents IA vers le moteur Zero Trust.

Contrôles implémentés :
- schéma `spiffe://` obligatoire ;
- trust domain DNS strict et en minuscules ;
- userinfo, port, query et fragment interdits ;
- chemins canoniques uniquement, sans double slash, dot-segments ou percent-encoding ambigu ;
- liste explicite de trust domains autorisés ;
- mappings de préfixes de chemin bornés ;
- résolution par mapping le plus spécifique ;
- types de sujets limités à `workload` et `agent` ;
- tags et groupes bornés ;
- trust domain inconnu = refus explicite ;
- chemin non mappé = refus explicite.

Le résultat n'alimente le modèle de sujet Zero Trust avec `deviceTrust: attested` que si l'appel fournit explicitement `svidVerified: true`. Sans cette preuve amont, le mapping retourne `SPIFFE_SVID_UNVERIFIED` et refuse l'identité.

Cette version ne vérifie pas encore les SVID X.509/JWT, la chaîne de confiance SPIRE ou la rotation des certificats. `spiffe` passe donc au statut `foundation` tandis que `spire` reste `planned`.

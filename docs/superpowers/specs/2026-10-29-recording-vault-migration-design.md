# Recording Vault V1 → V2 — conception détaillée

Autorisation : approche approuvée par l'utilisateur, implémentation autorisée après
production de ce document. Périmètre exclusif : recording-vault-r1-test et sa
qualification ; branche fix/recording-vault-v2-migration-hardening-20261028.

## Format et chemins

Sous context.noBackupFilesDir/<directory>, directory et id correspondent tous deux
à [A-Za-z0-9_-]{1,64}. Le nom logique du répertoire (pas son chemin absolu,
modifiable par Android) entre dans les AAD existantes. Les chemins sont :

- <id>.rv : ancien fichier V1, conservé octet pour octet après migration ; ou V2
  canonique produit par les save existants/nouveaux, pour compatibilité.
- <id>.v2.rv : publication V2 immuable d'une migration.
- <id>.v2.pending : candidat chiffré, avant publication (migration ou save).
- <id>.v2.required : marqueur authentifié immuable, autorité de lecture.
- <id>.v2.required.pending : préparation du marqueur, sans autorité de lecture.

Aucun clair n'est écrit sur disque. Enveloppe V2 : version 2, IV aléatoire neuf de
12 octets fourni par Cipher.init, chiffré AES-256-GCM, tag 128 bits. AAD du contenu
inchangées. Limite 16 MiB de clair, 16 MiB+29 octets de chiffré.

Le root privé doit être un vrai répertoire, sans lien symbolique ; même contrôle
sur le répertoire enfant, dont le parent canonique doit être root. Chaque chemin
existant doit être un fichier régulier NOFOLLOW_LINKS ; création CREATE_NEW et
ouverture NOFOLLOW_LINKS. Verrou interprocessus existant conservé, jamais remplacé.
La publication reste le JNI renameat2(RENAME_NOREPLACE), sans fallback écrasant.
Ces contrôles ne constituent pas une protection contre les courses d'un attaquant
capable de modifier arbitrairement les chemins avec le même UID.

## Marqueur

Enveloppe chiffrée/authentifiée dédiée, domaine AAD
sentinel.recording-vault.commit.v1 avec directory et id encodés avec longueurs,
clé AndroidKeyStore existante. Ne pas réutiliser le domaine AAD du contenu.
Clair fixe de 65 octets : mode (1=migration vers .v2.rv ; 2=V2 canonique .rv),
SHA-256 de la source V1 (mode 1 ; 32 zéros en mode 2), SHA-256 du V2 exact attendu.
Le marqueur occupe 94 octets (version/IV/tag inclus). Aucun contenu utilisateur,
numéro ou chemin absolu. Déchiffrement sans création implicite de clé.

Le marqueur indique l'exigence V2 et la destination autorisée ; il n'atteste pas
que la publication finale a déjà eu lieu. Il est écrit et forcé, publié sans
écrasement puis synchronisé dans le répertoire AVANT publication V2 finale.
Une erreur de synchronisation est une erreur de l'opération, jamais un succès.

## États et ordre exact

Toutes les actions sont sous le verrou persistant partagé.

L (legacy) : .rv V1, aucun marqueur ni final V2.
P (prepared) : candidat .v2.pending, marqueur final absent.
R (required) : marqueur final publié ; V2 exigé même si final pas encore présent.
C (committed) : marqueur + V2 final valides et synchronisés.
X (incohérent/corrompu) : refus explicite, conservation pour diagnostic.

Migration explicite migrate(id): Boolean (true si une migration/reprise de
migration est effectuée, false si V2 est déjà validé). read(V1) reste compatible
avant migration ; read après R ne retourne JAMAIS le V1, y compris sur erreur V2.

L → P : lire V1 via descripteur et tailles bornées ; authentifier entièrement par
GCM doFinal ; forcer le fichier source et synchroniser le répertoire ; chiffrer avec nouvel IV ; créer le candidat, écrire tout, force(true),
synchroniser le répertoire. Relire le candidat, authentifier V2 et comparer le
clair à la source authentifiée ; calculer les deux empreintes.

P → R : créer le marqueur temporaire, écrire tout et force(true), le relire et
l'authentifier ; renameNoReplace vers .v2.required ; synchroniser le répertoire.

R → C : revalider le candidat contre empreinte du marqueur ET GCM/AAD ;
renameNoReplace du candidat vers sa destination ; synchroniser le répertoire ;
relire et authentifier le fichier final, vérifier son empreinte ; forcer aussi
fichier final et marqueur puis synchroniser le répertoire avant de retourner.
Le V1 reste intact, y compris après C. Aucun chemin de migration ne le supprime.

save suit les mêmes étapes P/R/C, avec mode 2 et destination .rv ; save refuse
les identifiants possédant déjà une source, un final ou un marqueur. Un V2
canonique antérieur sans marqueur est authentifié, forcé et son répertoire
synchronisé AVANT enrôlement au premier read ou migrate. Une erreur d'enrôlement remonte une erreur plutôt qu'un faux succès.

## Interruptions et reprise

- Avant/pendant P : source intacte ; candidat incomplet non autoritaire. Une
  nouvelle migration authentifie d'abord V1, puis peut remplacer uniquement les
  artefacts de préparation non engagés. Pas de nettoyage global des résidus.
- Après P, avant R : source intacte ; reprise prépare un nouveau candidat/IV.
- Pendant publication du marqueur : si le marqueur final existe, sa présence
  interdit tout repli, même si l'appel précédent a échoué. Ré-authentifier et
  resynchroniser avant toute reprise ; sinon état P.
- Après R, avant publication V2 : read et migrate reprennent uniquement le
  candidat exact désigné par le marqueur, vérifient empreinte et AAD et le
  publient. Si le candidat manque/est corrompu, X ; conserver V1 pour récupération
  explicite, ne pas le convertir silencieusement en nouveau contenu autoritaire.
- Après rename V2, avant sync : erreur/résultat indéterminé ; aucune déclaration
  de durabilité. Reprise vérifie le final puis force fichier, marqueur et
  répertoire. Aucun écrasement ; les deux fichiers simultanés sont incohérents.
- Nouveau save interrompu en P sans aucun .rv, final V2 ni marqueur : read ou
  migrate authentifie le candidat complet avec les AAD exactes, le force puis
  synchronise le répertoire, publie un marqueur mode 2 et termine vers .rv.
  Aucun remplacement du candidat ; candidat incomplet/invalide conservé, erreur.
  Cette reprise n'est jamais choisie si une source .rv existe, même corrompue.
- Après C : migration répétée ne réécrit ni V2 ni marqueur. Reprise de processus
  utilise exclusivement les artefacts persistants, aucun état RAM requis.
- ENOSPC/EIO : propager l'exception, garder source et artefacts engagés. Jamais
  supprimer une copie autoritaire pour retenter. Une vérification d'espace libre
  indicative ne remplace pas le traitement des erreurs réelles d'écriture/fsync.

## Lecture, suppression et compatibilité

Présence d'un marqueur (même illisible, tronqué, lien, mauvais tag) : exigence
V2 ou erreur. Final de migration sans marqueur : erreur, pas de repli V1.
Avant engagement seulement, .rv V1 reste lisible. V2 canonique existant est
accepté après authentification et enrôlement durable. Version inconnue : rejet.
exists signale les artefacts autoritaires et les candidats à récupérer ; read est l'opération d'authentification.

Seul delete(id), explicitement demandé par l'appelant, supprime les copies de
cet identifiant. Vérifier tous les chemins avant toute suppression ; supprimer
source, candidat et finals avant marqueur ; synchroniser les entrées supprimées
avant de retirer le marqueur, puis synchroniser à nouveau. Une interruption peut
laisser un état X ; elle ne doit pas rendre un V1 encore présent lisible après R.
Les tests effacent uniquement leurs propres identifiants/répertoires aléatoires,
jamais les données applicatives, l'alias, ni le verrou global.

## Menaces et limites

V1 garantit le tag du contenu mais ne lie ni id ni directory ; la migration ne
peut pas réparer une substitution V1 antérieure. V2 lie le contexte logique,
pas l'identité physique d'une installation ni deux racines portant le même nom.
Le marqueur lie aussi les octets V2 : modifier seulement version, ciphertext,
chemin, ou restaurer un V1 alors que le marqueur subsiste est refusé. Aucune
conversion automatique de secours après engagement.

Supprimer/restaurer TOUS les marqueurs ET les finals et restaurer un ancien V1
peut contourner ce mécanisme local. Restaurer ensemble un ancien V2 et son ancien
marqueur cohérents peut aussi réussir. Il faut un état monotone externe/matériel
qualifié pour prétendre davantage ; AndroidKeyStore seul n'apporte pas cette
preuve. Conservation V1 : espace disque doublé et anciens chiffrés présents ;
purge et récupération manuelle ultérieures hors périmètre. fsync réussi repose
sur les garanties réelles du noyau et du stockage ; pas de preuve de résistance
électrique sur Galaxy avant qualification.

## Qualification

Tests hôte : vrais AES-GCM/JVM, vrai JNI rename Linux construit depuis le C,
AndroidKeyStore simulé explicitement ; aucun résultat Android déduit. Couvrir
lecture V1, migration, octets/IV, idempotence, chaque frontière de publication,
EIO/ENOSPC simulés, corruption/troncature, substitutions, downgrade, concurrence,
suppression et absence de génération de clé sur lecture. Processus enfant tué
après préparation/engagement/publication, reprise dans un autre processus.

Tests Android : même matrice fonctionnelle avec AndroidKeyStore réel ; protocole
force-stop pendant migration réelle dans processus distinct, puis reprise et
vérification. Découvrir la liste attendue par runner log=true et comparer les
identités exécutées, pas un nombre figé ; tout skip/échec/incomplétude est refusé.
Termux + Ubuntu proot : build isolé offline, sans changement des autres modules,
installation -r conservant données et signature. API36/SM-S926B à vérifier.
JDK17/AGP9.4.1 compatibilité à établir, ne pas masquer une incompatibilité.
Les alertes Dependabot restent NON VÉRIFIÉES (HTTP403).

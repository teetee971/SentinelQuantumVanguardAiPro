# Sentinel App Integrity Pro — module d'audit applicatif défensif

## Objectif
Offrir sur Android un centre d'inspection des applications **plus complet pour la sécurité** qu'une simple liste de patchs : provenance, intégrité, autorisations, exposition, risques, modifications de version, traçabilité et remédiation. Ne pas confondre audit et attaque, ni afficher « sécurisé » sans preuves.

## Contrat produit
- **Analyse locale (sans root)** d'applications que l'utilisateur peut inspecter via les API Android autorisées et des APK qu'il sélectionne dans le sélecteur de documents (SAF).
- **Inventaire et origine** : package, label, version/versionCode, installer/source quand accessible, dernière mise à jour, cible SDK, privilèges déclarés, états de permission exposés par PackageManager. Respecter la visibilité des paquets Android 11+, ne pas présumer QUERY_ALL_PACKAGES autorisé par Play Store.
- **Signature et historique** : certificat(s) publics SHA-256, capacité de comparer des APK fournis par l'utilisateur, indicateur de divergence de signer ; tenir compte de la rotation de certificat (signing lineage), pas de verdict de fraude automatique sur un hash différent. Une signature valide n'atteste pas de la sûreté de l'éditeur.
- **Surface d'attaque** : activités, services, receivers, providers exportés; permissions d'accès, filtres d'intentions, FileProvider, deep links; distinguer déclaration et vérification dynamique.
- **Données et vie privée** : configuration sauvegarde/transfert, trafic clair, permissions sensibles, trackers seulement à partir de preuves statiques avec source, version et faux positifs possibles.
- **Intégrité et réputation** : comparaison de version et certificats, empreintes de fichiers autorisés, analyse locale hors ligne ; consultations externes seulement après consentement explicite, sans envoyer l'APK/SMS/contacts par défaut.
- **Rapports** : observations avec champ `evidenceSource`, `confidence`, `scope`, `timestamp`, `status` (OK/WARN/BAD/UNKNOWN) ; expliquer la limitation de chaque verdict. Export redigé volontaire uniquement via SAF.
- **Remédiation** : ouvrir la fiche Paramètres Android, recommandations adaptées, aucune suppression ou modification silencieuse.

## Capacités réservées à un banc d'essai isolé et à une cible autorisée
- Simulation de reconditionnement APK et vérification du rejet de signature sur émulateur.
- Audit d'anti-tampering, protections anti-débogage/instrumentation, analyse de comportement des composants exportés.
- Test de résistance à des manipulations à l'exécution sur copie d'essai, avec traces, restauration et séparation stricte du téléphone personnel.
- Ne pas proposer contournement des licences, paiements, protection d'applications tierces, injection invasive, interception de communications, désactivation Play Protect ou accès root pour altérer d'autres logiciels.

## UI D.1 — nouvelle section « Intégrité des applications »
- Vue résumé : applications inspectables, alertes réelles, éléments non vérifiables, dernière inspection, frais réseau (0 par défaut).
- Fiche application : identité, signature, permissions, composants exposés, risques et preuves, historique comparaison.
- Action « Inspecter un APK » par `ACTION_OPEN_DOCUMENT` sur fichier utilisateur, lecture en streaming et limites de taille; aucune exécution du fichier inspecté.
- Action « Comparer deux versions » hors installation.
- Statut initial : `UNKNOWN` en absence de preuve, jamais `READY` par simple présence de l'écran.

## Architecture de réalisation
1. `AppIntegrityContract` types immuables + parseur local des évidences.
2. `InstalledAppInspector` encapsulant PackageManager et restrictions de visibilité ; timeout/cancellation.
3. `ApkDocumentInspector` lecture sans exécution via SAF et PackageManager.getPackageArchiveInfo (données potentiellement incomplètes), calcul SHA-256 ; ne pas importer d'APK dans le stockage partagé permanent.
4. `FindingEngine` règles versionnées, déterministes, tests unitaires incluant faux positifs, signature rotative, déclarations exportées et absence de permissions.
5. Ecran Compose D.1, navigation cohérente, permissions et états d'erreur, 48dp, TalkBack, français.
6. Tests sur émulateur et tests physiques non intrusifs, preuve par SHA/rapport avant diffusion.

## Conditions de livraison
Aucune publication tant que les autorisations et politiques Play ne sont pas revues, que la compilation et les tests n'ont pas réussi sur l'intégration complète, et que l'interface n'a pas été testée réellement. La section est **planifiée, pas implémentée** par ce document.

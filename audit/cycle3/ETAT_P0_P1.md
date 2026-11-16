# Cycle3 — 16 novembre2026 — NO-GO production

Base:064cbf498f8f29499484e8d2b9443475cf32b231.
Branche:audit/sentinel-integral-p0-20261116. Aucun push ni déploiement.
Budget:0USD; aucun service payant, clé API cloud ou GitHub Action utilisé.

## Changements

Client Kotlin SentinelAiLocalEngine:origine127.0.0.1:18080 fixée, proxy et redirects
interdits, HMAC serveur vérifié avant envoi du texte, empreinte Qwen épinglée,
MAC du corps de réponse vérifié avant interprétation, champs et recommandations
bornés. Une requête à la fois; close efface sa copie de clé, annule la connexion
active et rejette un résultat arrivé après fermeture. Ce client ne reçoit aucun
port de décision, adaptateur d'action ou permission Android. Neuf tests nouveaux
couvrent appariement, MAC, modèle, catalogue, entrées et fermeture pendant requête.

Défaut réel trouvé dans le service:uvloop fournit un socket pour stdin=PIPE;
llama.cpp rouvre /dev/stdin comme un fichier et échouait en serveur HTTP, alors
que le TestClient en mémoire réussissait. Reproduit avec sortie502 et erreur
llama.cpp failed to open file /dev/stdin. Correctif:pipe OS explicite, borné à
4096octets, fermé après lancement; texte toujours absent des arguments processus
et aucun fichier temporaire contenant la demande. Test de non-régression rouge
puis vert; essai Kotlin→HTTP→llama.cpp→Qwen effectivement relancé avec succès.
Les clés d'appariement d'essai ne sont ni versionnées ni livrées.

## Preuves d'exécution de ce cycle

- Échec initial attendu:tests Kotlin non compilables avant création du client.
- Tests de protocole Kotlin ciblés:9 réussis.
- Compilation Kotlin:BUILD SUCCESSFUL.
- Régression Android JVM complète:340 suites,1604 tests,0 échec,0 erreur,0 ignoré.
- Compilation tests instrumentés:BUILD SUCCESSFUL.
- assembleDebug:BUILD SUCCESSFUL; APK98888283octets.
- apksigner verify:exit0,signaturev2 valide,certificat Android Debug RSA2048.
- Manifeste final extrait de l'APK avec apkanalyzer:exit0. Debuggable=true,
  usesCleartextTraffic=false; rôles/services protégés conservés.
- Garde manifeste source:exit0,25 permissions déclarées.
- Service local corrigé:10 tests réussis,1 avertissement TestClient de dépréciation.
- Inférence réelle via client Kotlin compilé et serveur HTTP uvloop, x86_64:
  réponse2+2=4,actionNONE,environ2,1secondes,aucune action exécutée.
- Préservation:101 fichiers protégés comparés à l'archive de référence,0 différence;
  schéma RoomSHA256a7c5b4e1926c4441629734c0703ba6174e6bcf3796394b51302846bac0dae0c4.
- ADB:aucun appareil détecté. Aucun test instrumenté exécuté.

Les1328 testsJS et38 tests vocaux sont les résultats conservés du cycle2, pas
réexécutés ce cycle puisque leurs sources n'ont pas changé. Les journaux et
rapports rouges sont conservés; ils ne sont pas présentés comme des réussites.
Les avertissements Gradle annoncent une incompatibilité future Gradle10;
l'environnement actuel utilise Gradle9.8.0,JDK21 et toolchain17.

## Blocages résiduels

P0:le client n'est pas appelé par une Activity/composition root. Aucune chaîne
Android demande→consentement/authentification→action→journal n'est qualifiée.
Le manifeste refuse toujours HTTP:aucune exception cleartext n'a été ajoutée.
Le test hôte ne démontre ni le NetworkSecurityPolicy Android ni le cycle de vie.
Il reste à intégrer de manière contrôlée le moteur, l'écran de consentement,
l'authentification humaine et la journalisation persistante. Aucune action Phone
Core/SMS/MMS/VPN n'est exposée au modèle.

P1:aucun adaptateur fournisseur LiveKit confirmé, accès média précédemment émis
non déclarés révoqués. Le refus sécurisé demeure. Modèle/runtime ARM64,S24+,
latence,thermiques,batterie,hors ligne et interruptions restent à tester.

L'APK est un binaire debug vérifié en construction/signature, pas un APK qualifié
fonctionnellement sur appareil. Il n'embarque pas les poids du modèle et n'annonce
aucune nouvelle fonctionnalité IA disponible. Voir S24_VALIDATION.md avant toute
installation:ne jamais désinstaller une app existante pour contourner une
incompatibilité de certificat, au risque de perdre les données.

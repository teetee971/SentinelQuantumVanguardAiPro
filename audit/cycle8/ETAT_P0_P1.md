# Cycle8 — limite totale HTTP et annulation — NO-GO production

Défaut reproduit sur une vraie connexion locale:le délai de lecture35s de
HttpURLConnection s'appliquait à chaque lecture. Un serveur non authentifié
pouvait envoyer des octets espacés et empêcher indéfiniment la fin du challenge.
Le nouveau test a échoué avec TimeoutException après5s sous l'ancienne logique;
les deux autres tests passaient. Aucun résultat rouge n'est présenté comme réussi.

Le transport emploie maintenant OkHttp5.5 déjà présent dans les dépendances:
deadline totale2s pour l'échange du challenge,35s pour celui d'inférence,
connexion/écriture2s,aucune redirection HTTP/HTTPS,aucune relance automatique,
Proxy.NO_PROXY. Le budget réseau des deux échanges est donc au plus37s,hors
file de travail,calcul local,journal et cycle de vie. Le dialogue conserve ses
nonces,HMAC exacts,empreinte modèle et limite8192octets. La fermeture annule
l'appel actif et les résultats après fermeture restent refusés.

Le constructeur public de l'engine reste fixé à127.0.0.1:18080. La composition
interne peut injecter un port loopback valide pour isoler les tests avec de
vraies sockets;aucun hôte distant configurable n'est ajouté. Aucun paramètre du
modèle ou de l'interface utilisateur ne choisit ce port. La politique Android
127.0.0.1 du cycle6,les permissions,les rôles et le catalogue d'actions restent
identiques. Aucune dépendance ou version Gradle n'est ajoutée ou modifiée.

## Résultats exécutés

-Trois tests HTTP réels:challenge au compte-gouttes interrompu en environ2,005s,
 fermeture pendant lecture terminée en environ0,163s,test complet de redirection
 refusée environ0,406s. Ces durées de tests hôte ne sont pas des garanties S24+.
 Les sockets de test emploient des ports isolés et ne contactent pas de service
 extérieur;aucune fausse réponse IA n'est utilisée pour ces tests négatifs.
-Régression complète:344 suites,1633 tests JVM,0 échec,0 erreur,0 ignoré.
-JavaScript avec PostgreSQL isolé:1328 réussites,0 échec/ignoré.
-Trois tests statiques réseau et gardes manifeste/UI réussis.
-Compilation Kotlin et des instrumentés,assemblage debug et APK de tests réussis.
 Signatures vérifiées avec apksigner(exit0),empreintes dans validation.json.
-Les101 fichiers protégés sont identiques à la base du cycle7. Aucun changement
 de Phone Core,SMS/MMS,VPN,Wi-Fi,Bluetooth,Tracker,Room ou manifeste.

Le nouveau client a également communiqué avec le vrai serveur HTTP et Qwen local:
réponse2+2=4,HMAC vérifiés,actionNONE,aucune action exécutée,environ2,1s,x86_64.
Le premier lanceur JVM standalone échouait faute de classe android.util.Log de
l'AAR Android. Le jar Android mockable de Gradle a été ajouté uniquement au
classpath de test hôte,comme dans les tests JVM. La relance termine exit0 mais
émet un avertissement Log non mocké géré par OkHttp;sortie conservée dans
host-model-smoke.txt. Cela ne démontre ni Android,ni sa politique réseau réelle,
ni Keyguard. Aucune simulation de réponse n'est introduite en production.

## Limites

Aucun appareil ADB disponible:aucun test instrumenté exécuté. Les tests réseau
JVM et le vrai modèle x86_64 ne remplacent pas le parcours Android ARM64/S24+.
Vérifier sur appareil les deadlines,le refus de redirection,la mauvaise clé,
la disparition du serveur,l'annulation,les callbacks d'authentification tardifs,
la révocation entre écrans et le résultat réel du journal. Le test instrumenté
de politique réseau du cycle6 et ceux d'entrée/journal restent à exécuter.

La cancellation réseau ne prouve pas l'arrêt immédiat du calcul côté service;
sa génération peut continuer jusqu'à sa limite30s. La révocation commune du
cycle7 protège les effets autorisés du processus,pas un autre processus ni une
clé d'appariement côté service. Le journal reste limité256événements,sans
rotation destructive;la restauration conjointe journal+ancre reste indétectable.
LiveKit et les régressions physiques restent non qualifiés.

Budget0USD maintenu:aucune clé API cloud,aucun appel d'inférence payant,aucun
service payant ni GitHub Action utilisé. Décision:NO-GO production.

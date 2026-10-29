# Requalification S24+ Android 17 et preuve visuelle

L’installation de l’APK antérieur est rapportée réussie par l’utilisateur. Les essais du **nouveau candidat** ci-dessous restent **À EXÉCUTER** sur le téléphone, sans appel automatique ni numéro inconnu/surtaxé.

## Données et installation

- Ne pas désinstaller, effacer les données, restaurer une ancienne base ou utiliser `pm clear`. La PR ne change ni schéma Room, ni SQLite, ni provider SMS/MMS, ni clés de préférences.
- Noter les nombres de conversations/contacts et les préférences visibles avant mise à jour, sans exporter le contenu personnel.
- Comparer les certificats de l’installation et du candidat ; si `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, arrêter et rechercher la bonne clé, jamais contourner par une désinstallation silencieuse.

```bash
sha256sum -c SHA256SUMS.txt
adb install -r Sentinel-D1-debug-8cc1ad4e.apk
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
```

Noter versions réellement observées ; cible demandée Android17/API37. L’application conserve applicationId com.sentinel.quantum, version1.0.9/code10. Le SHA du fichier distingue le candidat de l’ancien APK.

## Parcours prioritaires

| Étape | À vérifier | Preuve attendue | Statut S24+ candidat |
|---|---|---|---|
| 1 Téléphone | Pavé, saisie/effacement/+, ligne SIM, champ et Vérifier, portrait/paysage, police100%/200% | Captures sans chevauchement, actions accessibles ; aucun appel déclenché par saisie/navigation | À EXÉCUTER |
| 2 Appels | Entrant/sortant entre deux lignes de test connues, répondre/rejeter/raccrocher, arrière-plan/notification, double appel, audio/muet/DTMF | Connexion/audio réellement observés, commandes au bon appel et fin effective ; préserver le rôle et possibilité de revenir à Samsung | À EXÉCUTER |
| 3 Contacts | Sans READ_CONTACTS puis avec autorisation, recherche, filtres/favoris, contacts multi-numéros, sélection puis appel/SMS sur action explicite | Contacts existants conservés ; source « carnet Android » claire ; ouvrir la fiche, modifier via Android, partager explicitement ; filtrer rapidement entre contacts avec/sans photo sans mélange des portraits ; aucun bouton de fausse synchro/création Sentinel enrichie | À EXÉCUTER |
| 4 Messages | État sans rôle, liste vide puis conversations existantes, ouvrir/répondre/écrire, choix SIM, pièce MMS, aperçu/quarantaine et confirmations de suppression/export | Présentation lisible, conservation des brouillons/données ; aucune suppression sans confirmation ; vrais retours opérateur pour envoi/livraison | À EXÉCUTER |
| 5 Bluetooth | Ouvrir Network Center/Bluetooth, permission refusée/accordée, scan, scroll jusqu’au bas, appairage, police200% | Barre Scanner séparée du contenu et du bouton d’appairage ; pas de résultat inventé ni d’appareil déclaré connecté sans preuve | À EXÉCUTER |
| 6 OSINT | Réseau normal : actualiser, observer nombre de sources et dates ; hors-ligne : conserver cache complet et limites ; rouvrir lien | Sources réellement récupérées et items lisibles ; 200 hôte n’est pas un PASS téléphone ; pas de « 3/3 » si récupération impossible | À EXÉCUTER |
| 7 Studio voix/PTT | Aperçu microphone local sur consentement ; auth absente/expirée ; backend503 ; session si et seulement si ready200 et deux comptes de test autorisés | Message utilisateur clair, pas de faux connecté ; microphone fermé après relâchement/erreur ; aucun jeton dans captures/logs | À EXÉCUTER |
| 8 Appel SIM + voix | Vérifier la limitation explicitement affichée | PTT refusé pendant appel mobile actif ; aucune promesse de transformation audio SIM | À EXÉCUTER |
| 9 Cycle de vie | Retour arrière, rotation, reprise, révocation des permissions/rôles, fermeture et relancement hors appel | Pas de crash ; faits runtime relus ; pas de rappel automatique ; données/préférences conservées | À EXÉCUTER |
| 10 D.1 | Comparer chaque vue du registre VISUAL_REFERENCES.md à sa capture réelle, dimensions/orientation identiques | Source maquette identifiée + SHA, capture réelle + SHA, éléments correspondants, écarts annotés, police et TalkBack testés | NON TESTÉ : captures réelles du candidat requises |

## Captures manuelles

Privilégier un profil/téléphone de test avec données fictives autorisées. Les captures peuvent afficher noms, numéros, messages et éléments d’authentification ; ne pas les publier sans vérification/masquage. Ne jamais fabriquer une image de l’application pour remplacer une capture manquante.

```bash
adb exec-out screencap -p > 01-telephone.png
# Après navigation manuelle : 02-contacts.png, 03-messages-liste.png,
# 04-messages-conversation.png, 05-messages-composition.png,
# 06-bluetooth.png, 07-osint.png, 08-studio-503.png, etc.
```

Examiner chaque fichier avant de l’accepter : bon écran, contenu stable, pas d’écran noir/de chargement/dialogue hors sujet, PNG complet. Associer UTC, SHA APK, modèle/API, résolution/densité, échelle de police et état des permissions. La mise en rapport côte à côte doit utiliser les maquettes validées originales, pas des approximations.

## Tests instrumentés et captures optionnelles

Uniquement sur un runtime de test sans données personnelles, installer l’APK instrumenté puis :

```bash
adb install -r Sentinel-D1-androidTest-8cc1ad4e.apk
adb shell am instrument -w -e class com.sentinel.quantum.ui.design.D1CommunicationLayoutInstrumentationTest com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner
# Opt-in explicite car les PNG peuvent contenir des données privées :
adb shell am instrument -w -e captureD1Screenshots true -e class com.sentinel.quantum.ui.D1CommunicationCaptureInstrumentationTest,com.sentinel.quantum.ui.AllStaticNavigationSurfacesInstrumentationTest com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/com.sentinel.quantum/files/d1-captures ./captures-D1
```

Le premier test vérifie le titre long à largeur280dp/police200% ; ce n’est pas une comparaison pixel aux maquettes. Le collecteur Activities capture Téléphone, Contacts et Messages sans injecter de faux SMS/contacts. Les tests NavGraph capturent leurs routes lorsqu’on active l’argument ; ils couvrent des états initiaux, pas chaque sous-parcours. Les captures doivent être inspectées avant d’être reconnues comme preuves.

Le contrôle de sortie ADB seul ne suffit pas : lire `INSTRUMENTATION_CODE`, nombres de tests et erreurs/assertions dans le journal. Un timeout ou un émulateur seulement « online » n’est pas un PASS.

## Conservation des preuves et retour Samsung

Collecteur expurgé déjà présent : `scripts/capture-phone-core-s24.py` ; aucune archive de données SQLite/SMS privée n’est nécessaire à cette campagne visuelle. Préférer les logs de rôles/erreurs sans numéros/messages ; relire avant partage.

Hors appel, Paramètres → Applications → Applications par défaut → Téléphone → Téléphone Samsung, puis vérifier `adb shell cmd role get-role-holders android.app.role.DIALER`. Ne pas attribuer un package Samsung supposé sans l’observer. Vérifier ensuite les appels entre lignes de test.

## Photos : régression URI inchangée et lecture tardive

Avec un contact de test autorisé ayant une photo : ouvrir sa fiche, utiliser Modifier dans Contacts Android, remplacer la photo et revenir sans redémarrer Sentinel. Vérifier l’image mise à jour dans la liste et la fiche. Répéter si le fournisseur conserve la même URI ou ne fournit pas de révision. Alterner rapidement deux contacts et les filtres, puis mettre l’application en arrière-plan et la reprendre : aucun portrait d’un autre contact ne doit apparaître. Tester READ_CONTACTS révoquée/réaccordée et rotation pendant un chargement. Conserver uniquement des captures autorisées, sans journal de numéros personnels. **NON TESTÉ ici.**

Trois scénarios instrumentés supplémentaires, compilés mais NON EXÉCUTÉS dans cette session :

```bash
adb shell am instrument -w -e class com.sentinel.quantum.ui.design.ContactPhotoStateInstrumentationTest com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner
```

Ils exercent le vrai producteur d’état utilisé par D1ContactAvatar avec une lecture différée contrôlée ; ils ne valident ni le fournisseur Samsung ni le rendu complet de la fiche.

## Messages : retour, brouillon et contexte

Statut : NON TESTÉ sur S24+. Ouvrir une conversation existante autorisée puis utiliser Retour de l’en-tête : la liste doit réapparaître sans quitter Messages. Répéter avec le geste/touche système ; si le clavier est ouvert, vérifier d’abord sa fermeture par Android. Depuis la liste, choisir Écrire, saisir un brouillon texte de test, revenir, puis rouvrir Écrire : le texte doit être conservé dans cette Activity. Recréer/faire pivoter et répéter. Choisir SMS puis MMS : le titre doit correspondre au mode et aucun ancien expéditeur ne doit servir d’en-tête d’un nouveau message.

Ouvrir séparément un compositeur externe sans destinataire ni envoi automatique :

```bash
adb shell am start -n com.sentinel.quantum/.SmsComposeActivity -a android.intent.action.SENDTO -d 'smsto:' --es sms_body 'Brouillon de qualification'
```

Retour doit quitter ce compositeur vers son appelant, pas imposer une liste intermédiaire. Vérifier également révocation de rôle/permissions et absence d’envoi involontaire. La navigation ne crée pas de stockage durable de brouillons ; les pièces jointes MMS restent non restaurées après recréation dans l’implémentation actuelle.

Quatre tests Activity/Compose ajoutés, compilés mais NON EXÉCUTÉS :

```bash
adb shell am instrument -w -e class com.sentinel.quantum.ui.SmsNavigationInstrumentationTest com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner
```

Le scénario dit « dispatcher » appelle le dispatcher Android dans l’Activity ; il ne remplace pas un essai réel du geste système avec IME ouvert. Aucun SMS ni contact de démonstration n’est injecté dans un provider par ces tests.

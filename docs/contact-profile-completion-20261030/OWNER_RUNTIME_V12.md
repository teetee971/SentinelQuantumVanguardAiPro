# Fiche Contacts — blocage en fin de défilement signalé

## Preuves et statut

Après autorisation explicite de mise à jour12, le propriétaire a fourni des captures du nouvel écran et signalé : « Quand je vais en bas de page… la page freeze ». Les captures montrent la composition de la version12 et plusieurs positions de défilement, jusqu’au pied de page, puis la liste Contacts. Aucun nom, numéro, adresse, anniversaire, portrait ou capture brute n’est publié ici. Les originaux restent dans la conversation. **Aucun relevé ADB du versionCode installé n’a encore été fourni après cette mise à jour.**

Observations limitées : photo locale affichée dans la fiche et la liste, un numéro visible dans la fiche au lieu du doublon antérieur, rubriques nom/adresses/date affichées, autres rubriques explicitement non renseignées. Cela ne garantit pas l’exhaustivité ni la fidélité à chaque donnée du fournisseur. Aucun test de transport ou de rôle supplémentaire démontré.

**Anomalie ouverte : blocage signalé au bas de la fiche. Qualification navigation : FAIL rapporté par le propriétaire, non reproduit dans le cloud.** Aucun ANR, crash ou diagnostic de cause établi. La dernière capture montre la liste ; elle ne prouve ni le fonctionnement du bouton Fermer pendant le blocage, ni une sortie par Retour, ni un redémarrage.

## Investigation

- La fiche utilise ModalBottomSheet entièrement développée + Column.verticalScroll + champs SelectionContainer. Le code n’effectue pas de lecture Contacts synchrone sur le thread UI pendant le défilement ; la lecture est dans Dispatchers.IO. Pas de boucle de recalcul de photo démontrée à la lecture statique.
- Les interactions feuille/contenu/sélection sont des hypothèses. Aucune correction de gestes, remplacement de conteneur ou suppression de fonctionnalité sans reproduction.
- Question demandée au propriétaire : peut-il remonter et utiliser Fermer au moment du blocage ; le problème disparaît-il après quelques secondes ? Ces réponses distinguent fin naturelle de défilement, capture de geste et gel de l’application.
- Nouveau test diagnostic réel `repeatedBottomGesturesStillAllowScrollingBackAndClosing` sur D1ContactProfileFrame : atteindre le pied, trois gestes vers la fin, vérifier la borne, geste inverse et fermeture. Données entièrement synthétiques.
- `:app:assembleDebugAndroidTest` : **exit0, compilation réussie**. Test ajouté **NON EXÉCUTÉ**, aucun appareil dans adb devices. Aucun RED/GREEN runtime revendiqué, aucun fix revendiqué.

**Verdict : NOT READY pour poursuivre la qualification de navigation sans clarification/reproduction du blocage.** Les précédents résultats JVM de l’application et la signature12 restent des preuves distinctes ; ils ne résolvent pas ce défaut physique. Aucun nouvel APK applicatif, aucune nouvelle signature, aucun changement de version ou de données. L’APK d’instrumentation diagnostique n’est pas une nouvelle livraison d’application et n’a pas été installé.

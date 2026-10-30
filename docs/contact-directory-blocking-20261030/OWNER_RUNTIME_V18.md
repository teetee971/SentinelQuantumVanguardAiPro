# Retour propriétaire — signature18 et parcours Téléphone

Le propriétaire a confirmé par sortie sha256sum le fichier unsigned18 attendu, puis transmis la fin du bloc de signature locale : marqueur SIGNATURE_ET_INTEGRITE_VERIFIEES — NON INSTALLE, certificat historique68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2, package com.sentinel.quantum/code18/name1.0.9. Contrôles effectués sur le téléphone selon cette sortie, pas réexécutés par le cloud sur le binaire signé. Clé privée et APK signé non reçus. Autorisation explicite de mise à jour manuelle18 ensuite donnée ; aucun install automatique.

Les nouvelles captures montrent : résultat d'identification locale dans le composeur, état Composition, état En communication avec durée affichée, écran Appel terminé et entrée sortante dans les récents Android. Nom de contact cohérent entre ces images. Aucun nom, numéro, portrait ou détail privé de journal reproduit dans Git ; captures non publiées.

**P05/P06 PARTIELS : états UI d'appel sortant observés.** Les captures seules ne prouvent pas qualité audio dans les deux sens, choix SIM, action de raccrochage locale/distante, disparition des notifications, absence d'appel fantôme ou appels simultanés. Une question sur audio/fin normale a été posée, réponse en attente au moment de ce rapport. Pas de relevé indépendant de version installée ni preuve de provenance binaire de l'exécution18 au-delà du parcours propriétaire rapporté.

Les chiffres du résumé des récents proviennent du journal Android borné ; ils ne prouvent ni que Sentinel a lui-même bloqué ces appels ni que la protection de blocage fonctionne effectivement. Aucun incident d'appel traversant antérieur résolu par ces captures.

**Écart photo identifié :** l'écran d'appel affiche une initiale pour un contact dont une photo était visible dans la fiche antérieure. Le code CallerHero de SentinelInCallActivity ne charge aucune photo, il affiche callerInitial ou Person. Capacité non implémentée sur cette surface, pas une panne démontrée du chargement de photo du répertoire18. Aucun changement de ce composant livré dans18 ; ne pas qualifier la demande de photos répertoire sur la seule capture InCall.

Photos/clique nom en liste, confirmation/annulation dans fiche et conversation, absence de gel18 : NON VALIDÉS par ce nouveau lot. Les contrôles statiques et l'essai favorable de défilement17 restent distincts. Aucun besoin d'un nouvel appel au seul motif de documenter ces captures.

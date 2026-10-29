# Livraison courante Sentinel D.1

Sources APK **d5c31b49e84901bb62f1be26072c73d36c2f5bb0**. Rapport [CALL_HISTORY_ACCESS_FOLLOWUP.md](CALL_HISTORY_ACCESS_FOLLOWUP.md). [Artefacts, tailles et SHA-256](ARTIFACTS.json).

1 397 tests unitaires Android réussis (302 XML), 0 échec/erreur/ignoré. Kotlin et deux APK réussis, signature/alignement vérifiés. Node : 19 tests du candidat précédent conservés comme historiques, non réexécutés ici. Instrumentation et S24+ NON EXÉCUTÉS/NON TESTÉS. Fidélité graphique D.1 complète NON VALIDÉE.

Le cache des récents est désormais lié au rôle Téléphone, à READ_CALL_LOG et au premier plan. Pause/révocation observée invalident les anciennes lectures. Une transition entièrement entre deux échantillons en restant RESUMED peut échapper à l'observation ; voir le rapport. Les correctifs Contacts et Messages précédents sont inclus.

# Filtrage d’appels — audit et correction ciblée16

Le propriétaire indique que quelques appels passent et ne se rappelle plus leur numéro/format ni l’état de l’application. Aucune règle défaillante réelle, journal ou capture de filtrage n’est disponible : ne pas attribuer ces appels à une cause supposée. Aucun appel réel de test ni recherche de numéro privé.

Constats : moteur exact/préfixes local, réputation signée au plus silencieuse, numéros FR national/+33/0033 équivalents par matchingRepresentations. Pas de délégation à BlockedNumberProvider Android ni filtrage SMS par cette liste. Le premier appel d’un processus froid peut passer avant préparation du snapshot/HMAC en mémoire ; politique volontaire pour préserver réponse Telecom. Application.onCreate/onScreenCall restent sans préparation disque/Keystore. Numéros masqués/indisponibles, règle expirée, nouveau numéro hors règle, rôle retiré et classification urgence inconnue sont des limites/conditions distinctes, non des causes établies ici.

Défaut source vérifiable : MainActivity.onPostResume prépare la maintenance, mais SentinelDialerActivity (entrée directe Téléphone/Contacts) ne la demandait pas.16 ajoute ce même appel asynchrone et idempotent après reprise de l’UI. Cela réduit la fenêtre après entrée directe ; ne corrige pas le premier appel d’un cold start sans interface.

Deuxième défaut UI : CallBlockingScreen ne relisait rôle/permission que lors de ses propres launchers. Une modification via Paramètres pouvait laisser un état affiché périmé.16 relit ces faits à chaque RESUMED, charge le snapshot sur IO et annule cette lecture à la pause. Le badge indique la sélection de Sentinel, pas une preuve de blocage réussi. Portée des règles exactes et prudence sur préfixes affichées.

Aucune modification des règles existantes, format des empreintes, clés HMAC, normalisation mondiale, cache critique, service de screening, protection des urgences, données SMS, Room ou main. Pas de nettoyage ou désinstallation. Pas de blocage global des inconnus sans demande.

Validation : tests existants moteur/bornes/expiration ciblés, suite app complète et gardes de démarrage/réponse, compilation Kotlin/APK, Node. Pas de nouveau test miroir de source pour une simple délégation de lifecycle ; trajet réel onPostResume/Paramètres et efficacité opérateur NON TESTÉS sans Android. Aucun correctif des appels signalés ni blocage100% prétendu. Relever au prochain incident l’heure, numéro exact/préfixe/masqué et état du rôle, avec preuves expurgées ; ne pas demander de rappeler le correspondant suspect.

## Revue et protection des éditions

La revue indépendante a trouvé une course introduite par le rafraîchissement de reprise : une ancienne lecture pouvait remplacer l’état UI après ajout/effacement. CallBlockingUiSnapshot suit une révision incrémentée par chaque publication de mutation (numéros, préfixes, ARCEP et synchronisation). La lecture ne publie que si sa révision est toujours courante. Trois tests de comportement couvrent nouvelle règle, règles retirées et lecture autorisée sans mutation ; RED API absente observé avant helper. Aucun test miroir de source ajouté.

Les champs saisis sont conservés si ajout numéro/préfixe échoue et le message français du numéro évoque aussi le stockage. Le store existant et ses écritures restent inchangés ; ce n’est pas une réparation des limites de persistance ni une preuve d’ajout/blocage physique.

## Résultats

Commande ciblée moteur/expiration/bornes exit0 avant correction ; noyau inchangé. Build final Kotlin/APK debug/test/unsigned exit0,1m38. Suite app1440réussis/0échec/0erreur/0ignoré,308XML, dont3nouveaux tests de fraîcheur UI. Wearable26/26 inchangé/caché. Node69+15réussis. Runtime instrumenté et appels physiques NON EXÉCUTÉS. Le défaut d’appels traversants signalé ne peut pas être considéré reproduit/résolu sans exemple ;16 est un correctif de préparation et d’état UI, pas une garantie de rejet.

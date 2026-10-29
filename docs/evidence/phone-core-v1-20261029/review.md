# Revue indépendante

Revue du diff contre da157f4 par l’agent `phone_core_review`, sans modification des fichiers ni exécution Gradle.

Deux observations importantes initiales :
1. Le drapeau OnlyAlertOnce commun à la notification entrante/en cours supprimait l’alerte d’appel en attente.
2. La suppression du lancement depuis InCallService ne couvrait plus les appels sortants initiés hors du composeur Sentinel.

Corrections examinées : IncomingCallAlertPolicy identifie la première publication entrante par ID et phase ; lancement protégé pour CALL_ADDED sortant dans InCallService. Tests JVM correspondants ajoutés.

Conclusion du relecteur : les deux observations sont traitées ; aucun autre défaut critique ou important identifié dans les changements revus. L’exception Android applicable au service InCallService lié par le système autorise cette tentative de lancement ; elle ne prouve pas un affichage effectif par Samsung. Validation S24+ toujours requise.

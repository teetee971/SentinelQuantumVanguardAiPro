# Raccordement local Android — périmètre du cycle3

Choix d'implémentation:client Android de propositions vers le service llama.cpp
sur127.0.0.1:18080 exclusivement. Aucun endpoint distant, aucune clé cloud,
aucune capacité d'approbation ou d'exécution transmise au moteur.
Le service séparé Termux/proot est une dépendance explicite, pas un moteur déjà
embarqué dans l'APK. Le JNI/NDK direct reste une évolution possible; il nécessite
son propre packaging ARM64 et ses validations. Le cloud est exclu par budget0.

Contrat:appariement256bits éphémère, challenge aléatoire indépendant par requête,
preuve serveur vérifiée avant envoi du texte, empreinte Qwen épinglée, MAC sur
corps exacts, réponse bornée, catalogue de recommandations fermé. Fermeture
annule la connexion active et interdit les nouvelles requêtes; le résultat est
rejeté si fermeture intervenue pendant son traitement. Les requêtes simultanées
sur une instance sont refusées. Aucun retry automatique d'inférence.

Le client est préparatoire:il n'ajoute aucune permission, exception cleartext,
Activity ou intégration Phone Core. L'activation Android réelle requiert encore
un point de composition de confiance, consentement explicite, authentification
humaine et écran d'approbation, adapters d'actions autorisées, journal persistant
et tests sur appareil. Le manifeste actuel peut refuser HTTP local:aucune
qualification Android n'est déduite des tests JVM de protocole.

Validation:tests de contrat négatifs et révocation locale, connexion HTTP réelle
loopback sur hôte si disponible, compilation Kotlin, régression JVM complète,
compilation instrumentés et assemblage APK après toute modification Android.
Le manifeste et la signature APK seront contrôlés sans publication.

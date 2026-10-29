# Limites et anomalies restantes

## Bloque une qualification physique ou une soumission

- **NON TESTÉ sur Galaxy S24+ Android 17** : installation/lancement, rôle réellement accordé, appels entrants/sortants/opérateur, audio/DTMF/double appel, appel manqué, notifications One UI, révocations, redémarrage et retour Samsung. Aucun appareil connecté. Aucun crash de lancement ne peut être exclu par la seule compilation.
- AAB volontairement non signé ; aucune clé d’upload de propriétaire fournie ou recherchée. Disponibilité du versionCode 10 et configuration Play App Signing non consultables ici.
- Permissions SMS/Call Log toujours présentes pour préserver les modules existants. Déclarations/éligibilité Play à confirmer pour l’application complète. Retirer les seules permissions SMS sans désactiver ses composants et son parcours casserait le module : aucune suppression aveugle.
- Politique publique, responsable de traitement, conservation des services distants, sous-traitants, suppression de compte et formulaire Data Safety nécessitent les faits du propriétaire. Le dossier fournit les éléments vérifiables et les champs restant à compléter ; il n’invente pas de conformité.

## Comportements bornés connus

- Sans POST_NOTIFICATIONS/canal/plein écran autorisés, la surface entrante peut être limitée par Android. Le centre d’activation indique la configuration ; tester explicitement le scénario avant de confier le téléphone à un utilisateur.
- La procédure complète de configuration/diagnostic existante couvre aussi SMS/MMS et filtrage. Son état global n’est pas une obligation d’activer tous ces modules pour les essais du composeur.
- Une demande d’appel en mémoire est volontairement abandonnée après mort du processus pendant un dialogue de permission : retoucher Appeler. Aucune restauration ne relance un appel automatiquement.
- Forcer l’arrêt peut suspendre les composants jusqu’à une réouverture, conformément à Android ; pas de promesse de contournement.
- Conférence/fusion/permutation masquées en V1 faute de qualification opérateur ; attente/reprise ciblées et double appel restent à tester. Les implémentations de service sont conservées.
- SMS/MMS, VPN, BLE, Voice Studio audio et Security Lab : sources et intégrations préservées, **hors certification physique de cette livraison** ; aucune affirmation commerciale d’opérationnalité. Aucun nouveau module expérimental ajouté.
- Les permissions caméra/projection d’écran et le service LiveKit de capture d’écran inutilisé sont retirés ; aucun parcours caméra/partage d’écran n’existe dans l’UI actuelle. Le transport audio LiveKit et RECORD_AUDIO sont conservés.
- Le build conserve des avertissements existants de dépréciation Kotlin/Compose/Gradle et de nullabilité VPN ; ce rapport ne transforme pas ces avertissements en preuve d’erreur observée ni en garantie d’absence de défaut.

Aucune anomalie bloquante reproductible restante n’est identifiée dans les tests hôte exécutés. Cette phrase ne vaut pas validation des scénarios non exécutés.

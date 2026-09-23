# Migration vers le rôle SMS Android — frontière de sécurité

Statut : client SMS logiciel activable pour validation sur appareil. Le rôle `ROLE_SMS` ne peut être demandé que lorsque toutes les capacités logicielles requises sont présentes ; les permissions SMS restent utilisables uniquement tant qu’Android confirme réellement Sentinel comme gestionnaire SMS par défaut.

## Verdict

Sentinel dispose désormais des capacités logicielles nécessaires pour demander `ROLE_SMS` et entrer dans la phase de validation physique : envoi via `SmsManager`, réception `SMS_DELIVER`, conversations provider Android, suivi envoyé/livré, téléchargement et aperçu MMS entrant bornés, `ACTION_SENDTO`, `RESPOND_VIA_MESSAGE`, multi-SIM et analyse locale. Les permissions `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, `RECEIVE_MMS` et `RECEIVE_WAP_PUSH` restent inutilisables tant qu’Android ne confirme pas réellement `ROLE_SMS`. Le statut « 100 % fonctionnel » reste interdit avant les essais physiques.

Android et Google Play exigent que l’application soit réellement le gestionnaire SMS par défaut avant de demander les permissions SMS. La perte du rôle doit immédiatement arrêter tout accès. Références officielles :

- https://developer.android.com/privacy-and-security/permissions-used-only-in-default-handlers
- https://support.google.com/googleplay/android-developer/answer/10208820

## Menaces prioritaires

| Menace | Conséquence | Contrôle obligatoire |
|---|---|---|
| Demande prématurée du rôle | messagerie inutilisable ou trompeuse | garde fail-closed avant tout appel à `RoleManager` |
| Perte de SMS/MMS | atteinte aux communications de l’utilisateur | tests de migration, restauration et non-perte sur appareils physiques |
| Mauvaise SIM d’envoi | coût ou fuite vers un mauvais abonnement | choix explicite, double SIM testée, aucun envoi silencieux |
| Lien malveillant | phishing ou exécution externe | analyse locale et aucun lien ouvert automatiquement |
| Pièce jointe hostile | exposition du parseur et du stockage | limites de taille/type, stockage privé, prévisualisation isolée |
| Notification indiscrète | contenu sensible visible sur écran verrouillé | aperçu configurable et masqué par défaut |
| Conservation excessive | risque vie privée et forensic | politique locale, export et suppression vérifiables |
| Perte du rôle | accès indu aux SMS | contrôle du rôle avant chaque opération sensible |
| Synchronisation implicite | exfiltration de conversations | aucune synchronisation cloud par défaut |

## Machine d’état obligatoire

1. **MANUAL_SCANNER_ONLY** — mode utilisateur sûr tant que Sentinel n’est pas gestionnaire SMS par défaut.
2. **CLIENT_INCOMPLETE** — composants de messagerie encore incomplets.
3. **ELIGIBLE_FOR_ROLE_REQUEST** — toutes les capacités logicielles requises sont présentes ; la boîte de dialogue système peut être ouverte. Les permissions SMS ne sont pas encore utilisables.
4. **DEVICE_VALIDATION_REQUIRED** — Android confirme Sentinel comme gestionnaire SMS par défaut ; les permissions peuvent être utilisées pour les essais physiques SMS/MMS/multi-SIM.
5. **DISTRIBUTION_REVIEW_REQUIRED** — les essais physiques sont validés mais la revue de distribution/Google Play n’est pas encore finalisée.
6. **ACTIVE_DEFAULT_HANDLER** — rôle Android confirmé, validation physique terminée et revue de distribution prête.

La classe `SmsRoleMigrationPolicy` applique cette frontière indépendamment de l’interface utilisateur. Elle interdit la demande de rôle tant que le logiciel est incomplet, mais elle n’exige jamais une preuve appareil avant `ROLE_SMS` : ce serait circulaire, car les tests réels exigent précisément que Sentinel détienne ce rôle.

## Capacités bloquantes

Réception SMS, lecture des conversations, envoi, notifications, MMS/pièces jointes, messages d’urgence, double SIM, conservation locale, export/suppression et analyse hors ligne doivent toutes être présentes avant la demande du rôle.

## Critères de validation finale

- tests unitaires de la garde ;
- activation réelle du rôle et test de sa révocation ;
- essais physiques sur au moins deux versions Android et un appareil double SIM ;
- preuve séparée du callback SMS SENT puis du chemin de statut DELIVERED (succès ou erreur opérateur observée, sans confondre callback et livraison réussie) ;
- corpus SMS longs, concaténés, Unicode, MMS, pièces jointes et messages d’urgence ;
- preuve de non-perte lors de la migration et du retour à l’ancienne application ;
- politique de confidentialité, fiche Play Store et déclaration de permissions alignées ;
- examen manuel du flux de consentement.

Le compteur local de l’écran Phone Core ne valide qu’une installation donnée. Il ne doit jamais être présenté comme « Phone Core 100 % fonctionnel ». Ce statut global reste interdit jusqu’à réussite de toute la matrice ci-dessus.

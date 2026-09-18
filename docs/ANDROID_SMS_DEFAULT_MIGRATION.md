# Migration vers le rôle SMS Android — frontière de sécurité

Statut : fondation active du client SMS par défaut. Les permissions SMS sont déclarées dans le manifeste, mais le rôle `ROLE_SMS` et tout usage effectif restent verrouillés par la politique fail-closed.

## Verdict

Sentinel n’est pas encore un client SMS complet. Le dépôt contient désormais les primitives de base : envoi via `SmsManager`, réception `SMS_DELIVER`, stockage dans le provider Android, suivi envoyé/livré, `ACTION_SENDTO`, `RESPOND_VIA_MESSAGE` et analyse locale. Les permissions `READ_SMS`, `RECEIVE_SMS` et `SEND_SMS` sont déclarées, mais leur usage reste interdit tant que Sentinel ne détient pas réellement `ROLE_SMS`. Le scanner coller/partager reste le mode utilisateur sûr tant que le rôle n’est pas activé.

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
3. **DEVICE_VALIDATION_REQUIRED** — client complet en code, mais essais SMS/MMS/double SIM/urgence manquants ou dossier Play non prêt.
4. **ELIGIBLE_FOR_ROLE_REQUEST** — le bouton peut ouvrir la boîte de dialogue système ; aucune permission SMS n’est encore utilisable.
5. **ACTIVE_DEFAULT_HANDLER** — accès autorisé uniquement tant qu’Android confirme le rôle.

La classe `SmsRoleMigrationPolicy` applique cette frontière indépendamment de l’interface utilisateur. Le client SMS partiel existe désormais, mais la politique doit continuer à refuser la demande de rôle tant que toutes les capacités obligatoires et les preuves appareil ne sont pas réunies.

## Capacités bloquantes

Réception SMS, lecture des conversations, envoi, notifications, MMS/pièces jointes, messages d’urgence, double SIM, conservation locale, export/suppression et analyse hors ligne doivent toutes être présentes avant la demande du rôle.

## Critères avant activation

- tests unitaires de la garde ;
- tests instrumentés du rôle et de sa révocation ;
- essais physiques sur au moins deux versions Android et un appareil double SIM ;
- corpus SMS longs, concaténés, Unicode, MMS, pièces jointes et messages d’urgence ;
- preuve de non-perte lors de la migration et du retour à l’ancienne application ;
- politique de confidentialité, fiche Play Store et déclaration de permissions alignées ;
- examen manuel du flux de consentement.

Aucun pourcentage supérieur ne doit être publié avant ces preuves.

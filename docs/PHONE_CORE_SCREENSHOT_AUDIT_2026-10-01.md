# Phone Core — audit des captures fournies le 1 octobre 2026

Périmètre : cinq captures Android fournies par l’utilisateur, inspectées directement. Version APK et modèle Android non établis par ces images. Source examinée : main ebd6e4b3b51dabdc83d225a35563d765656ecf69.

1. **Rédaction avec message — cohérent.** Bouton Envoyer activé, ligne opérateur visible. Le compteur suit la saisie. Aucune preuve de retour SENT/DELIVERED sur cette capture.
2. **Rédaction vide — cohérent.** Bouton désactivé et compteur nul. Cela ne prouve pas à lui seul la réussite de l’envoi précédent.
3. **Notification reçue — observation positive.** Notification Sentinel avec aperçu visible ; le réglage d’aperçu est explicitement activé dans le formulaire. À vérifier séparément : appareil verrouillé, option désactivée, confidentialité système.
4. **Conversations — correction nécessaire.** Une date de 2030 précède les messages actuels. Le fournisseur Android conserve la date originale ; notre tri ramenait cette date à maintenant. Correction : classer les dates anormales après les dates plausibles et les signaler dans la liste et le fil, sans modifier le fournisseur. Le compteur est renommé « messages chargés » : la lecture est limitée à 200 SMS et ne mesure pas toute la conversation.
5. **Appel sans session — correction nécessaire.** « Aucun appel actif » cohabite avec une grande fiche de correspondant inconnu. Correction : ne présenter la fiche que si une session existe ; le bandeau décrit explicitement l’absence d’appel. Cette capture ne prouve pas un défaut de réception des appels.

## Risques UX/accessibilité visibles

La liste de conversations consacre beaucoup d’espace à deux boutons et à de longs aperçus ; une liste compacte avec ouverture principale du fil améliorerait le balayage. Le formulaire utilise une zone de message volumineuse et des textes secondaires peu contrastés. La teinte jaune du champ destinataire ne permet pas d’identifier sa cause sur image (thème, surlignage système ou autofill). Les contrastes numériques, TalkBack, cibles tactiles et grandes polices restent à tester sur appareil ; aucune conformité globale n’est déclarée.

## Limites et tests restants

Ces captures documentent les écrans, pas une session interactive observée. Elles ne certifient ni livraison de bout en bout, ni appels entrants/sortants, ni MMS. Tester appel entrant, décroché, maintien, raccroché, disparition de la fiche et réouverture sans appel. Tester SMS avec option d’aperçu désactivée et écran verrouillé.

La fenêtre récente reste limitée à 200 SMS : une première requête sélectionne les dates plausibles, puis les dates anormales remplissent les places restantes. Ainsi une accumulation de dates futures ne masque plus les SMS plausibles avant le tri. Si la fenêtre plausible est pleine, les anomalies ne figurent pas dans cette fenêtre ; les dates originales et les messages ne sont pas effacés. Le fil individuel conserve sa lecture historique bornée et son ordre d’origine.

Les captures contiennent des données personnelles et ne sont pas ajoutées au dépôt public. Les images locales accompagnent ce rapport uniquement.

# Audit de préparation production — 9 septembre 2026

## Verdict

| Surface | Verdict | Preuve / blocage |
|---|---|---|
| Site public et annuaire ARCEP | Candidat production | Build, liens, accessibilité statique, sécurité client, isolation, tests et audit npm passent localement. Le déploiement final doit encore être vérifié après fusion. |
| Application Android installable | NO-GO distribution | La CI Android du SHA `3cb7ef67acc05be5dcbf3f57df1214165f8f4933` est réussie, mais aucun APK public signé, checksum et test sur appareil réel ne sont publiés. |
| Paiement et activation organisations | NO-GO commercial | Aucun prestataire de paiement, backend de licence, code d’organisation réel, CGV définitives ou procédure de remboursement n’est connecté. |
| Base communautaire de signalements | NO-GO collecte | Les garde-fous de modération sont testés dans le dépôt, mais aucun stockage durable, consentement, recours et publication signée ne sont déployés. |
| Plateforme opérationnelle complète | NO-GO | La confiance runtime, la garde des clés, les producteurs de preuves et les scénarios E2E de production restent à démontrer dans l’issue #215. |

## Contrôles exécutés localement

- `npm ci --ignore-scripts` ;
- syntaxe JavaScript ;
- liens et ressources statiques ;
- accessibilité statique ciblée ;
- contrat public (annuaire gratuit, aucun paquet Android public, activation organisation désactivée, identité visuelle) ;
- rapport de taille frontend ;
- gouvernance sécurité et IA ;
- 500 cas de fuzzing sans violation ;
- isolation du projet ;
- épinglage des GitHub Actions ;
- hygiène des credentials sur l’arbre courant ;
- build Cloudflare Pages ;
- `npm audit --audit-level=low` : 0 vulnérabilité.

Le build final compte 56 fichiers, 1 919 967 octets bruts et 432 242 octets gzip. Avant l’intégration visuelle, il comptait 55 fichiers, 1 828 160 octets bruts et 344 438 octets gzip. L’image Sentinel fournie a été convertie en WebP 1279 × 720 d’environ 84 Ko et est chargée en différé.

## CI et configuration GitHub observées

Sur le SHA `3cb7ef67acc05be5dcbf3f57df1214165f8f4933`, les workflows de build Android APK/AAB, sécurité, gouvernance IA, isolation, intégrité, CodeQL, frontend et Lighthouse pré-production ont réussi.

Le contrôle Lighthouse du déploiement réel a échoué uniquement sur son seuil absolu de performance mobile : échantillons 97/98/98, médiane 98, avec accessibilité, bonnes pratiques et SEO à 100 sur les trois échantillons ; desktop à 100 partout. Le contrôle n’est pas abaissé artificiellement.

Le ruleset `main` est actif : pull request obligatoire, branche à jour, suppression et force-push interdits. Il n’exige toutefois que les trois analyses CodeQL ; les autres gates critiques ne sont pas encore des checks obligatoires. L’issue #225 doit donc rester ouverte.

## Correctifs de cette passe

- engagement gratuit explicite pour la recherche téléphonique Web et le socle Android ;
- annuaire gratuit ajouté à la navigation principale ;
- identité Sentinel intégrée avec l’image du soldat et du bouclier ;
- prix incohérents 19/190/390 remplacés par 29/290/790 sur l’espace client ;
- ancien lien de téléchargement externe AAB supprimé ;
- APK présenté avec bouton réellement désactivé ;
- activation manuelle par code documentée sans fausse validation locale ;
- architecture multi-listes et priorité des listes personnelles explicitées ;
- add-ons professionnels proposés séparément du gratuit ;
- EUvsDisinfo, ISD et DISARM-FR documentés comme références à qualifier ;
- roadmap, FAQ, confidentialité, conditions et documentation Android alignées.

## Conditions de GO restantes

1. Fusionner uniquement après réussite de tous les workflows de la pull request.
2. Vérifier le déploiement Cloudflare sur le commit fusionné et rejouer les parcours clés.
3. Conserver le paiement désactivé jusqu’au backend de licence, aux CGV et aux procédures client.
4. Conserver le bouton APK désactivé jusqu’à signature, checksum et test réel.
5. Étendre le ruleset GitHub aux gates sécurité, frontend et Android stables.
6. Fermer les blocages runtime de l’issue #215 avant toute affirmation « plateforme production ».

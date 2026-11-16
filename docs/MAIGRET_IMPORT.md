# Import local Maigret — contrat v1

État : implémenté, expérimental. Aucun moteur Maigret n’est exécuté par Sentinel.
L’utilisateur fournit un rapport déjà obtenu dans un périmètre autorisé. Aucun
fichier, pseudonyme ou URL n’est envoyé à un tiers pendant cet import.

## Format vérifié

Adaptateur original écrit à partir du contrat de sérialisation de Maigret, commit
`29d61c6798a75eff7ecf3a8c19f7c0361997f026` consulté le 16 septembre 2026 :

- [generate_json_report](https://github.com/soxoj/maigret/blob/29d61c6798a75eff7ecf3a8c19f7c0361997f026/maigret/report.py)
- [MaigretCheckResult.json](https://github.com/soxoj/maigret/blob/29d61c6798a75eff7ecf3a8c19f7c0361997f026/maigret/result.py)

Le JSON simple est un objet indexé par nom de site ; chaque entrée comporte
`url_user` et un objet `status` contenant `username`, `site_name`, `url`, `status`.
Les deux URL doivent correspondre après normalisation ; les noms de site et les
pseudonymes, lorsqu’ils sont répétés, doivent être cohérents. Aucun code ou jeu de
données Maigret n’est incorporé. Les fixtures de test sont synthétiques.

**Limite critique :** l’export JSON standard examiné filtre les résultats pour
ne garder que `Claimed`. Il omet les erreurs et les résultats négatifs. Un site
absent du rapport ne signifie donc pas compte absent, site accessible, recherche
effectuée ni absence d’erreur. Sentinel ne reconstitue pas ces informations.
Si un fichier comporte explicitement `Available`, `Unknown` ou `Illegal`, ces
entrées sont comptées séparément et ne deviennent pas des comptes potentiels.

## Parcours

1. Ouvrir le carnet Investigations et choisir le JSON Maigret dans la zone dédiée.
2. Examiner les comptes potentiels et les compteurs de rejets. Rien n’est coché.
3. Sélectionner les résultats utiles, puis les ajouter au dossier existant.
4. Exporter le dossier Sentinel si nécessaire. Le fichier reste en clair.

Chaque sélection crée une entité `pseudonyme`, de nature `hypothese`, avec source
non vérifiée et sans relation d’identité automatique. Les descriptions, IDs,
tags, mots-clés et profils détaillés du rapport ne sont pas conservés.
Le champ optionnel `importProvenance` du dossier Sentinel v1 contient le format,
le SHA-256 des octets exacts du rapport, le statut déclaré `Claimed` et une date de
collecte `null`. La chronologie utilise explicitement la date d’import, pas une
date d’activité ou de collecte supposée. Une empreinte ne prouve ni l’authenticité
du rapport ni celle de son auteur. Une provenance réimportée reste déclarative.

## Bornes et échecs

- UTF-8 strict, JSON simple uniquement : NDJSON non pris en charge.
- Maximum 2 Mio et 1 000 entrées ; sélection 1–60 résultats, capacité totale du
  carnet inchangée à 60 entités / 120 relations / export 256 Kio.
- URL HTTPS sans identifiants uniquement, toujours affichée comme texte.
  Une URL HTTP est rejetée, jamais réécrite arbitrairement en HTTPS.
- Les lignes incompatibles sont comptées ; dépassement des limites du dossier
  rejette toute l’opération sans insertion partielle.
- Un même fichier/résultat importé deux fois est ignoré via son identifiant.
  Deux fichiers différents ne sont pas fusionnés automatiquement.
- Modification du dossier, annulation ou choix d’un nouveau fichier invalident
  la lecture précédente et son aperçu. Une erreur conserve le dossier courant.
- Aucun accès automatique aux URL, aucun contournement, aucune collecte en ligne.

## Validation

`npm run test:investigations` : contrat de fichier, SHA-256 exact, statut incertain,
minimisation, rejet URL, capacité atomique, aller-retour JSON, absence de fusion,
aperçu et sélection, affichage HTML comme texte et annulation des lectures tardives.
Tests DOM simulé : pas de certification visuelle mobile ou de taux de précision
Maigret. Aucun compte réel recherché pour ces tests.

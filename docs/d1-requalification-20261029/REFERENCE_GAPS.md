# Références et écarts structurels D.1

Base utilisateur : `92d1c916ebbdf393166fcf2fd904894ebee15661` ; branche dédiée `fix/d1-phone-messages-conformity-20261029`.

## Références visuelles reçues

Les visuels officiels ont été reçus dans cette conversation. Le registre `VISUAL_REFERENCES.md` identifie précisément les messages, pièces jointes et doublons. Les originaux ne sont pas matérialisés dans le système de fichiers ; aucun chemin local ni SHA d’original n’est inventé. Les illustrations intégrées sont des décorations générées, distinctes des maquettes et des captures de l’application.

La spécification textuelle reste `docs/superpowers/specs/2026-10-14-sentinel-full-visual-refactor-design.md`. Comparaison des sources aux images : partielle ; comparaison de captures réelles côte à côte : NON EXÉCUTÉE, faute de runtime utilisable. Le lot contient des doublons et plus de vues que le décompte initial de 13 ; le registre utilise des identifiants de vue plutôt qu’un total artificiel.

## Écarts identifiés dans les sources / retours terrain

| Exigence | Code présent | Écart ou limite | Action de cette PR |
|---|---|---|---|
| Langage D.1 partagé Phone/Contacts/Messages | Tokens/chrome D.1 présents, composition mêlant cartes techniques et blocs locaux | Hiérarchie inégale ; comparaison des sources seulement ; rendu à capturer | En-tête de communication partagé, verre/tokens existants, titres sémantiques, corps lisible et flexible |
| Téléphone étroit/police agrandie | Champ Numéro et Vérifier dans une rangée pondérée | Risque de compression horizontale | Champ pleine largeur, actions à retour à la ligne ; gestes/rôles/API d’appel inchangés |
| Contacts Sentinel enrichis | Carnet Android lu, favoris/filtrage, sélection multi-numéros | **Non développé** : modèle Sentinel indépendant riche (e-mails, anniversaire, provenance par champ, synchro opt-in) décrit par la spécification | Pas de faux écran de création/synchro ; fiche locale avec photo réelle bornée, appels/messages, édition déléguée à Contacts Android et partage explicite, stockage inchangé |
| Messages / identité et contexte | Conversations provider, composition/réponse, MMS, analyse de liens et confirmations | Refonte complète illustrée non démontrée ; pas de preuve opérateur SMS/MMS de cette PR | En-tête compact, panneau de risque fondé sur les résultats locaux, état vide lisible sans données factices ; transports et confirmations préservés |
| Bluetooth / commandes sans chevauchement | Scanner flottant sur le contenu ; commande d’appairage dans le défilement | Chevauchement rapporté par utilisateur, contenu sans espace FAB réservé | Scanner dans bottomBar réservée par Scaffold ; appairage reste une action Android distincte |
| OSINT 3 sources accessibles | URLs www.cert.ssi.gouv.fr, redirections désactivées | **Reproduit sur hôte : 302 pour 3/3**, flux non suivis | URLs canoniques cert.cyber.gouv.fr : HTTP200 et RSS valides pour 3/3 ; refus des redirections conservé |
| PTT en session | Client authentifié, jetons éphémères, LiveKit/audio transformé, fermeture fail-closed | **Bloqué** : santé live200 mais ready503 ; aucune session distante affirmée | Erreurs403/401/503 lisibles sans exceptions brutes ; diagnostic distinct vie/préparation |
| Transformation de l’audio d’un appel SIM | Le code bloque une session PTT pendant un appel mobile actif | **Non développé / non pris en charge par l’architecture courante**, ce n’est pas une option manquante à activer | Pas de contournement Android ni de faux bouton ; périmètre PTT distinct des appels opérateur |
| Profilage/risque et protection | Moteurs et présentations locaux existants | Présence du code ≠ preuve matérielle/commerciale | Aucun statut vert ni chiffre de protection ajouté par la nouvelle présentation |
| Conservation | SQLite/providers SMS/MMS, Room et préférences existants | Toute migration visuelle risquerait les données | Aucun DAO, schéma, migration, préférence ou transport modifié ; empreinte Room contrôlée |

## Lecture de la matrice

`SURFACE_INVENTORY.csv` recense chaque destination réelle avec son fichier source, sans confondre classe composable, Activity et module. Les statuts « implémenté » décrivent la présence d’un parcours en code, pas un PASS matériel. Pour les services, `SERVICE_DIAGNOSTICS.md` sépare sondes publiques réellement exécutées, tests hôte et état utilisateur rapporté.

L’installation sur Galaxy S24+ Android17 est **rapportée réussie par l’utilisateur pour l’APK précédent**. Ce constat ne valide ni les nouveaux correctifs ni les autres fonctions. Cette PR produit un candidat à requalifier, pas un D.1 déclaré terminé.

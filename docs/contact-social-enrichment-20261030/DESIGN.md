# Enrichissement social des fiches Contacts — recherche assistée confirmée

## Demande et état réel

Le propriétaire confirme vouloir rechercher des profils à partir du **nom et de l’entreprise**, puis confirmer chaque correspondance avant enrichissement. Ce volet appartenait à l’objectif des références D.1 (R03 : profils liés), mais n’a pas été livré dans les versions12/13. Le candidat13 ne traite que le rebond de défilement. Le présent document n’est ni un connecteur opérationnel ni une preuve de recherche réussie.

Audit du code/env courant : ContactIntelligenceAdapter ne publie que des observations Android locales ; ExternalMessagingLinks construit des liens publics initiés par utilisateur ; CommunicationsHubPolicy ne découvre pas de comptes ; Maigret est un import local expérimental du carnet web Investigations, sans moteur exécuté et sans liaison aux Contacts Android. Aucune configuration native d’API/client LinkedIn/Meta/X ni variable correspondante trouvée dans cet environnement. Les configurations distantes et les secrets ne sont pas réputés inspectés. Le connecteur LinkedIn de la session ChatGPT n’accorde pas une API à l’APK.

## Approche retenue et alternative

Le propriétaire a retenu **recherche assistée puis confirmation** plutôt qu’une liaison automatique ou uniquement la saisie de liens déjà connus. L’intégration opérationnelle doit utiliser un fournisseur/API dont les droits de recherche et de lecture de profils tiers sont effectivement établis. Une connexion OAuth ordinaire ou l’accès à son propre profil ne suffit pas à promettre une recherche de toutes les personnes. Aucun abonnement/service payant créé ou déclenché.

Alternative si ces droits ne sont pas disponibles : recherche externe volontaire dans le navigateur puis ajout manuel d’un profil confirmé. Cette alternative peut rendre le parcours utile, mais doit porter son vrai nom et ne pas être livrée comme une collecte automatique par API. Elle ne remplace pas implicitement la demande retenue.

## Parcours concret proposé

1. Dans la fiche, bloc **Profils et informations externes**, séparé des données Android.
2. Action **Rechercher des profils**. Écran d’aperçu modifiable du nom et de l’entreprise, choix LinkedIn/Instagram/X, indication des champs et destinataires transmis. Aucun numéro, SMS, journal, note, adresse personnelle, photo Android ou carnet entier joint à la recherche.
3. Chaque réseau expose sa capacité réelle : Non connecté, Accès insuffisant, Disponible, Indisponible temporairement, Quota atteint. Un connecteur sans accès ne fabrique aucun résultat, aucune photo et aucun score.
4. Résultats candidats bornés : réseau, nom/identifiant, URL canonique, contexte professionnel/localisation lorsqu’effectivement retournés, source et date de collecte réelle. Même nom ≠ même identité ; aucune association automatique et aucune sélection par défaut.
5. Le propriétaire choisit un candidat et voit à nouveau **le contact cible et le profil source** avant de confirmer. Le statut est **Associé par vous**, jamais « identité vérifiée » pour une simple confirmation manuelle.
6. Choix explicite des informations conservées dans Sentinel : photo publique, présentation, entreprise/fonction, localisation publique, sites et autres champs effectivement disponibles. Les valeurs Android d’origine ne sont pas écrasées. Les champs manuels, importés et collectés par API restent distingués avec provenance/date.
7. Actualisation et retrait explicites. Aucune synchronisation en arrière-plan du carnet. Une suppression de liaison ne supprime ni le contact Android ni un compte social.

## Architecture et invariants

- Sous-système d’enrichissement de contact distinct du module Social Intelligence/Foreign Interference. Conserver l’isolation Phone Core définie dans docs/SOCIAL-INTELLIGENCE-EVIDENCE-MODEL.md : pas d’import de contacts/SMS/appels dans ce module ni jointure implicite. Le consentement de recherche est un contrat ciblé de la fiche, pas une exception globale d’export du carnet.
- Port de connecteur séparant recherche et lecture de profil ; fournisseur réel seulement après vérification de ses capacités, autorisations, conditions d’utilisation, quotas et coûts. Aucun secret embarqué dans l’APK ni export de la clé de signature. OAuth/PKCE ou backend ne seront choisis qu’après examen des exigences du fournisseur ; aucun nouveau déploiement facturable.
- Requêtes liées à l’identité Android stable/lookup et à une génération. Changement rapide de contact/requête/réseau, fermeture et perte de permission invalident les anciens résultats et la confirmation. Aucune publication d’un résultatA dans la ficheB.
- Données bornées et validées : schéma, URL HTTPS de domaines attendus, identifiants, taille JSON/image, nombre de résultats. Aucune navigation vers une URL fournie arbitrairement, aucun rendu HTML exécuté. Pas de recherche de numéro privé ni de lecture de messageries privées.
- Stockage local privé des seules liaisons/informations sélectionnées, avec provenance et possibilité de retrait. Pas de migration Room ni modification des schémas existants dans ce premier parcours ; le choix de stockage doit préserver les données existantes et ne pas exposer les associations aux exports/logs automatiques.
- Les profils privés/inaccessibles restent explicitement inaccessibles. Aucune promesse d’exhaustivité ni inférence de données sensibles pour remplir une fiche.

## Validation requise avant livraison

- Tests de contrats des fournisseurs sur fixtures synthétiques : recherche, permissions/API manquantes, erreurs, quotas, résultats homonymes, données malformées, profils supprimés et URLs hors domaine.
- Tests de confirmation : rien lié avant confirmation, rien écrit dans Contacts Android, refus/annulation sans effet ; provenance conservée et retrait ciblé.
- Tests asynchrones : rechercheA→B, retour tardif, changement de contact, fermeture/recréation/révocation ; aucune confusion d’identité ni association implicite.
- UI réellement utilisée dans D1ContactProfileSheet, états non connecté indisponible honnêtes, parcours de confirmation testé physiquement ; aucun composant dormant déclaré livré.
- Essai opérationnel de chaque connecteur avec compte de test autorisé et droits établis, sans dépense non autorisée. Les fixtures et tests JVM ne constituent pas cette preuve.
- Maquette D.1 comparée sur S24+, navigation bas de fiche et TalkBack/texte200% séparément qualifiés. Ne pas mélanger cette intégration au verdict de la mitigation13.

## Information manquante avant collecte API

Question posée au propriétaire : dispose-t-il d’un accès développeur/API destiné à Sentinel pour LinkedIn, Instagram/Meta ou X ? Demander seulement les services/capacités, **jamais les mots de passe, clés ou jetons dans la conversation**. En l’absence de ces droits, signaler le blocage opérationnel et faire choisir explicitement l’alternative assistée via navigateur ; ne pas fabriquer de connecteur ni multiplier les recherches de contacts privés pour tester.

Aucune recherche de personne réelle, collecte sociale, accès à un secret, modification de fonctionnalité Android, compilation ou nouvelle installation n’est effectué dans cet audit de conception. PR1748 reste en brouillon et le candidat13 est inchangé.

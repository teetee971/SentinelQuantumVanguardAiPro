# Fiche contact locale — périmètre du correctif demandé

Demande utilisateur : « tout », après signalement d'une fiche incomplète et demande de préciser informations, actions et présentation D.1. Références : R03 et C01, dans le registre D.1 existant ; captures physiques privées dans la conversation, non publiées.

Choix d'implémentation : conserver Kotlin/Compose et le fournisseur Contacts Android. Une lecture des rubriques du seul contact sélectionné évite de charger les notes/adresses de tout le carnet. Le résultat est lié à l'identifiant, révision, génération de retour et activation. Aucune écriture fournisseur, aucune migration Room, aucune synchronisation distante.

Données courantes : nom structuré/phonétique, téléphones et libellés, e-mails, adresses, entreprise/fonction/service/détails professionnels, dates, notes, sites/profils importés, autres noms, relations, identifiants de messagerie et SIP. Les libellés et les valeurs sont ceux du fournisseur ; dates affichées sans déduction d'âge ni date inventée. Champs vides distingués d'un échec de lecture. Les colonnes propriétaires de comptes tiers, groupes et informations hors fournisseur ne constituent pas une intégration universelle de tous les carnets.

Présentation : feuille ouverte entièrement, fermeture fixe, contenu défilable, photo réelle intégrée au globe décoratif, identité locale, modifier/partager puis coordonnées et rubriques. Palette cyan locale explicite. Les contrôles de transport restent ceux de l'application ; aucun appel/envoi n'est lancé par l'ouverture de la fiche.

Alternatives écartées : importer tout le carnet en mémoire (données privées inutiles), inventer les scores/profils sociaux de la maquette (affirmations injustifiées), remplacer toutes les interfaces Téléphone/Messages dans ce correctif (risque de régression sans rapport avec la fiche).

La conformité D.1 reste partielle : globe décoratif généré déjà présent, aucun original R03 disponible comme fichier de travail local ; pas de capture runtime du nouvel écran avant validation propriétaire. Historique des communications et vérification sociale non intégrés. Le candidat12 devra être signé localement avec la clé originale et vérifié avant toute mise à jour autorisée ; aucune installation automatique.

# Essai propriétaire S24+ — candidat17

Après signature locale17 et autorisation explicite de mise à jour manuelle, le propriétaire a montré le fichier SIGNED dans Download et le parcours d'installation/Play Protect. À la question « Une fois l'installation réussie, teste la fiche Contacts en bas de page : le défilement bloque-t-il encore ? », il répond **« Non »**.

**Résultat : absence de gel rapportée par le propriétaire pour cet essai de défilement en bas de fiche.** C'est un premier résultat physique favorable à la mitigation17, pas une reproduction/mesure indépendante dans le cloud ni une preuve causale définitive. Les gestes du panneau sont désactivés ; le défilement du contenu reste actif.

Source du candidat : `76911552583432a776ddcfda7a64bc161c0035d7`. Le terminal propriétaire avait confirmé signature/intégrité, certificat historique et versionCode17 avant cette mise à jour autorisée. Aucun dumpsys récent ni APK installé relu par le cloud. Ne pas déduire de ce retour une validation de Retour, recréation d'Activity, permissions, texte agrandi, changement rapide de contact ou des26scénarios complets.

Le SHA du fichier unsigned a été confirmé sur le téléphone. Le SHA signé n'est pas retranscrit ici : la réponse précédente de l'assistant en a donné une chaîne tronquée, qui ne doit pas servir de référence. Une sortie textuelle sha256sum complète du SIGNED sera nécessaire pour figer cette empreinte propriétaire ; aucune nouvelle signature requise pour cela.

Suites statiques/JVM inchangées, aucun nouveau build nécessaire pour ce compte rendu documentaire. Tests instrumentés toujours NON EXÉCUTÉS. Conformité D.1 complète et fiabilité du filtrage non établies. Aucune nouvelle installation, désinstallation, suppression de données, clé exportée ou fusion main.

## Captures complémentaires du lot Contacts/navigation

Le propriétaire transmet ensuite des vues du haut, du milieu et du bas d'une fiche sans photo (avatar initiale), une autre fiche avec photo, et l'écran Communications. Les deux fiches présentent des noms et coordonnées distincts ; aucun mélange manifeste d'identité n'est visible sur ces images. La fiche sans photo montre aussi les rubriques nom structuré, e-mail, entreprise et date importées, ainsi que les états non renseignés. Aucun nom, numéro, adresse, date personnelle, portrait ou capture brute publié dans ce rapport.

P11 reste PARTIEL : affichage de photo/initiale et de champs supplémentaires observé sur ces exemples, sans comparaison exhaustive avec le provider Android, ni validation d'une succession rapide A/B/A. P24 reste PARTIEL : écran Communications observé, mais captures statiques insuffisantes pour prouver les actions Retour/Fermer/réouverture ou la transition Contacts vers Messages (Communications est un écran différent). La déclaration précédente d'absence de gel reste la preuve utilisateur du sous-cas défilement17 ; ces images seules ne mesurent pas sa fluidité.

Les canaux externes indiqués non raccordés ne deviennent pas des connecteurs actifs parce que la carte Profils liés est visible : celle-ci demeure une association manuelle assistée navigateur, sans collecte API. Aucune action de recherche ni transmission effective n'est prouvée par ces captures.

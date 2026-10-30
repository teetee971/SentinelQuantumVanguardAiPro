# Essai propriétaire S24+ — candidat17

Après signature locale17 et autorisation explicite de mise à jour manuelle, le propriétaire a montré le fichier SIGNED dans Download et le parcours d'installation/Play Protect. À la question « Une fois l'installation réussie, teste la fiche Contacts en bas de page : le défilement bloque-t-il encore ? », il répond **« Non »**.

**Résultat : absence de gel rapportée par le propriétaire pour cet essai de défilement en bas de fiche.** C'est un premier résultat physique favorable à la mitigation17, pas une reproduction/mesure indépendante dans le cloud ni une preuve causale définitive. Les gestes du panneau sont désactivés ; le défilement du contenu reste actif.

Source du candidat : `76911552583432a776ddcfda7a64bc161c0035d7`. Le terminal propriétaire avait confirmé signature/intégrité, certificat historique et versionCode17 avant cette mise à jour autorisée. Aucun dumpsys récent ni APK installé relu par le cloud. Ne pas déduire de ce retour une validation de Retour, recréation d'Activity, permissions, texte agrandi, changement rapide de contact ou des26scénarios complets.

Le SHA du fichier unsigned a été confirmé sur le téléphone. Le SHA signé n'est pas retranscrit ici : la réponse précédente de l'assistant en a donné une chaîne tronquée, qui ne doit pas servir de référence. Une sortie textuelle sha256sum complète du SIGNED sera nécessaire pour figer cette empreinte propriétaire ; aucune nouvelle signature requise pour cela.

Suites statiques/JVM inchangées, aucun nouveau build nécessaire pour ce compte rendu documentaire. Tests instrumentés toujours NON EXÉCUTÉS. Conformité D.1 complète et fiabilité du filtrage non établies. Aucune nouvelle installation, désinstallation, suppression de données, clé exportée ou fusion main.

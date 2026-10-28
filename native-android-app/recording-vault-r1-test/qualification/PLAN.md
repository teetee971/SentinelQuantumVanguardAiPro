# Recording Vault R3 — plan d'exécution

Base : 7b4b84363feac5c691dd9861bd044be7ca34acfc. Périmètre : ce module isolé uniquement.

1. Auditer Store/Crypto et conserver les six classes de tests R1/R2 intactes.
2. Reproduire sur JVM les défauts de publication et de clé absente, avec adaptateurs Android explicitement simulés.
3. Corriger sans changer alias, répertoire ou enveloppe R1/R2 : publication sans remplacement, lecture bornée sans suivi du lien final, verrou de fichiers commun aux instances/processus du Store, déchiffrement sans création de clé.
4. Ajouter instrumentation de persistance write/verify/cleanup, défi cryptographique conservé entre processus, limites/corruption/interruption et protocole ADB extérieur au processus tué.
5. Exécuter les tests JVM puis la compilation Android isolée ; exécuter l'instrumentation seulement si un appareil est connecté. Conserver commandes, codes retour, sorties exactes.
6. Rapporter explicitement les limites : accès hostile même UID, répertoire remplacé, rollback de chiffrés valides, durabilité coupure électrique, API minimale, génération de clé hors Store et qualification physique.
7. Relire le diff de périmètre puis publier uniquement la branche demandée et une PR brouillon, sans fusion.

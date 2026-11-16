# Cycle4 — journal IA persistant — NO-GO production

Base de travail2cfd5af01196eee16e156cb4387a3655fa990339; transfert précédent
67bc2a74cb08359c33ede70ed0dbcae33698bebf. Branche audit/sentinel-integral-p0-20261116.
Budget0USD; aucune clé cloud, aucune facturation, aucun déploiement.

Le journal ajouté est une brique préparatoire, pas une intégration IA achevée.
Il ne reçoit que des types d'événements fermés, un compteur et l'uptime Android.
Il ne reçoit pas de texte, réponse du moteur, numéro, identifiant utilisateur,
jeton d'approbation ou secret d'appariement. Le HMAC authentifie la chaîne et une
ancre séparée. Une altération, troncature, mauvaise clé, fichier manquant ou
incohérence de commit est refusée sans réinitialisation silencieuse.

Une paire de fichiers sauvegardée ensemble puis restaurée à un ancien état
valide reste indétectable. Ce mécanisme n'est pas un compteur matériel antirollback.
Un attaquant contrôlant le processus/UID de l'application n'est pas isolé par ce
journal. La capacité est volontairement bornée256événements; au-delà,il refuse
l'écriture. Rotation/export explicite et UX de maintenance restent à développer
avant un usage soutenu. Aucune preuve d'absence totale de défaut ne découle des tests.

L'adaptateur Android choisit AndroidKeyStore et noBackupFilesDir. Une clé existante
impose le journal existant:aucune régénération après perte des fichiers. Un verrou
interprocessus protège l'initialisation de la clé, un autre les écritures. La
copie temporaire est synchronisée puis renommée dans le même dossier sous Linux/
Android; après un crash entre les deux commits, une incohérence doit désactiver
les effets IA plutôt que revendiquer un journal complet. L'appelant futur devra
traiter ce refus explicitement. Aucune écriture n'est lancée au démarrage Android.

Le test AndroidKeyStore ajouté emploie un alias et un dossier cache UUID de test;
il ne touche jamais au journal ou à la clé applicative. Il vérifie clé non
exportable,rechargement,écriture et rejet d'altération. Sa compilation ne vaut
pas exécution sur appareil. Commande ciblée à exécuter sur S24+ de test:

    adb shell am instrument -w -e class com.sentinel.quantum.ai.SentinelAiAuditJournalInstrumentationTest com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner

Les dix tests JVM de journal couvrent réouverture,altération,fichiers manquants,
rollback d'un fichier,mauvaise clé,refus d'écrasement,capacité,concurrence entre
instances,taille excessive et uptime négatif. Le rouge initial avant création
des classes et le vert sont conservés dans les preuves du cycle.

P0 encore ouverts:le moteur,le consentement,l'authentification humaine,les actions
et ce journal ne sont pas reliés à un parcours applicatif Android complet. Aucun
Phone Core/SMS/MMS/VPN/Wi-Fi/Bluetooth n'est exposé au moteur. Le manifeste et
ses permissions sont conservés, y compris le refus HTTP cleartext. Aucun accès
au modèle local depuis l'application n'est déclaré opérationnel. Les résultats
x86_64 du cycle3 restent des preuves hôte,pas des tests S24+.
P1:LiveKit reste sans barrière fournisseur de révocation validée; cette limite
bloquante est inchangée. Aucun rôle Android n'est affaibli pour contourner un test.

## Résultats de ce cycle

Compilation Kotlin,régression JVM,compilation instrumentée,assemblage debug et
assemblage des tests instrumentés ont tous terminé BUILD SUCCESSFUL.
341 suites,1614 tests JVM,0 échec,0 erreur,0 ignoré. Le test AndroidKeyStore est
compilé mais NON exécuté,aucun appareil ADB disponible. Les signatures des deux
APK sont vérifiées avec apksigner(exit0). Les101 fichiers protégés restent
identiques à l'archive validée. Commandes exactes et empreintes dans validation.json;
rapport ciblé dans journal-junit.xml. Les preuves complètes sont conservées dans
l'environnement,mais leur téléchargement ChatGPT demeure non fiable. Aucun
rapport de ce cycle ne prétend à une validation physique ou de bout en bout.

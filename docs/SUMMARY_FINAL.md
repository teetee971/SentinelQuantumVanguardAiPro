# Résumé final — état historique

Ce document est conservé comme trace historique d'un ancien chantier. Il ne constitue pas la source de vérité actuelle du projet.

## État actuel à consulter

Le projet Sentinel Quantum Vanguard AI Pro est maintenu avec :

- une surface web/PWA à la racine du dépôt ;
- un projet Android canonique dans `native-android-app/` ;
- un workflow de build Android séparé ;
- un workflow de release Android déclenché par tags de version et contrôlant l'appartenance du tag à `main` ;
- des contrôles d'isolation, de gouvernance IA, de sécurité, de fuzzing, d'intégrité et de supply chain ;
- une séparation stricte avec A KI PRI SA YÉ.

## Règle de validation

Les affirmations historiques telles que « tous les workflows passent », « 0 vulnérabilité », « prêt pour production », des scores Lighthouse estimés ou des statistiques de livraison ne doivent pas être considérées comme des mesures actuelles sans preuve datée et reproductible.

La validation actuelle doit distinguer :

1. correction du code ;
2. test local ;
3. exécution CI réussie ;
4. artefact vérifié ;
5. validation de sécurité.

## Blocage CI connu

Un problème d'exécution des runners GitHub Actions, avec échecs observés avant l'exécution normale des étapes, est documenté dans l'issue #195. Tant que ce problème n'est pas résolu et que les contrôles concernés n'ont pas réellement exécuté leurs étapes, aucune validation CI globale ne doit être déclarée verte.

## Android

`native-android-app/` est la seule source Android maintenue. Les anciens chemins et anciens workflows mentionnés dans cette archive ne sont plus des sources de vérité.

## Séparation des projets

Sentinel Quantum Vanguard AI Pro et A KI PRI SA YÉ sont deux projets totalement séparés. Cette séparation inclut le code, les dépendances, la configuration, les secrets, les workflows et les intégrations opérationnelles.

## Conclusion

Utiliser les workflows actifs et la documentation actuelle pour toute décision de build, de release ou de sécurité. Ce fichier ne constitue qu'un historique nettoyé des anciennes affirmations.
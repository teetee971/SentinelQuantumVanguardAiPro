# Architecture technique — référence de conformité

## Statut

Cette page est une référence documentaire actuelle, mais elle ne constitue pas à elle seule une preuve de conformité juridique, de sécurité ou de production. Les affirmations doivent être corroborées par la configuration source et les contrôles exécutés.

## Périmètre

Sentinel Quantum Vanguard AI Pro comprend une surface web/PWA et une surface Android maintenue. Le projet Android canonique est `native-android-app/`.

La documentation historique qui décrivait React Native, des flavors Institutional/Public, des permissions téléphoniques sensibles ou l'ancien chemin `android-app/android/` n'est plus une description de l'architecture actuelle.

## Sécurité Android

Les permissions déclarées doivent rester minimales et justifiées par les fonctionnalités effectivement présentes. Aucun accès aux communications, aucun enregistrement audio ou aucune surveillance intrusive ne doit être déduit d'une ancienne documentation.

Les secrets de signature ne sont pas stockés dans Git. Le workflow de release utilise les secrets GitHub Actions et un fichier de travail temporaire protégé.

## Web/PWA

La surface web utilise la chaîne de build actuellement définie par le dépôt. Le déploiement de production visé est Cloudflare Pages. Les versions et dépendances documentaires doivent être vérifiées contre les fichiers de configuration présents avant toute déclaration de compatibilité.

## Gouvernance IA et actions

Les décisions sensibles sont soumises aux contrôles de politique, d'intégrité des preuves, de confiance, d'incertitude et de simulation. Les actions critiques nécessitent les autorisations prévues et une validation humaine. Le système ne doit pas transformer une inférence non vérifiée en preuve.

## Validation CI

Les workflows actifs sont la source de vérité pour la validation automatisée. Un contrôle qui n'a pas effectivement exécuté ses étapes ne peut pas être déclaré réussi.

Un blocage de runners GitHub Actions est documenté dans l'issue #195. Tant que les contrôles concernés ne s'exécutent pas normalement, la validation CI globale reste non démontrée.

## Séparation Sentinel / A KI PRI SA YÉ

Sentinel Quantum Vanguard AI Pro est totalement séparé de A KI PRI SA YÉ. Cette règle couvre le code, les dépendances, les configurations, les secrets, les workflows et les intégrations opérationnelles.

## Règle de conformité

Ce document ne remplace ni une analyse juridique, ni un test de sécurité, ni une exécution CI. Toute déclaration de conformité ou de production doit être fondée sur des preuves actuelles, datées et reproductibles.
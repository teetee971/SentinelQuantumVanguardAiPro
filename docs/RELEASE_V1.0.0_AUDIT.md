# Audit de la release `v1.0.0-release`

## Statut

`v1.0.0-release` est une release historique. Elle ne constitue pas une preuve de sécurité, de validation CI, de readiness production ou de conformité pour l'état actuel du projet.

## Constat vérifié

- Le tag `v1.0.0-release` pointe vers le commit historique `f3cc177d912ca80d0f28fafa1c596115e52c495c`.
- La release ne contient actuellement aucun asset publié.
- Le `package.json` associé à cet état est antérieur à la chaîne actuelle de contrôles de sécurité et de gouvernance.
- Aucun statut CI exploitable n'a été trouvé pour établir rétroactivement une validation de cette release.
- Les contrôles d'exécution, de liaison d'opération, d'anti-rejeu et de binding de simulation ajoutés sur les branches de sécurité ultérieures ne doivent pas être attribués à cette release historique.

## Décision red-team

Ne pas réutiliser `v1.0.0-release` comme référence pour une nouvelle publication ou comme preuve de sécurité.

Ne pas déplacer ni réécrire le tag historique uniquement pour masquer cet écart. L'historique doit rester traçable.

Toute nouvelle release doit être créée à partir d'un commit actuellement vérifié et satisfaire `RELEASE_CHECKLIST.md`, notamment les preuves de build, signature, tests, isolation, CI et publication des artefacts.

## Règle de communication publique

Toute documentation ou page de release faisant référence à `v1.0.0-release` doit la présenter comme historique. Les formulations telles que « sécurité confirmée », « build vérifié » ou « production-ready » ne doivent pas être reprises comme des preuves actuelles sans éléments reproductibles correspondants.

**Dernière vérification :** septembre 2026

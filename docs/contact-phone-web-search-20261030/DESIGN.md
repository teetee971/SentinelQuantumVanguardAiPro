# Recherche web explicite d’un numéro de contact

Demande : permettre une recherche internet via un numéro de téléphone, si possible. Implémentation ciblée : bouton dans la vraie rubrique Téléphone de la fiche, puis dialogue affichant le numéro normalisé et DuckDuckGo avant un second geste de confirmation. Recherche publique dans le navigateur, sans API payante, récupération ou association automatique.

Seul le numéro choisi est transmis ; ni nom/entreprise, carnet entier, notes, SMS ou historique. Pas de recherche sur ouverture, en arrière-plan, ni de preuve d’identité déduite des résultats. Numéro éventuellement réattribué, homonymie/annuaire obsolète : vérifier chaque source. Aucune recherche réelle d’un numéro utilisateur dans le cloud.

Normalisation : retirer séparateurs visuels connus, garder l’indicatif fourni, accepter5–15 chiffres avec + initial éventuel ; pas d’indicatif supposé, extension, commandes USSD, paramètres de requête, contrôle ou expression libre. Requête littérale entre guillemets encodée en UTF8. Pas de modification du numéro dans Contacts Android.

Avant l’ouverture externe, vérifier hors UI la permission Contacts, le LOOKUP_KEY courant du contact sélectionné et la présence du même numéro dans ses lignes fournisseur. Retour tardif, changement de contact/numéro, fermeture ou pause annulent l’ouverture ; vérifier aussi permission et lifecycle RESUMED avant Intent. Confirmation jamais restaurée après recréation.

Validation : tests JVM de normalisation, indicatif conservé, encodage et rejets ; tests de la vraie UI (aucune recherche avant confirmation, annuler et changer de numéro/contact ne lancent rien), compilés seulement si aucun runtime Android. Suite Android complète, contrôles Node, APK versionCode15 et rapport source/signature/alignement. Conserver Room, brouillon1748, aucun merge/installation automatique/secret changé.

## Vérifications réalisées
- RED d’API absente constaté avant implémentation ; tests de requête GREEN3/3, puis suite app1437/1437 (0échec/erreur/ignoré),307XML. Wearable26/26 inchangé/caché. Node produit69/69 et livraison15/15.
- Build Kotlin/APK debug/test/unsigned exit0,1m41. Trois nouveaux tests instrumentés compilés (confirmation/annulation, changement d’identité/numéro, vrai guard avec fournisseur synthétique et révocation) : NON EXÉCUTÉS sans runtime. Aucun RED/GREEN runtime ou succès de recherche internet réelle prétendu.
- Revue indépendante ciblée : aucun défaut important relevé dans geste, permission, propriété, annulation ou URL ; revue statique seulement.
- Intégration réelle : D1ContactProfileContent ajoute ContactPhoneWebSearchButton à chaque numéro frais valide. Confirmation non restaurée ; scope lié contactId/lookup/numéro. Lecture IO puis permission/RESUMED/annulation avant Intent.
- Rebond13 et parcours social14 conservés ; aucun verdict de résolution du gel ou de conformité visuelle D.1. Aucune donnée privée recherchée ni publiée.

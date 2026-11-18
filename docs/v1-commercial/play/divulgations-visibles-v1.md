# Divulgations et consentements visibles · Sentinel Phone Core V1

**Brouillon au 18 novembre 2026.** Textes proposés pour l’application. Cette revue ne modifie pas le code. Un texte marqué « déjà affiché » est celui lu dans les sources. Un texte marqué « non branché » est un blocage tant qu’il n’apparaît pas dans le parcours.

La politique User Data exige une divulgation dans l’application avant un accès à des données personnelles ou sensibles que la personne ne peut pas raisonnablement attendre, puis un geste d’accord distinct. Le lien de politique ne remplace pas cette divulgation. [User Data](https://support.google.com/googleplay/android-developer/answer/10144311) · [Bonnes pratiques de divulgation](https://support.google.com/googleplay/android-developer/answer/11150561).

Pour une application dont le cœur est le téléphone et les SMS, le choix du rôle système est le moment attendu. Les cartes déjà présentes dans l’assistant expliquent ce rôle avant le dialogue Android. Elles n’ont pas le bouton séparé « Accepter » / « Plus tard » décrit dans les bonnes pratiques. Google peut l’accepter pour un gestionnaire par défaut, ou demander le texte plus complet ci-dessous. Le conserver comme texte de repli, sans l’ajouter au code dans cette revue.

## Déjà affiché

| Emplacement | Texte lu | Limite |
| --- | --- | --- |
| Assistant, carte Téléphone | « Permet à Sentinel de composer les appels et d’afficher son interface pendant les appels entrants/sortants. » | N’explique pas le journal d’appels. |
| Assistant, carte Filtrage | « Active le service système de filtrage pour appliquer les règles locales avant l’affichage de l’appel. » | Convient au rôle de filtrage, qui n’est pas le journal. |
| Assistant, carte Contacts et historique | « Utilisés localement pour afficher les contacts et les appels récents dans le composeur Sentinel. » | La demande de journal n’est ajoutée que si le rôle Téléphone est déjà détenu. |
| Assistant, SMS | Le détail liste le rôle, l’envoi, la lecture, la réception et la SIM. Le bouton de permission n’apparaît qu’après le rôle. | Suffisant pour l’ordre exigé par la politique SMS. |
| Assistant, MMS | « Android doit autoriser la réception des MMS et des messages WAP Push. » Le bouton n’apparaît qu’après le rôle SMS. | La quarantaine des formats non retenus est mentionnée. |
| Assistant, plein écran | « Requis pour présenter de façon fiable l’interface d’appel entrant lorsque l’écran est verrouillé. » | Réservé aux appels. |
| Export SMS | « L’export peut contenir les numéros de téléphone, le texte des SMS et leurs dates. Le fichier ne sera partagé qu’avec l’application que vous choisirez ensuite. » Boutons Annuler et confirmer. | Déjà conforme à une action choisie. |
| Paramètres, aperçu SMS | « Inclut l’expéditeur et un extrait du message dans les notifications Sentinel. » Désactivé par défaut. | Déjà un choix. |
| Paramètres, catalogues | « Actualise les copies publiques ARCEP et RTR. Une recherche manuelle peut aussi télécharger la copie publique si elle manque sur l’appareil. » | L’interrupteur ne bloque pas la recherche manuelle. |
| Paramètres, politique | Ouvre `https://sentinelquantumvanguardaipro.pages.dev/public/privacy.html`. | Cette URL décrit la vitrine de septembre 2026, pas cette politique V1. |

L’écran « Conformité et souveraineté » parle encore de réputation distante, d’OSINT et d’une liste de permissions trop courte. Il n’est pas dans le graphe V1. Le laisser hors du profil. Ne pas le réactiver sans réécrire ses phrases.

## Texte de repli avant le rôle Téléphone

Titre : Téléphone et appels récents

Sentinel a besoin d’être l’application Téléphone pour passer vos appels et afficher son écran pendant l’appel. Ensuite seulement, l’application peut demander à lire le journal d’appels, afin d’afficher vos appels récents dans le composeur. Ces données restent sur le téléphone. Sentinel ne les envoie pas à son éditeur.

Android va demander votre accord. Vous pouvez refuser. Le filtrage local, s’il est disponible, se règle à part et ne donne pas accès au journal.

Boutons proposés : Continuer vers Android · Plus tard

## Texte de repli avant le rôle SMS

Titre : SMS et MMS

Sentinel a besoin d’être l’application SMS pour envoyer, recevoir et afficher vos SMS et MMS. Les messages restent dans le téléphone et passent par votre opérateur. Sentinel ne les envoie pas à son serveur. Un export n’a lieu que si vous le demandez, après une confirmation, vers l’application que vous choisissez.

Vous pouvez refuser. Les autres écrans restent ouverts, sans la messagerie.

Boutons proposés : Continuer vers Android · Plus tard

## Texte de repli avant les contacts

Titre : Contacts sur ce téléphone

Si vous l’autorisez, Sentinel lit le nom et l’organisation d’un contact pour les afficher pendant un appel ou une recherche. Cette lecture reste sur le téléphone. Vous pouvez refuser : le composeur fonctionne sans le répertoire. Vous pouvez retirer l’autorisation dans les réglages Android.

Boutons proposés : Continuer · Plus tard

## Texte non branché · premier téléchargement de catalogue

À placer avant le premier GET, y compris celui d’une recherche manuelle, et pas seulement à côté de l’interrupteur périodique.

Titre : Plans de numérotation publics

Pour afficher le titulaire publié d’une tranche de numéros, Sentinel peut télécharger une copie publique ARCEP ou RTR. Le numéro que vous cherchez n’est pas placé dans l’adresse de téléchargement. La comparaison se fait sur le téléphone. Votre adresse IP est toutefois visible par l’hébergeur de la copie, comme pour toute page HTTPS.

Ce téléchargement est facultatif. Sans lui, l’appel, les SMS et le filtrage local restent disponibles. Le résultat ne prouve pas l’opérateur actuel après portabilité.

Boutons proposés : Télécharger la copie · Continuer sans télécharger

Blocage : `SentinelDialerActivity.lookup()` appelle le client ARCEP ou RTR, et ce client télécharge la copie s’il n’en a pas en mémoire. L’interrupteur des paramètres ne commande que le travail périodique. Tant que le code n’affiche pas ce choix avant le GET, la divulgation des paramètres n’est pas au bon endroit du parcours.

## Lien de politique à brancher après publication

Texte du bouton, déjà présent : « Politique de confidentialité ».

Destination à utiliser après avoir rempli les placeholders et publié la page : [[À COMPLÉTER : URL HTTPS de la politique V1.]]

Blocage : le bouton ouvre aujourd’hui la politique de la vitrine. Changer cette adresse est une modification de `CommercialPhoneCoreScreens.kt`. Elle n’est pas faite ici. La politique User Data n’est pas satisfaite tant que l’application et la Console pointent vers une page qui ne décrit pas les SMS, le journal, les contacts et les catalogues.

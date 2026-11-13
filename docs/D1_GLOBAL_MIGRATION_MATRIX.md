# D.1 global — inventaire et critères de migration Android

État: audit de surface et plan d'exécution. **La refonte complète n'est pas encore implémentée.** La PR #1718 ne modifie que HomeScreen.kt et SentinelVisualEffects.kt. Conserver #1718, #1719 et #1720 séparées jusqu'aux tests d'intégration.

## Périmètre constaté sur main (20/10/2026)
- 26 fichiers Kotlin dans `ui/screens` (incluant des présentations/helpers, donc pas tous des écrans navigables).
- 12 fichiers Kotlin dans `ui/design`, dont `SentinelD1`, `SentinelGlassComponents`, `SentinelHeroVisual`, `SentinelVisualEffects`, `SentinelMotionPolicy`.
- Autres écrans et Activity à inventorier : dialer, InCall, SMS, activation Phone Core, contacts, permissions, premiers lancements, dialogues système. Ne pas confondre fichier Kotlin et écran.

## Règles visuelles globales
1. D.1 doit reposer sur des tokens de thème partagés, non sur des couleurs/copies codées isolément. Identité : fond sombre, anneaux et rayons modérés, surfaces vitrées lisibles, navigation stable.
2. Conserver le Phone Core en priorité, et les actions d'urgence accessibles. Effets graphiques purement décoratifs : jamais bloquants ni interceptant les gestes.
3. États produits READY/LIMITED/LOCKED/UNKNOWN : refléter seulement des preuves runtime, pas la présence d'une UI.
4. Taille de texte et contraste accessibles, cibles tactiles >=48dp quand possible, support de la police agrandie, TalkBack, réduction d'animations, charge CPU/GPU contenue.
5. États : permissions refusées, hors-ligne, chargement, erreurs, états vides. Textes en français et cohérents.
6. Interdire tout changement involontaire d'authentification, rôles téléphonie/SMS, VPN, collecte de données, signature, versionCode, ou stockage utilisateur dans les PR purement visuelles.

## Matrice de couverture (à prouver, non présumée)
- [ ] Accueil Home et navigation de bas d'écran
- [ ] Pavé d'appel / appels entrants et sortants / InCall / historique
- [ ] SMS/MMS composition, conversations, analyse phishing
- [ ] Contacts Sentinel, profils, synchronisation opt-in
- [ ] Blocage numéros, fiches, recherches, listes de protection
- [ ] Assistant premier lancement et Phone Core Activation
- [ ] Network Center Wi-Fi/Bluetooth et VPN
- [ ] E-mail sécurité, exposition numérique, OSINT, défense communautaire
- [ ] System Doctor, audits, permissions, réglages, conformité et aide
- [ ] Fenêtres modales, erreurs, composants réutilisés et orientation paysage

## Ordre de réalisation
**D1-G0** Inventaire des routes NavHost/Activities, comparaison avec cette matrice et tests de contrat.
**D1-G1** Tokens, thèmes et composants partagés (TopBar, bottom navigation, sections, boutons, cartes, statuts).
**D1-G2** Home, Phone Core, dialer, InCall, SMS, contacts.
**D1-G3** Network, VPN, e-mail, sécurité, OSINT, settings.
**D1-G4** Captures S24+ pour chaque écran réel + tests UI/TalkBack/police et émulateur.
**D1-G5** Qualification Gradle et tests locaux Termux au SHA exact, puis tests d'intégration combinés.

## Définition de terminé
Chaque route et Activity recensée doit avoir : test de navigation, état vide/erreur, police agrandie, contraste, capture D.1 réelle, test absence de débordement, vérification d'accessibilité et absence de régression Phone Core. Une compilation verte ne démontre pas cette couverture.

## Inventaire de navigation vérifié dans le code (20/10/2026)
Source : `navigation/Screen.kt` et `navigation/NavGraph.kt` sur `main`.

**23 routes Compose déclarées et câblées dans NavGraph :**
`home`, `search`, `osint_feed`, `security_audit`, `system_doctor`, `local_logs`, `phone_security`, `communications_hub`, `call_blocking`, `phone_lists`, `call_filter_history`, `email_security`, `sms_scanner`, `app_permission_analyzer`, `network_surveillance`, `digital_exposure`, `collective_defense`, `smart_home`, `vpn`, `about`, `compliance`, `settings`, `osint_detail/{itemId}`.

**8 fichiers d'Activity Android à inspecter dans le même module :** `MainActivity`, `CallerIdActivity`, `PhoneCoreActivationActivity`, `PhoneCoreDiagnosticActivity`, `SentinelDialerActivity`, `SentinelInCallActivity`, `SmsComposeActivity`, `VoiceStudioActivity`. La présence d'une classe ne prouve pas son exposition dans le Manifest ni son accessibilité utilisateur : audit manifeste et déclencheurs à suivre.

**Composant partagé examiné** : `ui/design/SentinelChrome.kt` contient `SentinelTopBar`, `SentinelPanel`, `SentinelHero`. Il exploite déjà des couleurs D.1, mais utilise certaines couleurs directes et la rangée de badges de `SentinelHero` ne traite pas encore les petits écrans ni le passage à la ligne. Corriger de manière ciblée puis tester avant d'affirmer couverture globale.

**Contrôle d'acceptation complémentaire** : comparer à chaque migration la liste `Screen` avec les destinations `composable(Screen.*.route)` ; toute nouvelle route exige une entrée dans la matrice et un contrôle visuel sur S24+.

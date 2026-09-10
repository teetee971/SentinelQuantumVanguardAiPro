# Play Store Listing — Sentinel Quantum Vanguard AI Pro

Material to prepare the Play Console store entry. All statements below describe only the code currently present in `native-android-app/`; the positioning deliberately avoids antivirus, VPN, firewall, or active-protection claims, consistent with the app's read-only / informational scope.

## Title (30 characters max)

```text
Sentinel Quantum Vanguard
```

## Short description (80 characters max)

```text
Veille OSINT et informations de sécurité, traitées localement sur l'appareil.
```

## Full description (4000 characters max)

```text
Sentinel Quantum Vanguard AI Pro est une application de veille et d'information en cybersécurité. Elle consulte des sources OSINT publiques et traite les données localement sur votre appareil.

Fonctionnalités :

• Veille OSINT publique (CERT-FR, ANSSI, CVE/NVD) avec cache local hors-ligne, recherche texte, filtre par source et marquage lu/non lu.
• Filtrage d'appels via le rôle système Android Call Screening : blocage local par règles définies par l'utilisateur, sans envoi des numéros vers un serveur (empreintes HMAC locales uniquement).
• Analyse locale d'un e-mail brut : en-têtes, résultat d'authentification observé, domaines et liens — sans accès à votre boîte mail.
• Analyse des permissions des applications installées, présentée à titre informatif.
• Journal de sécurité local, consultable et exportable uniquement par l'utilisateur via le sélecteur de partage Android.

Confidentialité :

• Aucune authentification requise.
• Aucun backend propriétaire et aucune analytique.
• Les consultations de sources publiques se font uniquement en HTTPS et ne transmettent pas de données utilisateur.
• Le manifeste interdit le trafic HTTP en clair et désactive la sauvegarde Android.

Permissions utilisées : INTERNET, ACCESS_NETWORK_STATE, notifications optionnelles, permissions Wi-Fi/Bluetooth nécessaires aux scans locaux, accès optionnel au répertoire pour le Caller ID et rôle système CallScreeningService (attribué explicitement par l'utilisateur). Aucun accès à la boîte SMS, au journal d'appels ou au microphone.

Sentinel est un outil de veille et de consultation. Il ne remplace ni un antivirus, ni un pare-feu, ni un VPN, et ne prend aucune action de protection active sur l'appareil.
```

## Feature bullets (Play Console "key features" style)

1. Veille OSINT publique (CERT-FR, ANSSI, CVE/NVD) avec cache hors-ligne local.
2. Filtrage d'appels local via le rôle Android Call Screening, sans serveur.
3. Analyse locale d'e-mails bruts : en-têtes, authentification, liens.
4. Analyseur informatif des permissions des applications installées.
5. Journal de sécurité local exportable uniquement par l'utilisateur.

## Store settings suggestions

- Category: **Tools** (Outils) or **Productivity** (Productivité).
- Content rating: everyone; the app shows public security feed content and requests no sensitive personal data.
- Target countries: France first (sources and UI are French-first), other countries optional.
- Tags/keywords: veille cybersécurité, OSINT, filtrage d'appels, analyse email, confidentialité locale.

## Screenshots checklist

- [ ] Home screen (feature overview).
- [ ] OSINT feed with offline cache banner and read/unread state.
- [ ] Call blocking screen (local rules).
- [ ] Email analysis screen with a sample raw email.
- [ ] App permission analyzer screen.
- [ ] Local security log screen with export action.

## Privacy policy URL

- [ ] Publish a reachable rendering of `PRIVACY_POLICY.md` (e.g. GitHub Pages or the repository blob URL) and set it in the Play Console before submission.

## Non-goals for this listing

- No antivirus, VPN, firewall, or active-protection claims.
- No claim of certification (RGPD, ANSSI, ISO 27001, SecNumCloud).
- No production-readiness or zero-vulnerability claims.

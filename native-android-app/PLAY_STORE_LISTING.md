# Play Store Listing — Sentinel Quantum Vanguard AI Pro

Material to prepare the Play Console store entry. All statements below must match the exact release submitted to Play. The repository currently contains call screening, an optional Caller Reputation enrichment path, a fail-closed WireGuard client foundation and staged default-SMS primitives. None of these may be advertised as a distributed production service until the corresponding release, infrastructure, consent flow and physical-device validation exist.

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
• Filtrage d'appels via le rôle système Android Call Screening : blocage local par règles définies par l'utilisateur. Un enrichissement Caller Reputation distant peut être activé séparément par l'utilisateur ; il reste facultatif et ne doit jamais ralentir le chemin critique de filtrage.
• Analyse locale d'un e-mail brut : en-têtes, résultat d'authentification observé, domaines et liens — sans accès à votre boîte mail.
• Analyse des permissions des applications installées, présentée à titre informatif.
• Journal de sécurité local, consultable et exportable uniquement par l'utilisateur via le sélecteur de partage Android.

Confidentialité :

• Aucune authentification requise.
• Aucun backend propriétaire n’est requis pour le filtrage local d’appels ; certaines fonctions optionnelles, comme l’enrichissement Caller Reputation, utilisent un service Sentinel distinct après opt-in.
• Aucune analytique comportementale n’est annoncée.
• Les consultations de sources publiques se font uniquement en HTTPS.
• Le manifeste interdit le trafic HTTP en clair et désactive la sauvegarde Android.

Permissions et rôles présents dans le code : INTERNET, ACCESS_NETWORK_STATE, notifications optionnelles, permissions Wi-Fi/Bluetooth nécessaires aux scans locaux, accès optionnel au répertoire pour le Caller ID, rôle système CallScreeningService, ainsi que les permissions SMS nécessaires au client SMS par défaut en préparation. Les permissions SMS restent inutilisables tant que l’utilisateur n’a pas accordé `ROLE_SMS` et que la garde fail-closed n’autorise pas le mode gestionnaire par défaut. Aucun accès au journal d’appels ni au microphone n’est revendiqué.

Sentinel ne remplace pas un antivirus ni un EDR. Le dépôt contient un client WireGuard Android, mais aucun service VPN public ne doit être revendiqué tant qu’aucune passerelle Sentinel n’est provisionnée et validée. Le client SMS par défaut reste lui aussi en préparation et ne doit pas être présenté comme actif avant validation complète.
```

## Feature bullets (Play Console "key features" style)

1. Veille OSINT publique (CERT-FR, ANSSI, CVE/NVD) avec cache hors-ligne local.
2. Filtrage d'appels via le rôle Android Call Screening, avec enrichissement Caller Reputation distant optionnel et séparé ; activation guidée officiellement supportée à partir d’Android 10 (API 29).
3. Analyse locale d'e-mails bruts : en-têtes, authentification, liens.
4. Analyseur informatif des permissions des applications installées.
5. Journal de sécurité local exportable uniquement par l'utilisateur.

## Store settings suggestions

- Category: **Tools** (Outils) or **Productivity** (Productivité).
- Content rating: à déterminer à partir de la version finale soumise et de son questionnaire Play ; ne pas supposer l’absence de données sensibles tant que les rôles SMS/contacts et les flux réseau de la release ne sont pas figés.
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

- No antivirus, firewall or broad active-protection claims. Do not market the VPN or default-SMS mode as operational until their production acceptance gates are met.
- No claim of certification (RGPD, ANSSI, ISO 27001, SecNumCloud).
- No production-readiness or zero-vulnerability claims.

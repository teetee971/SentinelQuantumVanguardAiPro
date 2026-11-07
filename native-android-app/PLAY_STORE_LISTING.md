# Play Store Listing — Sentinel Quantum Vanguard AI Pro

Material to prepare the Play Console store entry. All statements below must match the exact release submitted to Play. The repository currently contains call screening, an optional Caller Reputation enrichment path, a fail-closed WireGuard client foundation and a role-gated default-SMS implementation. None of these may be advertised as a distributed production service until the corresponding release, infrastructure, consent flow and physical-device validation exist.

## Title (30 characters max)

```text
Sentinel Quantum Vanguard
```

## Short description (80 characters max)

```text
Téléphone et SMS/MMS locaux, filtrage d’appels et veille cybersécurité.
```

## Full description (4000 characters max)

```text
Sentinel Quantum Vanguard réunit un téléphone, une messagerie SMS/MMS et des outils de veille en cybersécurité.

Téléphone et messages
• Composez et recevez vos appels dans Sentinel après l’avoir choisi comme application Téléphone par défaut.
• Consultez vos contacts et votre historique sur l’appareil, avec les autorisations Android correspondantes.
• Envoyez et recevez des SMS/MMS après avoir choisi Sentinel comme application SMS par défaut. Les coûts, la disponibilité et les limites de taille dépendent de votre opérateur et de votre SIM. Une image trop volumineuse est refusée avec une explication.
• Appliquez vos règles locales de filtrage d’appels avec le rôle de filtrage Android. Aucun système ne peut garantir le blocage de tous les appels indésirables.

Outils complémentaires
• Veille publique CERT-FR, ANSSI et CVE/NVD avec cache local et recherche.
• Analyse locale d’un e-mail fourni volontairement : en-têtes, domaines et liens, sans connexion à votre boîte mail.
• Diagnostic informatif des permissions et journal local exportable à votre demande.
• Nettoyeur local à partir d’Android 8 : choisissez et confirmez la suppression de copies temporaires Sentinel anciennes. Les conversations, bases et données des autres applications sont exclues.
• Aperçu vocal local : ne modifie pas les appels téléphoniques de votre opérateur.

Vos choix et vos données
Les fonctions locales Téléphone, SMS et filtrage ne nécessitent pas de compte Sentinel. Vous choisissez les rôles et autorisations Android et pouvez les retirer dans les réglages système. L’accès au répertoire et les notifications sont facultatifs selon les fonctions utilisées.

Les consultations de sources publiques utilisent Internet. L’enrichissement distant de réputation téléphonique est facultatif : son activation explicite peut transmettre le numéro à vérifier au service indiqué dans le parcours de consentement. Consultez la politique de confidentialité pour les données et services concernés.

Les MMS passent par les services Android et votre opérateur. L’acceptation de la demande par Android ne garantit pas sa livraison. Les fonctions dépendent des capacités de l’appareil, de la SIM et du réseau.

Sentinel ne garantit pas la détection des logiciels malveillants et ne remplace pas un antivirus. Cette version ne fournit pas de service VPN public ni d’appels VoIP/PSTN transformés.
```


## Permission declaration preparation (not store copy)

- `ROLE_DIALER`: `CALL_PHONE` and `READ_CALL_LOG` only after the default Phone role and runtime consent. Primary use case: user-initiated/default phone and call history.
- `ROLE_SMS`: `SEND_SMS`, `RECEIVE_SMS`, `READ_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` only for the user-selected default messaging handler. Primary use case: SMS/MMS conversations and composition.
- `READ_PHONE_STATE`: selected SIM/subscription, including multi-SIM. No unrestricted identifier access claim.
- `USE_FULL_SCREEN_INTENT`: incoming call UI of the default dialer; eligibility and declaration must be verified in Play Console.
- `RECORD_AUDIO`: explicit local Voice Studio preview, never an appel SIM. The code foundation for microphone transformé during an appel Sentinel VoIP is not an available service in this candidate. Declare only the actual submitted behavior and traffic.
- `READ_CONTACTS`, notifications, Wi-Fi/Bluetooth/location: contextual requests for their corresponding optional features. The merged release manifest and every SDK must be reviewed, not this list alone.

Google approval for restricted permissions is **NOT VERIFIED**. Prominent listing of a feature does not grant permission eligibility by itself.

## Feature bullets (Play Console "key features" style)

1. Téléphone et messagerie SMS/MMS par défaut, après choix explicite des rôles Android.
2. Veille OSINT publique (CERT-FR, ANSSI, CVE/NVD) avec cache hors-ligne local.
3. Filtrage d'appels via le rôle Android Call Screening, avec enrichissement Caller Reputation distant optionnel et séparé ; activation guidée officiellement supportée à partir d’Android 10 (API 29).
4. Analyse locale d'e-mails bruts : en-têtes, authentification, liens.
5. Analyseur informatif des permissions des applications installées.
6. Journal de sécurité local exportable uniquement par l'utilisateur.

## Store settings suggestions

- Category: **Tools** (Outils) or **Productivity** (Productivité).
- Content rating: à déterminer à partir de la version finale soumise et de son questionnaire Play ; ne pas supposer l’absence de données sensibles tant que les rôles SMS/contacts et les flux réseau de la release ne sont pas figés.
- Target countries: France first (sources and UI are French-first), other countries optional.
- Tags/keywords: veille cybersécurité, OSINT, filtrage d'appels, analyse email, confidentialité locale.

## Screenshots checklist

- [ ] Téléphone : clavier, appel entrant/sortant, répertoire, historique (données fictives).
- [ ] Messages : conversations, composition SMS/MMS et statut transport (données fictives).
- [ ] Home screen (feature overview).
- [ ] OSINT feed with offline cache banner and read/unread state.
- [ ] Call blocking screen (local rules).
- [ ] Email analysis screen with a sample raw email.
- [ ] App permission analyzer screen.
- [ ] Local security log screen with export action.

## Privacy policy URL

- [ ] Verify the actual public page `https://sentinelquantumvanguardaipro.pages.dev/public/privacy.html`, its responsible contact, retention and network disclosures. HTTP 403 observed from qualification environment: public availability NOT VERIFIED.
- [ ] Provide the working account/data deletion page in Play Console and `-PsentinelAccountDeletionUrl`. Never substitute a logout or an informational placeholder.

## Non-goals for this listing

- No antivirus, firewall or broad active-protection claims. Do not market a public VPN. This draft describes implemented Phone Core behavior; publish only after the exact submitted artifact passes physical acceptance and restricted-permission review.
- No claim of certification (RGPD, ANSSI, ISO 27001, SecNumCloud).
- No production-readiness or zero-vulnerability claims.

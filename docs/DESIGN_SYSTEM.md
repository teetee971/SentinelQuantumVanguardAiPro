# Sentinel Quantum Vanguard AI Pro — Design System

Status: recovered design baseline for repository review. This document consolidates the visual direction previously validated for Sentinel and the design primitives already present in the current frontend. It does not claim that every page is already conformant.

## 1. Brand position

Sentinel is a defensive cybersecurity product. Its visual identity must communicate control, reliability, technical depth and institutional credibility.

The default experience is:

- dark, sober and high-contrast;
- institutional / cyber-defence rather than consumer or gaming;
- mobile-first and usable on operational screens;
- restrained in motion and decoration;
- free of neon-heavy, cartoon, emoji-led or speculative-AI aesthetics;
- compatible with the static Cloudflare Pages frontend and the native Android application.

Visual effects must never make operational claims. A cinematic treatment may support communication surfaces, but the product UI remains the authoritative visual language.

## 2. Two presentation modes

### Institutional mode — default

This is the product interface and the reference mode for web/PWA and Android.

Characteristics:

- graphite / deep navy background;
- restrained blue accents;
- translucent glass surfaces used selectively;
- clear information hierarchy;
- high readability;
- compact operational layouts;
- visual status indicators with text, never colour alone;
- no decorative animation that interferes with monitoring or decision tasks.

### Cinematic mode — optional communication layer

Reserved for presentation, landing-page hero areas, institutional video or controlled showcase material.

Characteristics:

- realistic cyber-defence / command-centre imagery;
- black, graphite and steel-blue palette;
- slow and restrained motion;
- no cartoon, emoji, neon-cyberpunk or game HUD treatment;
- no fictional capability presented as a live product function.

The cinematic mode must not redefine the core component library.

## 3. Colour system

The historical validated palette and the current implementation are close but not identical. The repository currently uses the following effective tokens in `public/shared-styles.css` and these are the implementation baseline until a deliberate migration is approved.

### Current core tokens

| Role | Token | Value |
|---|---|---|
| Main background | `--bg-main` / `--bg-primary` | `#0b0f14` |
| Secondary background | `--bg-soft` / `--bg-secondary` | `#121826` |
| Panel background | `--bg-panel` / `--bg-tertiary` | `#0f1623` |
| Primary text | `--text-main` / `--text-primary` | `#e8ecf1` |
| Secondary text | `--text-soft` / `--text-secondary` | `#b6c0cc` |
| Muted text | `--text-muted` | `#8b97a8` |
| Primary accent | `--accent` / `--accent-primary` | `#4fa3ff` |
| Secondary accent | `--accent-secondary` | `#5ba3f5` |
| Tertiary accent | `--accent-tertiary` | `#7fb3f0` |
| Success | `--status-success` | `#4caf50` |
| Warning | `--status-warning` | `#ff9800` |
| Error | `--status-error` | `#f44336` |
| Information | `--status-info` | `#2196f3` |

### Historical reference palette

The earlier approved visual direction used approximately:

- background `#0B0F14`;
- surface `#111827`;
- institutional blue `#2563EB`;
- focus / active blue `#0EA5E9`;
- primary text `#E5E7EB`;
- secondary text `#9CA3AF`.

Do not mix both palettes ad hoc page by page. The current CSS tokens remain the implementation source until a single migration PR intentionally reconciles them.

## 4. Liquid Glass treatment

The frontend already implements a restrained Liquid Glass layer.

Reference variables:

- `--glass-bg: rgba(255, 255, 255, 0.08)`;
- `--glass-bg-strong: rgba(255, 255, 255, 0.12)`;
- `--glass-border: rgba(255, 255, 255, 0.18)`;
- `--glass-highlight: rgba(255, 255, 255, 0.28)`;
- `--glass-shadow: 0 10px 40px rgba(0, 0, 0, 0.45)`.

Rules:

- glass is a surface treatment, not the brand itself;
- blur must not reduce text contrast;
- avoid stacking multiple translucent layers without purpose;
- primary actions must remain visually explicit;
- no excessive glow, bloom or neon borders;
- use reflection gradients sparingly;
- provide a solid-enough fallback where backdrop filtering is unavailable.

## 5. Typography

Reference family:

- interface: `Inter` preferred, with `IBM Plex Sans` acceptable for institutional documents or future consolidation;
- technical / identifiers / hashes / logs: `JetBrains Mono` where a monospace face is useful;
- current web fallback chain: `Inter`, `Roboto`, system UI, sans-serif.

Hierarchy:

- headings: medium-to-semibold, never ultra-light;
- body copy: generous line height around 1.6–1.7;
- operational numbers and statuses: stronger weight than explanatory copy;
- long uppercase labels are avoided except for short technical status tags;
- typography must remain readable at 320 px without horizontal scrolling.

## 6. Spacing and radii

Current shared tokens:

- `--spacing-xs: 8px`;
- `--spacing-sm: 12px`;
- `--spacing-md: 20px`;
- `--spacing-lg: 32px`;
- `--spacing-xl: 48px`;
- `--radius-sm: 8px`;
- `--radius-md: 14px`;
- `--radius-lg: 16px`.

Use these tokens before introducing local values. Page-specific spacing is allowed only where information density requires it.

## 7. Navigation

The shared fixed navigation is part of the product identity.

Requirements:

- persistent dark glass bar;
- brand left, primary destinations right on large screens;
- mobile toggle at narrow widths;
- current destination visible visually and exposed accessibly;
- keyboard Escape closes the mobile menu and returns focus;
- mobile menu targets should meet a 44 px touch target where applicable;
- skip navigation must remain available for keyboard users;
- navigation labels are concise and operational.

The brand mark can use the existing geometric hex/diamond motif, but iconography must remain consistent and not devolve into emoji.

## 8. Surfaces and cards

Reference components:

- `.glass`;
- `.card`;
- `.module`;
- `.panel`;
- `.box`.

Visual rules:

- low-opacity glass background;
- 1 px translucent border;
- 14–16 px radius for primary cards;
- soft dark shadow;
- reflection overlay only where it does not impair content;
- card titles clearly separated from metrics and supporting text;
- avoid decorative cards that carry no informational value.

Operational dashboards should privilege scanability over visual novelty.

## 9. Buttons and actions

Primary action:

- institutional blue fill;
- strong text contrast;
- clear hover and focus state;
- no animated glow;
- minimum touch target around 44 px on mobile.

Secondary action:

- transparent or glass surface;
- visible border;
- same focus quality as primary action.

Destructive or security-sensitive action:

- explicit label;
- error / warning colour used with text and iconography;
- no ambiguity between simulate, authorize and execute states.

## 10. Status system

Colours are semantic, not decorative.

- green: successful / healthy state;
- orange: warning / attention;
- red: error / blocked / critical;
- blue: informational / active / selected.

Every critical status must include text. Never encode security or operational state by colour alone.

## 11. Dashboard and operational UI

Desired information hierarchy:

1. global system status;
2. current risk / threat posture;
3. active observations and evidence;
4. decision / simulation state;
5. actions and authorizations;
6. audit trail / provenance.

Dashboards should use:

- compact metric cards;
- restrained data visualisation;
- clear timestamps;
- explicit data source / evidence state where relevant;
- technical details progressively disclosed rather than always expanded.

Do not fabricate live telemetry for visual effect.

## 12. Responsive baseline

The design is mobile-first.

Required review widths:

- 320 px;
- 375 px;
- 768 px;
- 1024 px;
- 1440 px.

Rules:

- no hidden overflow used to mask layout bugs;
- tables that cannot collapse must become horizontally scrollable in an explicit container;
- cards must not enforce a minimum width wider than the viewport;
- fixed navigation must not obscure anchors or page headings;
- images use intrinsic sizing and never force horizontal overflow;
- important controls remain reachable by touch and keyboard.

A static CSS inspection is not a substitute for browser validation.

## 13. Accessibility baseline

Required product behaviour:

- `html lang` present and correct;
- visible `:focus-visible` treatment;
- skip link;
- semantic landmarks;
- one meaningful page-level `h1`;
- coherent heading hierarchy;
- accessible names for links and buttons;
- appropriate `alt` text;
- `aria-expanded` and `aria-controls` for the mobile menu where required;
- `prefers-reduced-motion` respected;
- tables use captions / headers / scopes when needed;
- dynamic status uses live semantics only when genuinely dynamic.

Do not claim WCAG AA without a complete reproducible audit.

## 14. Imagery and iconography

Product imagery:

- realistic;
- institutional;
- cyber-defence / infrastructure / operations oriented;
- no stock-photo clichés where a functional diagram is clearer;
- no fictional AI avatar as the product identity;
- no aggressive offensive-hacking imagery as the default brand signal.

Icons:

- geometric, simple, monochrome or accent-coloured;
- consistent stroke / fill family;
- used to reinforce labels, not replace them for critical actions.

## 15. Motion

Default product motion is restrained.

Allowed:

- short hover / focus transitions;
- menu opening / closing;
- subtle state transitions;
- optional cinematic media outside the core operational path.

Avoid:

- continuous decorative animation;
- parallax that affects readability;
- pulsing critical controls;
- motion that implies live activity when no live data exists.

Respect `prefers-reduced-motion`.

## 16. Web/PWA and Android consistency

Web/PWA and Android should share:

- colour roles;
- typography hierarchy;
- status semantics;
- spacing logic;
- component tone;
- icon language;
- security-state vocabulary.

They do not need pixel-identical components. Native Android should follow platform behaviour where it improves usability or accessibility.

## 17. Public image / brand guardrails

Sentinel must not visually imply guarantees the repository or runtime does not prove.

Avoid public visual claims such as:

- “military grade”;
- “government certified”;
- “fully autonomous defence”;
- “zero vulnerabilities”;
- “real-time protection” unless backed by a real deployed mechanism;
- certification badges that are not earned.

The phrases `Government Grade`, `FINAL` or `Production` in historical CSS comments are not evidence of actual certification or release readiness and should not be treated as public claims.

## 18. Current implementation anchor

The current shared visual implementation is concentrated in:

- `public/shared-styles.css`;
- `public/shared-navigation.js`;
- `index.html`;
- page-level CSS and HTML under `public/`;
- native Android UI under `native-android-app/`.

`public/shared-styles.css` already contains the dark background system, Liquid Glass variables, typography defaults, fixed navigation, spacing, radii, status colours and common surfaces. Any visual refactor should begin by comparing page-level overrides against this file.

## 19. Design drift audit checklist

Before changing the visual system, inventory every page and classify deviations from the shared baseline:

- colour token bypass;
- local hard-coded background / accent colours;
- duplicated navigation styles;
- non-standard radii / shadows;
- inconsistent button styles;
- inconsistent heading scale;
- touch targets below baseline;
- mobile overflow;
- inaccessible focus states;
- inline styles that should become shared tokens;
- inconsistent icon style;
- cinematic effects leaking into operational screens.

A deviation is not automatically a defect. Correct it only when it creates inconsistency, accessibility risk, maintenance cost or brand drift.

## 20. Design source-of-truth rule

After this document is reviewed and merged, it becomes the written design baseline. `public/shared-styles.css` remains the current implementation reference.

Future visual changes should state explicitly whether they:

- conform to the baseline;
- intentionally evolve the baseline;
- or are page-specific exceptions.

This prevents the visual identity from being silently lost during security, CI or release work.

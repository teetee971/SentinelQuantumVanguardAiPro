# Sentinel D.1 Phase 4 — Visual Fidelity Design

## Outcome

Rebuild the Android-native D.1 surfaces so the 13 preserved official references are expressed by
scene-specific, data-driven holographic compositions rather than a generic wireframe hero. The
implementation keeps all existing Android contracts and exposes missing evidence as `UNKNOWN`,
`LIMITED`, `LOCKED`, or `TEST`.

## Boundaries

- Source branch: `feat/sentinel-d1-phase3-official-visuals` at `0242647fa6a1f2c2651eec2dbcc562388638613c`.
- Delivery branch: `feat/sentinel-d1-phase4-visual-fidelity`.
- Visual source: the 13 original files in `docs/design/sentinel-d1/reference-source/visuels/`.
- Target: native Jetpack Compose Android app; no PWA, reference image, or fabricated runtime data.
- Primary layout: portrait Samsung Galaxy S24+ / Android 16; no device validation is claimed here.

## Design decisions

### Rendering architecture

`SentinelHolographicScene` remains the single Compose entry point and receives a truthful
`SentinelHolographicSceneModel`. Its renderer is upgraded with bounded Canvas layers: volumetric
materials, extruded silhouettes, depth offsets, radial lighting, perspective grids, specular
highlights, orbit particles, and scene-specific geometry. It stays 2.5D/Canvas instead of adding a
heavy 3D engine; this preserves accessibility, reduced-motion behavior, and battery control while
making depth perceptible.

Each `SentinelSceneKind` owns a different composition:

- command/protection: layered sphere, shield extrusion, rim light, scan arc, proof nodes;
- VPN/threat map: globe mesh, latitude/longitude depth, route arcs and evidence points;
- device/report: exploded phone frame, screen, boards and depth-separated layers;
- risk applications: translucent cube faces and orbiting app/permission cards;
- quarantine: filled containment cylinder, caps, beams and contained evidence particles;
- threat detail: red volumetric core, hot center, warning geometry and critical proof ring;
- contact/number/messages: portrait or unknown silhouette inside an illuminated identity orb.

No geometry may create a security claim. Status colors and labels continue to come only from the
model. `MotionLevel.REDUCED` and `STATIC` freeze dynamic phases; the Canvas has a TalkBack summary.

### Layout architecture

Evidence metrics use an adaptive two-column grid rather than four narrow columns. Text remains
soft-wrapped with no one-line truncation in required evidence tiles. The hero is bounded by its
screen context. Network Center receives a topology-specific scene. Phone Core keeps the dial pad
and call action above decorative content. Activation steps use wrapped labels and explicit state
rows. All fixes preserve existing click handlers, role/permission gates, and provider pipelines.

### Truth and failure states

Reference-only counters, locations, threat counts, scores, and connections are never copied into
runtime UI. If an adapter has no proof, the screen keeps the visual composition but displays the
corresponding truthful state and evidence source. An unavailable runtime action is visibly disabled
or routes to the existing Android settings/error path.

## Verification gates

1. Reference gate: 13 originals, hashes, mapping, and this matrix remain in the repository.
2. Source contract gate: scene-specific depth/material primitives and adaptive layout are tested.
3. Android gate: unit tests, Android-test Kotlin compilation, lint, and debug APK build pass.
4. Artifact gate: the APK hash and signing certificate are recorded from the final commit.
5. Visual/device gate: blocked until a real Android runtime is available; no synthetic capture or
   FPS/device claim is permitted.

## Explicit non-goals

- Do not add OpenGL or a third-party 3D engine without a measured need.
- Do not invent a global threat feed, VPN handshake, antivirus score, quarantine, identity, or
  contact social profile.
- Do not remove or weaken `ROLE_DIALER`, `ROLE_SMS`, Telecom, SMS/MMS, VPN, identity, reputation,
  Wear OS, or provenance contracts.

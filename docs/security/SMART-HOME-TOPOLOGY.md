# Sentinel Smart Home — topology and integrations

Status: foundation only. This document does not claim that vendor devices are controllable until a real device and an authorized interface have passed acceptance tests.

## Initial integrations

- Philips Hue: prefer the supported local Hue API/bridge and Matter where the user's hardware supports it.
- LSC Smart Connect: prefer Matter for compatible devices. A Tuya/LSC cloud connector must only be enabled with documented user authorization and supported credentials.
- Imou: cameras and related devices may be represented in topology, but proprietary control is disabled until an official, licensed interface for the exact device path is verified.
- Generic Matter: local commissioning and control with explicit Android/user consent.
- Generic Bluetooth companion devices: pairing remains Android-managed.

## Topology model

Sentinel records explicit observed or user-confirmed relationships instead of guessing them:

Phone
├── Wi-Fi access point / router
│   ├── Ethernet hub/bridge
│   │   └── bridge-managed child device
│   ├── Wi-Fi IoT device
│   └── Matter-over-Wi-Fi device
├── Bluetooth/BLE companion
└── Matter/Thread border-router relationship when verifiable

Each node carries a transport and optional parent/relation. A relationship is marked verified only when Android, a local protocol, a supported bridge/API, or the user supplies evidence. MAC proximity alone must never be represented as proof that one device is connected through another.

## Security boundaries

- No credential guessing, default-password testing, camera-stream probing, or unauthorized LAN exploitation.
- No automatic control of cameras, locks, alarms, or other sensitive actuators merely because they were discovered.
- Secrets remain in Android secure storage where applicable.
- Discovery is bounded and local; cloud integrations require explicit opt-in.
- Device controls must fail closed when identity, authorization, or topology binding is ambiguous.

## Planned UI

“Appareils & Maison” should show:
- device name/type/brand;
- connection transport;
- “connected via” parent when verified;
- online/offline/unknown;
- trust and security posture;
- safe actions appropriate to the device type;
- a topology view grouping devices by router, bridge, hub, Matter fabric, or Bluetooth companion relationship.

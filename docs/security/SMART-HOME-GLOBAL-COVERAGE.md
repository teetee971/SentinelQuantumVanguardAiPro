# Sentinel Smart Home — global ecosystem coverage

Status: integration catalogue and topology foundation. A listed ecosystem is not a claim that every model is controllable.

## Protocol-first coverage

Sentinel models Wi-Fi, Ethernet, Bluetooth LE, Matter over Wi-Fi, Matter over Thread, Thread, Zigbee via a bridge, Z-Wave via a bridge, and explicitly authorized cloud connectors.

## Ecosystems represented

Matter, Philips Hue, Imou, LSC Smart Connect, Tuya-derived devices where an authorized path exists, TP-Link Tapo/Kasa, Samsung SmartThings, Google Home, Amazon Alexa/Echo, Apple Home, Home Assistant, IKEA Home smart, and Xiaomi/Aqara.

## Device families

Cameras, doorbells, lights, plugs, switches, sensors, thermostats, locks, TVs, speakers, watches/wearables, headphones, hubs, routers/mesh nodes, NAS, printers, consoles, appliances, robot vacuums, blinds, smoke/CO alarms, water devices, energy meters, EV chargers, phones, tablets and computers.

## Evidence states

- DETECTED: observed, but identity/relationship is not established.
- IDENTIFIED: device identity/type has adequate evidence.
- CONNECTED: a current connection relationship has adequate evidence.
- CONTROLLABLE: an authorized control interface is available.
- VERIFIED: identity and topology binding have passed the relevant verification.

These states are intentionally distinct. Discovery never implies control.

## Topology

Relationships are represented as explicit parent/child edges, such as router -> Wi-Fi device, Hue bridge -> lamp, Matter controller/border-router -> Matter/Thread device, or Android phone -> BLE companion. Cycles, self-links and unknown parents are rejected by the model.

No relationship may be inferred solely from radio proximity, a vendor OUI, or an IP address.

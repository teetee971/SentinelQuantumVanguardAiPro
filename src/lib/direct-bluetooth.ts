export const SENTINEL_RADIO_SERVICE = "6f4c2a10-9b1e-4d77-a3c5-2e8b7f01d6a4";

export type BluetoothReadiness = "ready" | "missing" | "off" | "insecure";

type RadioDevice = {
  name?: string;
  gatt?: {
    connect: () => Promise<{ getPrimaryService: (uuid: string) => Promise<unknown> }>;
    disconnect: () => void;
  };
};

type BluetoothApi = {
  getAvailability?: () => Promise<boolean>;
  requestDevice: (options: { acceptAllDevices: true; optionalServices: string[] }) => Promise<RadioDevice>;
};

function bluetoothApi(nav: Navigator): BluetoothApi | undefined {
  return (nav as Navigator & { bluetooth?: BluetoothApi }).bluetooth;
}

export async function bluetoothReadiness(nav: Navigator, secure: boolean): Promise<BluetoothReadiness> {
  const bluetooth = bluetoothApi(nav);
  if (!secure) return "insecure";
  if (!bluetooth) return "missing";
  if (!bluetooth.getAvailability) return "ready";
  return (await bluetooth.getAvailability()) ? "ready" : "off";
}

export function bluetoothFailureMessage(error: unknown): string {
  const name = error && typeof error === "object" && "name" in error ? String((error as { name: unknown }).name) : "";
  if (name === "ServiceMissing") return "Cet appareil ne parle pas Sentinel.";
  if (name === "NotFoundError") return "Aucun téléphone Sentinel à proximité.";
  if (name === "NotAllowedError") return "Recherche annulée.";
  if (name === "SecurityError") return "Cet aperçu n’a pas le droit d’ouvrir Bluetooth.";
  if (name === "NotSupportedError") return "Ce navigateur ne fait pas de lien direct entre deux téléphones.";
  if (name === "NetworkError") return "La liaison Bluetooth a été coupée.";
  return "Liaison Bluetooth impossible.";
}

export async function connectSentinelRadio(nav: Navigator): Promise<string> {
  const bluetooth = bluetoothApi(nav);
  if (!bluetooth) throw Object.assign(new Error("missing"), { name: "NotSupportedError" });
  const device = await bluetooth.requestDevice({
    acceptAllDevices: true,
    optionalServices: [SENTINEL_RADIO_SERVICE],
  });
  const server = await device.gatt?.connect();
  if (!server) throw Object.assign(new Error("gatt"), { name: "NetworkError" });
  try {
    await server.getPrimaryService(SENTINEL_RADIO_SERVICE);
  } catch {
    device.gatt?.disconnect();
    throw Object.assign(new Error("service"), { name: "ServiceMissing" });
  }
  const name = device.name?.trim();
  return name ? name : "Téléphone Sentinel";
}

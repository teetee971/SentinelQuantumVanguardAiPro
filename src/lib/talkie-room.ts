export const TALKIE_ROOMS = {
  "Équipe terrain": "talkie-148717ef668860f6f103893ed08270fa",
  Atelier: "talkie-56d2bb8adf06b84becd1cb4e42592583",
  Veille: "talkie-7672aa5acf979cab01f86d30e6824bb1",
} as const;

export async function talkieRoomId(channel: string): Promise<string | null> {
  const name = channel.trim();
  if (!name || name.length > 80) return null;
  const bytes = new TextEncoder().encode(`sentinel-talkie:v1:${name}`);
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
  const suffix = [...digest.slice(0, 16)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
  return `talkie-${suffix}`;
}

export function roomClosedMessage(roomId: string | null): string {
  return roomId ? `Salle ${roomId} non ouverte. Aucun jeton.` : "Canal refusé. Aucune salle.";
}

export async function checkTalkieRooms(): Promise<{ label: string; ok: boolean }[]> {
  const checks = await Promise.all(
    Object.entries(TALKIE_ROOMS).map(async ([channel, expected]) => {
      const roomId = await talkieRoomId(channel);
      return { label: `${channel} → ${roomId ?? "refusée"}`, ok: roomId === expected };
    }),
  );
  const blank = await talkieRoomId("   ");
  return [...checks, { label: "Canal vide refusé", ok: blank === null }];
}

export async function checkTalkie(input: {
  linkMode: "network" | "bluetooth" | "simulation";
  linked: boolean;
  micOpen: boolean;
  encoderReady: boolean;
}): Promise<{ label: string; ok: boolean }[]> {
  const rooms = await checkTalkieRooms();
  const micOk = input.linkMode === "simulation" ? input.micOpen : input.linked ? input.micOpen : !input.micOpen;
  const micLabel =
    input.linkMode === "simulation"
      ? "Simulation locale"
      : input.linked
        ? "Micro ouvert pour le téléphone relié"
        : "Micro fermé sans lien";
  return [
    ...rooms,
    { label: input.encoderReady ? "Encodeur Opus prêt" : "Encodeur Opus absent", ok: true },
    { label: micLabel, ok: micOk },
    { label: "Salle web non ouverte", ok: true },
  ];
}

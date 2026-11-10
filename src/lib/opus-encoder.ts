export const OPUS_CONFIG = {
  codec: "opus",
  sampleRate: 48_000,
  numberOfChannels: 1,
  bitrate: 16_000,
} as const;

export const OPUS_FRAME_SAMPLES = 960;

type EncodedChunk = { byteLength: number; copyTo: (target: Uint8Array) => void };

type OpusAudioEncoder = {
  configure: (config: typeof OPUS_CONFIG) => void;
  encode: (data: unknown) => void;
  close: () => void;
};

type AudioEncoderCtor = {
  isConfigSupported?: (config: typeof OPUS_CONFIG) => Promise<{ supported?: boolean }>;
  new (init: { output: (chunk: EncodedChunk) => void; error: () => void }): OpusAudioEncoder;
};

export type OpusEncoder = {
  encodeFrame: (pcm: Int16Array) => Promise<Uint8Array | null>;
  close: () => void;
};

function constructorFrom(scope: Window): AudioEncoderCtor | undefined {
  return (scope as Window & { AudioEncoder?: AudioEncoderCtor }).AudioEncoder;
}

export async function openOpusEncoder(scope: Window = window): Promise<OpusEncoder | null> {
  const Encoder = constructorFrom(scope);
  if (!Encoder?.isConfigSupported) return null;
  let support: { supported?: boolean };
  try {
    support = await Encoder.isConfigSupported(OPUS_CONFIG);
  } catch {
    return null;
  }
  if (!support.supported) return null;
  let failed = false;
  let pending: ((packet: Uint8Array | null) => void) | null = null;
  const encoder = new Encoder({
    output: (chunk) => {
      const packet = new Uint8Array(chunk.byteLength);
      chunk.copyTo(packet);
      const accepted = packet.byteLength > 0 && packet.byteLength <= 160 ? packet : null;
      pending?.(accepted);
      pending = null;
    },
    error: () => {
      failed = true;
      pending?.(null);
      pending = null;
    },
  });
  try {
    encoder.configure(OPUS_CONFIG);
  } catch {
    encoder.close();
    return null;
  }
  if (failed) {
    encoder.close();
    return null;
  }
  return {
    encodeFrame: (pcm) =>
      new Promise((resolve) => {
        if (pcm.length !== OPUS_FRAME_SAMPLES || pending) {
          resolve(null);
          return;
        }
        const AudioDataCtor = (scope as Window & { AudioData?: new (init: object) => { close?: () => void } }).AudioData;
        if (!AudioDataCtor) {
          resolve(null);
          return;
        }
        pending = resolve;
        const data = new AudioDataCtor({
          format: "s16",
          sampleRate: OPUS_CONFIG.sampleRate,
          numberOfFrames: pcm.length,
          numberOfChannels: 1,
          timestamp: 0,
          data: pcm,
        });
        try {
          encoder.encode(data);
          data.close?.();
        } catch {
          pending = null;
          resolve(null);
        }
      }),
    close: () => encoder.close(),
  };
}

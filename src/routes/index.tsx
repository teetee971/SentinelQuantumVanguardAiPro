import { createFileRoute } from '@tanstack/react-router'
import { SentinelShell } from "@/components/sentinel-shell";
import { bluetoothFailureMessage, bluetoothReadiness, connectSentinelRadio } from "@/lib/direct-bluetooth";
import { ChevronLeft, MessageSquare, Mic, Radio, Settings, Shield, UserRound, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { openOpusEncoder } from "@/lib/opus-encoder";
import { checkTalkie, roomClosedMessage, talkieRoomId } from "@/lib/talkie-room";

export const Route = createFileRoute("/")({ component: App });

function App() {
  return <SentinelShell talkie={<TalkieScreen />} />;
}

const CHANNELS = ["Équipe terrain", "Atelier", "Veille"] as const;

const PRESENCE = [
  { id: "available", label: "Disponible", hint: "Vous pouvez parler sur le canal." },
  { id: "solo", label: "Solo", hint: "Vous restez là, sans émettre." },
  { id: "busy", label: "Occupé", hint: "Le micro reste fermé." },
  { id: "offline", label: "Hors ligne", hint: "Vous n’émettez pas et vous ne recevez pas." },
] as const;

type Presence = (typeof PRESENCE)[number]["id"];
type Phase = "closed" | "ready" | "live";
type SettingsPage = "menu" | "status" | "audio" | "button" | "behavior" | "privacy" | "link" | "test";
type LinkMode = "network" | "bluetooth" | "simulation";

function TalkieScreen() {
  const [channelIndex, setChannelIndex] = useState(0);
  const [presence, setPresence] = useState<Presence>("available");
  const [phase, setPhase] = useState<Phase>("ready");
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [settingsPage, setSettingsPage] = useState<SettingsPage>("menu");
  const [beepOn, setBeepOn] = useState(true);
  const [linkMode, setLinkMode] = useState<LinkMode>("network");
  const [peerName, setPeerName] = useState<string | null>(null);
  const [linkMessage, setLinkMessage] = useState("Aucun téléphone relié.");
  const [opusReady, setOpusReady] = useState(false);
  const [messageOpen, setMessageOpen] = useState(false);
  const [draft, setDraft] = useState("");
  const [messageNote, setMessageNote] = useState("");
  const [simNote, setSimNote] = useState("");
  const [roomNote, setRoomNote] = useState("");
  const hold = useRef(false);
  const encoderRef = useRef<{ encodeFrame: (pcm: Int16Array) => Promise<Uint8Array | null>; close: () => void } | null>(null);

  useEffect(() => {
    let encoder: { close: () => void } | null = null;
    let cancel = false;
    void openOpusEncoder().then((opened) => {
      if (cancel) {
        opened?.close();
        return;
      }
      encoder = opened;
      encoderRef.current = opened;
      setOpusReady(opened !== null);
    });
    return () => {
      cancel = true;
      encoderRef.current = null;
      encoder?.close();
    };
  }, []);

  const channel = CHANNELS[channelIndex];
  const bluetoothOpen = linkMode === "bluetooth" && Boolean(peerName) && presence !== "busy" && presence !== "solo";
  const simulationOpen = linkMode === "simulation" && presence === "available";
  const networkOpen = false;
  const canTalk = phase !== "closed" && (bluetoothOpen || networkOpen || simulationOpen);

  function stopTalk() {
    hold.current = false;
    setPhase((current) => (current === "live" ? "ready" : current));
  }

  function chooseLink(next: LinkMode) {
    stopTalk();
    setLinkMode(next);
    setPeerName(null);
    setSimNote("");
    if (next === "simulation") {
      setLinkMessage("Simulation sur ce téléphone. Personne d’autre n’écoute.");
      return;
    }
    setLinkMessage("Aucun téléphone relié.");
  }

  async function searchBluetooth() {
    setLinkMessage("Recherche à proximité…");
    try {
      const readiness = await bluetoothReadiness(navigator, window.isSecureContext);
      if (readiness === "missing") {
        setPeerName(null);
        setLinkMessage("Ce navigateur n’ouvre pas Bluetooth.");
        return;
      }
      if (readiness === "off") {
        setPeerName(null);
        setLinkMessage("Bluetooth est éteint.");
        return;
      }
      if (readiness === "insecure") {
        setPeerName(null);
        setLinkMessage("Bluetooth direct exige une page sécurisée.");
        return;
      }
      const name = await connectSentinelRadio(navigator);
      setPeerName(name);
      setLinkMessage(`Relié à ${name}. Sans réseau.`);
    } catch (error) {
      setPeerName(null);
      setLinkMessage(bluetoothFailureMessage(error));
    }
  }

  async function joinRoom() {
    stopTalk();
    setLinkMode("network");
    setPeerName(null);
    setSimNote("");
    setLinkMessage("Aucun téléphone relié.");
    const roomId = await talkieRoomId(channel);
    setRoomNote(roomClosedMessage(roomId));
  }

  function choosePresence(next: Presence) {
    stopTalk();
    setPresence(next);
  }

  function sendMessage() {
    const text = draft.trim();
    if (!text) {
      setMessageNote("Écrivez un message avant de l’envoyer.");
      return;
    }
    if (linkMode === "simulation") {
      setMessageNote("Affiché ici seulement. Rien n’est parti.");
      return;
    }
    if (!peerName) {
      setMessageNote("Message non envoyé. Aucun lien.");
      return;
    }
    setMessageNote("Message non envoyé. Ce lien ne transporte pas de texte.");
  }

  function beginTalk() {
    if (!canTalk || settingsOpen || messageOpen) return;
    hold.current = true;
    setPhase("live");
    if (beepOn) playReadyBeep();
  }

  function endTalk() {
    if (!hold.current) return;
    hold.current = false;
    const simulating = linkMode === "simulation";
    setPhase((current) => (current === "closed" ? current : "ready"));
    if (!simulating) return;
    window.setTimeout(() => playEchoBeep(), 350);
    const encoder = encoderRef.current;
    if (!encoder) {
      setSimNote("Écho local. Rien n’est parti.");
      return;
    }
    void encoder.encodeFrame(new Int16Array(960)).then((packet) => {
      setSimNote(packet ? `Écho local. Trame Opus de ${packet.length} octets. Rien n’est parti.` : "Écho local. Rien n’est parti.");
    });
  }

  const headline =
    phase === "closed"
      ? "Session arrêtée"
      : phase === "live"
        ? "Relâchez pour couper"
        : linkMode === "bluetooth" && !peerName
          ? "Reliez un téléphone"
          : presence === "solo"
            ? "Écoute seulement"
            : presence === "busy"
              ? "Occupé"
              : presence === "offline"
                ? "Hors ligne"
          : linkMode === "simulation" && presence === "available"
            ? "Maintenez pour simuler"
            : linkMode === "network"
                  ? "Aucun lien web"
                  : "Maintenez pour parler";

  const detail =
    phase === "closed"
      ? "Démarrer rouvre le canal. Aucune voix n’est envoyée."
      : phase === "live"
        ? linkMode === "simulation"
          ? "Simulation. Personne n’écoute."
          : peerName
          ? "Un seul parleur à la fois. Écran éteint : la session s’arrête."
          : "Le micro est ouvert ici. Aucune voix n’est envoyée."
        : linkMode === "bluetooth" && !peerName
          ? "Sans réseau. Les deux téléphones doivent être proches. Aucune voix n’est envoyée."
          : linkMode === "bluetooth" && peerName
            ? `Maintenez pour parler à ${peerName}. Sans réseau.`
          : linkMode === "simulation"
            ? "Le son reste dans ce téléphone."
            : presence === "solo"
              ? "Vous restez sur le canal, sans émettre."
              : presence === "busy"
                ? "Le micro reste fermé."
                : presence === "offline"
                  ? "Vous n’émettez pas et vous ne recevez pas."
                  : "Aucune voix n’est envoyée.";

  const mark =
    phase === "closed" ? "Arrêté" : phase === "live" ? (linkMode === "simulation" ? "Simulation" : peerName ? "Émission" : "Micro ouvert") : canTalk ? (linkMode === "simulation" ? "Simulation" : "Prêt") : presence === "available" ? "Sans lien" : "Coupé";
  const presenceTone =
    presence === "available" ? "text-ok" : presence === "solo" ? "text-accent" : "text-muted";
  const dotTone = presence === "available" ? "bg-ok" : presence === "solo" ? "bg-accent" : "bg-muted";
  const presenceLabel = PRESENCE.find((item) => item.id === presence)?.label ?? "Disponible";

  return (
    <main className="mx-auto flex h-full min-h-0 w-full max-w-md flex-col overflow-hidden bg-bg px-5 pb-4 pt-4 text-fg">
      <header className="flex items-center justify-between">
        <p className="text-lg font-semibold text-accent">Sentinel</p>
        <div className="flex items-center gap-2">
          <button
            type="button"
            aria-expanded={settingsOpen}
            className="flex min-h-11 items-center gap-2 rounded-full border border-line px-3 text-sm font-medium text-accent"
            onClick={() => {
              stopTalk();
              setSettingsPage("menu");
              setMessageOpen(false);
              setSettingsOpen((open) => !open);
            }}
          >
            <Settings className="size-5" />
            Configuration
          </button>
          <Shield className="size-6 text-accent" aria-hidden="true" />
        </div>
      </header>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight">Talkie-walkie</h1>

      <section className="mt-4 rounded-card border border-line bg-surface px-4 py-3">
        <h2 className="text-lg font-medium">{channel}</h2>
        <div className="mt-3 flex items-center justify-between gap-3">
          <p className="text-sm font-medium text-muted">{linkMode === "simulation" ? "Simulation locale" : peerName ? `Relié à ${peerName}` : "Déconnecté"}</p>
          <div className="flex flex-col items-end gap-1 text-sm font-medium">
            <p className={`flex items-center gap-2 ${linkMode === "bluetooth" && peerName ? "text-accent" : "text-muted"}`}>
              <span className={`size-2 rounded-full ${linkMode === "bluetooth" && peerName ? "bg-accent" : "bg-muted"}`} />
              {linkMode === "simulation" ? "Simulation" : linkMode === "bluetooth" ? (peerName ? "Bluetooth relié" : "Bluetooth non relié") : "Web non relié"}
            </p>
            <p className={`flex items-center gap-2 ${presenceTone}`}>
              <span className={`size-2 rounded-full ${dotTone}`} />
              {presenceLabel}
            </p>
          </div>
        </div>
      </section>

      {settingsOpen ? (
        <SettingsPanel
          page={settingsPage}
          presence={presence}
          beepOn={beepOn}
          linkMode={linkMode}
          linkMessage={linkMessage}
          opusReady={opusReady}
          linked={Boolean(peerName)}
          micOpen={canTalk}
          onPage={setSettingsPage}
          onPresence={choosePresence}
          onBeep={() => setBeepOn((value) => !value)}
          onLink={chooseLink}
          onSearch={() => void searchBluetooth()}
        />
      ) : messageOpen ? (
        <section className="mt-4 flex min-h-0 flex-1 flex-col rounded-card border border-line bg-surface px-4 py-4">
          <h2 className="text-lg font-medium">Message</h2>
          <button type="button" className="mt-2 flex min-h-11 items-center gap-2 text-sm font-medium text-accent" onClick={() => setMessageOpen(false)}>
            <ChevronLeft className="size-5" />
            Retour
          </button>
          <textarea
            value={draft}
            maxLength={280}
            placeholder="Votre message"
            className="mt-3 min-h-24 w-full rounded-card border border-line bg-bg px-3 py-3 text-base"
            onChange={(event) => setDraft(event.target.value)}
          />
          <button
            type="button"
            className="mt-3 min-h-11 rounded-card bg-accent font-semibold text-bg disabled:opacity-40"
            disabled={!peerName && linkMode !== "simulation"}
            onClick={sendMessage}
          >
            Envoyer
          </button>
          <p className="mt-3 text-sm leading-relaxed text-muted">
            {messageNote || (linkMode === "simulation" ? "Le texte reste sur cet écran." : peerName ? "Le texte part seulement si le lien le transporte." : "Aucun lien. Le message ne part pas.")}
          </p>
          {!peerName && linkMode !== "simulation" ? (
            <button
              type="button"
              className="mt-3 min-h-11 rounded-card border border-line px-4 font-medium text-accent"
              onClick={() => {
                chooseLink("bluetooth");
                setSettingsPage("link");
                setMessageOpen(false);
                setSettingsOpen(true);
              }}
            >
              Relier en Bluetooth
            </button>
          ) : null}
        </section>
      ) : (
        <div className="flex min-h-0 flex-1 flex-col items-center justify-center py-4 text-center">
          <p className="mb-3 text-sm font-medium text-accent">{mark}</p>
          <button
            type="button"
            aria-pressed={phase === "live"}
            disabled={!canTalk}
            onPointerDown={beginTalk}
            onPointerUp={endTalk}
            onPointerCancel={endTalk}
            onPointerLeave={endTalk}
            className={`flex size-36 items-center justify-center rounded-full border-2 bg-bg transition-colors duration-200 disabled:opacity-40 ${
              phase === "live" ? "border-accent text-accent" : "border-accent/70 text-fg"
            }`}
          >
            <Mic className="size-12" />
          </button>
          <p className="mt-5 text-2xl font-semibold">{headline}</p>
          <p className="mt-2 max-w-xs text-base leading-relaxed text-muted">{roomNote || simNote || detail}</p>
          {phase !== "closed" ? (
            <button
              type="button"
              className="mt-4 min-h-11 rounded-card border border-line px-4 font-medium text-accent"
              onClick={() => void joinRoom()}
            >
              Rejoindre la salle
            </button>
          ) : null}
          {linkMode !== "simulation" && !peerName && phase !== "closed" ? (
            <button
              type="button"
              className="mt-4 min-h-11 rounded-card border border-line px-4 font-medium text-accent"
              onClick={() => chooseLink("simulation")}
            >
              Simuler ici
            </button>
          ) : null}
          {!peerName && linkMode !== "simulation" && phase !== "closed" ? (
            <button
              type="button"
              className="mt-4 min-h-11 rounded-card border border-line px-4 font-medium text-accent"
              onClick={() => {
                stopTalk();
                chooseLink("bluetooth");
                setSettingsPage("link");
                setSettingsOpen(true);
              }}
            >
              Relier en Bluetooth
            </button>
          ) : null}
          <button
            type="button"
            className="mt-3 flex min-h-11 items-center gap-2 rounded-card border border-line px-4 font-medium text-accent"
            onClick={() => {
              stopTalk();
              setSettingsOpen(false);
              setMessageOpen(true);
            }}
          >
            <MessageSquare className="size-5" />
            Message
          </button>
        </div>
      )}

      <nav className="grid shrink-0 grid-cols-3 border-t border-line pt-3 text-sm">
        <button
          type="button"
          className="flex min-h-11 flex-col items-center gap-1 text-muted"
          onClick={() => choosePresence(presence === "offline" ? "available" : "offline")}
        >
          <UserRound className="size-5" />
          Indisponible
        </button>
        <button
          type="button"
          className="flex min-h-11 flex-col items-center gap-1 text-accent"
          onClick={() => setChannelIndex((index) => (index + 1) % CHANNELS.length)}
        >
          <Radio className="size-5" />
          Canal
        </button>
        <button
          type="button"
          className="flex min-h-11 flex-col items-center gap-1 text-muted"
          onClick={() => {
            hold.current = false;
            setPhase("closed");
          }}
        >
          <X className="size-5" />
          Fermer
        </button>
      </nav>
      {phase === "closed" && !settingsOpen ? (
        <button
          type="button"
          className="mt-4 min-h-11 rounded-card bg-accent font-semibold text-bg"
          onClick={() => {
            setPresence("available");
            setPhase("ready");
          }}
        >
          Démarrer
        </button>
      ) : null}
    </main>
  );
}

function SettingsPanel({
  page,
  presence,
  beepOn,
  linkMode,
  linkMessage,
  opusReady,
  linked,
  micOpen,
  onPage,
  onPresence,
  onBeep,
  onLink,
  onSearch,
}: {
  page: SettingsPage;
  presence: Presence;
  beepOn: boolean;
  linkMode: LinkMode;
  linkMessage: string;
  opusReady: boolean;
  linked: boolean;
  micOpen: boolean;
  onPage: (page: SettingsPage) => void;
  onPresence: (presence: Presence) => void;
  onBeep: () => void;
  onLink: (mode: LinkMode) => void;
  onSearch: () => void;
}) {
  const presenceLabel = PRESENCE.find((item) => item.id === presence)?.label ?? "Disponible";
  const [checks, setChecks] = useState<{ label: string; ok: boolean }[] | null>(null);
  useEffect(() => {
    if (page !== "test") return;
    let cancel = false;
    setChecks(null);
    void checkTalkie({ linkMode, linked, micOpen, encoderReady: opusReady }).then((result) => {
      if (!cancel) setChecks(result);
    });
    return () => {
      cancel = true;
    };
  }, [page, linkMode, linked, micOpen, opusReady]);
  const testsOk = checks !== null && checks.every((item) => item.ok);
  return (
    <section className="mt-4 flex min-h-0 flex-1 flex-col overflow-y-auto rounded-card border border-line bg-surface px-4 py-4">
      {page !== "menu" ? (
        <button
          type="button"
          className="mb-3 flex min-h-11 items-center gap-2 text-sm font-medium text-accent"
          onClick={() => onPage("menu")}
        >
          <ChevronLeft className="size-5" />
          Configuration
        </button>
      ) : (
        <h2 className="text-lg font-medium">Configuration</h2>
      )}
      {page === "menu" ? (
        <div className="mt-3 flex flex-col">
          <MenuRow title="Lien" detail={linkMode === "simulation" ? "Simulation locale" : linkMode === "bluetooth" ? (linked ? "Bluetooth relié" : "Chercher un téléphone") : "Relier en Bluetooth"} onClick={() => onPage("link")} />
          <MenuRow title="Statut" detail={presenceLabel} onClick={() => onPage("status")} />
          <MenuRow title="Audio" detail={beepOn ? "Bip activé" : "Bip coupé"} onClick={() => onPage("audio")} />
          <MenuRow title="Bouton" detail="Maintenir sur l’écran" onClick={() => onPage("button")} />
          <MenuRow title="Comportement" detail="La session s’arrête en quittant" onClick={() => onPage("behavior")} />
          <MenuRow title="Confidentialité" detail="Rien n’est enregistré" onClick={() => onPage("privacy")} />
          <MenuRow title="Test" detail="Salles" onClick={() => onPage("test")} />
        </div>
      ) : null}
      {page === "link" ? (
        <div>
          <div role="radiogroup" aria-label="Lien">
            <button
              type="button"
              role="radio"
              aria-checked={linkMode === "network"}
              className="flex min-h-11 w-full items-center justify-between text-left"
              onClick={() => onLink("network")}
            >
              <span>
                <span className="block font-medium">Web</span>
                <span className="block text-sm text-muted">Le canal passerait par Internet. Aucun lien n’est ouvert.</span>
              </span>
              <span className={`size-3 rounded-full ${linkMode === "network" ? "bg-accent" : "bg-line"}`} />
            </button>
            <button
              type="button"
              role="radio"
              aria-checked={linkMode === "bluetooth"}
              className="mt-2 flex min-h-11 w-full items-center justify-between text-left"
              onClick={() => onLink("bluetooth")}
            >
              <span>
                <span className="block font-medium">Bluetooth direct</span>
                <span className="block text-sm text-muted">100 % hors ligne, entre deux téléphones proches.</span>
              </span>
              <span className={`size-3 rounded-full ${linkMode === "bluetooth" ? "bg-accent" : "bg-line"}`} />
            </button>
            <button
              type="button"
              role="radio"
              aria-checked={linkMode === "simulation"}
              className="mt-2 flex min-h-11 w-full items-center justify-between text-left"
              onClick={() => onLink("simulation")}
            >
              <span>
                <span className="block font-medium">Simulation</span>
                <span className="block text-sm text-muted">Sur ce téléphone seulement. Personne d’autre n’écoute.</span>
              </span>
              <span className={`size-3 rounded-full ${linkMode === "simulation" ? "bg-accent" : "bg-line"}`} />
            </button>
          </div>
          {linkMode === "bluetooth" ? (
            <div className="mt-4">
              <button type="button" className="min-h-11 rounded-card bg-accent px-4 font-semibold text-bg" onClick={onSearch}>
                Chercher un téléphone
              </button>
              <p className="mt-3 text-sm leading-relaxed text-muted">
                {opusReady ? "Encodeur Opus prêt." : "Encodeur Opus absent."} {linkMessage} La liste qui s’ouvre est celle du téléphone : télé, enceinte, terminal ou appareil inconnu. N’associez pas ces appareils. Seul un second téléphone Sentinel peut être relié.
              </p>
            </div>
          ) : null}
        </div>
      ) : null}
      {page === "status" ? (
        <div role="radiogroup" aria-label="Statut">
          {PRESENCE.map((item) => (
            <button
              key={item.id}
              type="button"
              role="radio"
              aria-checked={presence === item.id}
              className="mt-2 flex min-h-11 w-full items-center justify-between gap-3 text-left"
              onClick={() => onPresence(item.id)}
            >
              <span>
                <span className="block font-medium">{item.label}</span>
                <span className="block text-sm text-muted">{item.hint}</span>
              </span>
              <span className={`size-3 rounded-full ${presence === item.id ? "bg-accent" : "bg-line"}`} />
            </button>
          ))}
        </div>
      ) : null}
      {page === "audio" ? (
        <button
          type="button"
          role="switch"
          aria-checked={beepOn}
          className="flex min-h-11 items-center justify-between gap-3 text-left"
          onClick={onBeep}
        >
          <span>
            <span className="block font-medium">Bip d’ouverture</span>
            <span className="block text-sm text-muted">Joué quand le micro s’ouvre. Aucune voix n’est envoyée.</span>
          </span>
          <span className={`rounded-full px-3 py-1 text-sm font-medium ${beepOn ? "bg-accent text-bg" : "bg-bg text-muted"}`}>
            {beepOn ? "Oui" : "Non"}
          </span>
        </button>
      ) : null}
      {page === "button" ? (
        <p className="text-sm leading-relaxed text-muted">
          Le seul bouton actif est celui de l’écran : maintenez pour parler, relâchez pour couper. Aucun bouton matériel n’est branché.
        </p>
      ) : null}
      {page === "behavior" ? (
        <p className="text-sm leading-relaxed text-muted">
          Un seul parleur à la fois. Quitter l’écran ou l’éteindre coupe la session. Le canal est en direct : rien n’est mis en attente.
        </p>
      ) : null}
      {page === "privacy" ? (
        <p className="text-sm leading-relaxed text-muted">
          Aucune voix n’est enregistrée. Aucune position n’est envoyée. L’historique et le QR code restent fermés tant qu’il n’y a pas de contact réel.
        </p>
      ) : null}
      {page === "test" ? (
        <div className="mt-1">
          <p className="text-sm leading-relaxed text-muted">Aucune voix n’est envoyée. LiveKit n’est pas contacté.</p>
          {checks === null ? <p className="mt-3 text-sm">Vérification…</p> : null}
          <ul className="mt-3">
            {checks?.map((item) => (
              <li key={item.label} className={`border-b border-line py-2 text-sm ${item.ok ? "text-ok" : "text-muted"}`}>
                {item.ok ? "OK" : "Échec"} {item.label}
              </li>
            ))}
          </ul>
          {checks !== null ? <p className="mt-3 text-sm font-medium">{testsOk ? "Tests réussis." : "Un test a échoué."}</p> : null}
          <button type="button" className="mt-3 min-h-11 rounded-card border border-line px-4 font-medium text-accent" onClick={() => playReadyBeep()}>
            Bip local
          </button>
        </div>
      ) : null}
    </section>
  );
}

function MenuRow({ title, detail, onClick }: { title: string; detail: string; onClick: () => void }) {
  return (
    <button type="button" className="flex min-h-11 flex-col items-start border-b border-line py-2 text-left" onClick={onClick}>
      <span className="font-medium">{title}</span>
      <span className="text-sm text-muted">{detail}</span>
    </button>
  );
}

function playEchoBeep() {
  playTone(440, 0.18);
}

function playReadyBeep() {
  playTone(880, 0.12);
}

function playTone(frequency: number, seconds: number) {
  const audioWindow = window as Window & { webkitAudioContext?: typeof AudioContext };
  const Context = window.AudioContext ?? audioWindow.webkitAudioContext;
  if (!Context) return;
  const context = new Context();
  const tone = context.createOscillator();
  const level = context.createGain();
  tone.frequency.value = frequency;
  level.gain.value = 0.04;
  tone.connect(level);
  level.connect(context.destination);
  tone.start();
  tone.stop(context.currentTime + seconds);
  tone.onended = () => {
    void context.close();
  };
}

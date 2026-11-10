import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { ChevronLeft, ChevronRight, Home, LayoutGrid, MoreHorizontal, Shield, ShieldAlert } from "lucide-react";

type View =
  | "home"
  | "protection"
  | "apps"
  | "threat"
  | "map"
  | "quarantine"
  | "scan"
  | "report"
  | "vpn"
  | "contact"
  | "messages"
  | "number"
  | "call"
  | "board"
  | "more"
  | "talkie";

const SERVERS = ["Paris", "Allemagne", "États-Unis", "Japon", "Canada", "Royaume-Uni", "Pays-Bas", "Singapour"] as const;

export function SentinelShell({ talkie }: { talkie: ReactNode }) {
  const [view, setView] = useState<View>("home");
  const [vpnOn, setVpnOn] = useState(true);
  const [server, setServer] = useState<(typeof SERVERS)[number]>("Paris");
  const [held, setHeld] = useState(["Trojan.Android.Banker", "Spyware.Monitor", "Riskware.Adware"]);
  const [scan, setScan] = useState(78);
  const [scanning, setScanning] = useState(false);
  const [blocked, setBlocked] = useState(false);

  useEffect(() => {
    if (!scanning) return;
    const id = window.setInterval(() => {
      setScan((value) => {
        if (value >= 100) {
          setScanning(false);
          return 100;
        }
        return value + 2;
      });
    }, 180);
    return () => window.clearInterval(id);
  }, [scanning]);

  if (view === "talkie") {
    return (
      <div className="mx-auto flex h-dvh w-full max-w-md flex-col bg-bg text-fg">
        <button type="button" className="flex min-h-11 items-center gap-2 px-4 text-sm text-accent" onClick={() => setView("more")}>
          <ChevronLeft className="size-4" />
          Sentinel
        </button>
        <div className="min-h-0 flex-1">{talkie}</div>
      </div>
    );
  }

  const open = (next: View) => setView(next);
  const tab = view === "apps" ? "apps" : view === "home" ? "home" : view === "protection" || view === "threat" || view === "quarantine" || view === "scan" ? "protection" : "more";

  return (
    <div className="sentinel-sky mx-auto flex h-dvh w-full max-w-md flex-col text-fg">
      <div className="min-h-0 flex-1 overflow-y-auto px-3 pb-3 pt-3">
        {view === "home" && <Official file="1000415628.jpg" label="Accueil Sentinel, command center" />}
        {view === "protection" && (
          <Official file="1000415625.jpg" label="Protection en temps réel">
            <button type="button" className="min-h-12 rounded-full bg-danger font-semibold" onClick={() => open("quarantine")}>Voir la quarantaine</button>
          </Official>
        )}
        {view === "apps" && (
          <Official file="1000415621.jpg" label="Applications à risque">
            <button type="button" className="min-h-12 rounded-full bg-accent font-semibold text-bg" onClick={() => open("threat")}>Examiner la menace élevée</button>
          </Official>
        )}
        {view === "threat" && (
          <Official file="1000415626.jpg" label="Détail de la menace critique">
            <div className="grid grid-cols-2 gap-2">
              <button type="button" className="min-h-12 rounded-full bg-danger font-semibold" onClick={() => open("quarantine")}>Isoler</button>
              <button type="button" className="min-h-12 rounded-full border border-line" onClick={() => open("apps")}>Retour</button>
            </div>
          </Official>
        )}
        {view === "map" && <Official file="1000415622.jpg" label="Carte mondiale des menaces" />}
        {view === "quarantine" && (
          <Official file="1000415624.jpg" label="Quarantaine sécurisée">
            {held.length === 0 ? <p className="text-sm text-muted">Zone vide.</p> : null}
            {held.map((name) => (
              <button key={name} type="button" className="glass flex min-h-12 items-center justify-between rounded-card px-4 text-left" onClick={() => setHeld((current) => current.filter((item) => item !== name))}>
                <span>{name}</span>
                <span className="text-sm text-danger">Supprimer</span>
              </button>
            ))}
          </Official>
        )}
        {view === "scan" && (
          <Official file="1000415627.jpg" label="Analyse de l’appareil">
            <div className="glass rounded-card p-4">
              <div className="flex items-end justify-between">
                <p className="font-display text-lg">{scan >= 100 ? "Analyse terminée" : "Analyse en cours"}</p>
                <p className="font-display text-3xl text-accent">{scan}%</p>
              </div>
              <div className="mt-3 h-2 overflow-hidden rounded-full bg-line">
                <div className="h-full bg-accent" style={{ width: `${scan}%` }} />
              </div>
            </div>
            {scan >= 100 ? (
              <button type="button" className="min-h-12 rounded-full bg-accent font-semibold text-bg" onClick={() => open("report")}>Voir le rapport</button>
            ) : (
              <button type="button" className="min-h-12 rounded-full bg-accent font-semibold text-bg" disabled={scanning} onClick={() => { setScan(12); setScanning(true); }}>
                {scanning ? "Analyse…" : "Relancer l’analyse"}
              </button>
            )}
          </Official>
        )}
        {view === "report" && (
          <Official file="1000415623.jpg" label="Rapport d’analyse">
            <button type="button" className="min-h-12 rounded-full border border-line" onClick={() => { setScan(8); setScanning(true); open("scan"); }}>Relancer l’analyse</button>
          </Official>
        )}
        {view === "vpn" && (
          <Official file="1000415630.jpg" label="VPN privé">
            <p className="text-sm text-muted">{vpnOn ? `Tunnel actif · ${server}` : "Tunnel coupé"}</p>
            <button type="button" className="min-h-12 rounded-full border border-line" onClick={() => setServer((current) => SERVERS[(SERVERS.indexOf(current) + 1) % SERVERS.length])}>
              Serveur suivant
            </button>
            <button type="button" className={`min-h-12 rounded-full font-semibold ${vpnOn ? "bg-danger" : "bg-accent text-bg"}`} onClick={() => setVpnOn((value) => !value)}>
              {vpnOn ? "Déconnecter" : "Connecter"}
            </button>
          </Official>
        )}
        {view === "contact" && (
          <Official file="1000415631.jpg" label="Contact Marie Dubois">
            <div className="grid grid-cols-2 gap-2">
              <button type="button" className="min-h-12 rounded-full bg-accent font-semibold text-bg" onClick={() => open("call")}>Appeler</button>
              <button type="button" className="min-h-12 rounded-full border border-line" onClick={() => open("messages")}>Message</button>
            </div>
          </Official>
        )}
        {view === "messages" && (
          <Official file="1000415632.jpg" label="Messages sécurisés et alerte de phishing">
            <button type="button" className="flex min-h-12 items-center justify-between rounded-full border border-danger px-4 text-danger" onClick={() => open("number")}>
              <span className="flex items-center gap-2"><ShieldAlert className="size-4" /> Ouvrir la fiche</span>
              <ChevronRight className="size-4" />
            </button>
          </Official>
        )}
        {view === "number" && (
          <Official file="1000415633.jpg" label="Fiche d’un numéro inconnu">
            <p className="text-sm text-muted">{blocked ? "Numéro bloqué." : "Aucune identité confirmée."}</p>
            <div className="grid grid-cols-2 gap-2">
              <button type="button" className="min-h-12 rounded-full border border-ok text-ok" onClick={() => setBlocked(false)}>Autoriser</button>
              <button type="button" className="min-h-12 rounded-full border border-danger text-danger" onClick={() => setBlocked(true)}>Bloquer</button>
            </div>
          </Official>
        )}
        {view === "call" && (
          <div className="flex flex-col gap-3">
            <button type="button" className="flex min-h-11 items-center gap-2" onClick={() => open("contact")}>
              <ChevronLeft className="size-4 text-accent" />
              <span className="font-display text-lg">Appel entrant</span>
            </button>
            <section className="glass rounded-card p-4 text-center">
              <div className="mx-auto mb-3 flex size-24 items-center justify-center rounded-full border border-accent font-display text-3xl">SM</div>
              <h2 className="font-display text-2xl">Sophie Martin</h2>
              <p className="text-muted">Mobile · +33 6 12 34 56 78</p>
              <p className="mt-2 text-sm text-ok">Contact connu · confiance 92</p>
            </section>
            <div className="grid grid-cols-2 gap-2">
              <button type="button" className="min-h-14 rounded-full bg-danger font-semibold" onClick={() => open("contact")}>Refuser</button>
              <button type="button" className="min-h-14 rounded-full bg-ok font-semibold text-bg" onClick={() => open("contact")}>Répondre</button>
            </div>
          </div>
        )}
        {view === "board" && (
          <Official file="1000415629.jpg" label="Planche officielle des écrans Sentinel">
            <button type="button" className="min-h-12 rounded-full border border-line" onClick={() => open("home")}>Retour à l’accueil</button>
          </Official>
        )}
        {view === "more" && <MoreView open={open} />}
      </div>
      <nav className="grid grid-cols-4 border-t border-line bg-bg px-2 py-2">
        <Tab icon={<Home className="size-5" />} label="Accueil" active={tab === "home"} onClick={() => open("home")} />
        <Tab icon={<Shield className="size-5" />} label="Protection" active={tab === "protection"} onClick={() => open("protection")} />
        <Tab icon={<LayoutGrid className="size-5" />} label="Applications" active={tab === "apps"} onClick={() => open("apps")} />
        <Tab icon={<MoreHorizontal className="size-5" />} label="Plus" active={tab === "more"} onClick={() => open("more")} />
      </nav>
    </div>
  );
}

function Official({ file, label, children }: { file: string; label: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col gap-3">
      <div className="overflow-hidden rounded-card">
        <img src={`/d1/screens/${file}`} alt={label} className="w-full" style={{ marginBottom: "-26%" }} />
      </div>
      {children}
    </div>
  );
}

function MoreView({ open }: { open: (view: View) => void }) {
  const items: { id: View; title: string; detail: string }[] = [
    { id: "board", title: "Planche des écrans", detail: "Visuel 09" },
    { id: "map", title: "Carte des menaces", detail: "Visuel 02" },
    { id: "report", title: "Rapport d’analyse", detail: "Visuel 03" },
    { id: "quarantine", title: "Quarantaine", detail: "Visuel 04" },
    { id: "threat", title: "Détail d’une menace", detail: "Visuel 06" },
    { id: "scan", title: "Analyse de l’appareil", detail: "Visuel 07" },
    { id: "vpn", title: "VPN privé", detail: "Visuel 10" },
    { id: "contact", title: "Contacts", detail: "Visuel 11" },
    { id: "messages", title: "Messages", detail: "Visuel 12" },
    { id: "number", title: "Fiche numéro", detail: "Visuel 13" },
    { id: "call", title: "Appel", detail: "Planche 09" },
    { id: "talkie", title: "Talkie-walkie", detail: "Canal réel" },
  ];
  return (
    <div className="flex flex-col gap-2">
      <h1 className="font-display text-2xl">Plus</h1>
      {items.map((item) => (
        <button key={item.id} type="button" className="glass flex min-h-14 items-center justify-between rounded-card px-4 text-left" onClick={() => open(item.id)}>
          <span>
            <span className="block font-medium">{item.title}</span>
            <span className="text-sm text-muted">{item.detail}</span>
          </span>
          <ChevronRight className="size-4 text-muted" />
        </button>
      ))}
    </div>
  );
}

function Tab({ icon, label, active, onClick }: { icon: ReactNode; label: string; active: boolean; onClick: () => void }) {
  return (
    <button type="button" className={`flex min-h-12 flex-col items-center justify-center gap-1 text-xs ${active ? "text-accent" : "text-muted"}`} onClick={onClick}>
      {icon}
      {label}
    </button>
  );
}

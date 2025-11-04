import SentinelLiveWidget from "@/components/SentinelLiveWidget";

export default function AdminConsole() {
  return (
    <div className="p-6 space-y-6">
      <SentinelLiveWidget />
      {/* Ici viendront ton tableau VPN, cartes, graphiques, etc. */}
    </div>
  );
}

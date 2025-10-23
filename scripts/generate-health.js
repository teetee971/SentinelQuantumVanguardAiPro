import fs from "fs";
import { execSync } from "child_process";

try {
  const commit = execSync("git rev-parse --short HEAD").toString().trim();
  const date = new Date().toISOString();

  const data = {
    status: "ok",
    project: "Sentinel Quantum Vanguard AI Pro",
    build_id: `v3.3-${commit}`,
    uptime: date,
    agents: [
      "WatchdogAI",
      "SmartCachePurge",
      "AutoRedeploy",
      "SentinelHealthAgent"
    ],
    site: "https://sentinelquantumvanguardaipro.pages.dev"
  };

  fs.mkdirSync("dist", { recursive: true });
  fs.writeFileSync("dist/health.json", JSON.stringify(data, null, 2));
  console.log("✅ health.json généré avec succès !");
} catch (err) {
  console.error("❌ Erreur lors de la génération du health.json :", err);
  process.exit(1);
}

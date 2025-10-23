import { execSync } from "child_process";
import { setTimeout as wait } from "timers/promises";

const MAX_RETRIES = 3;
const RETRY_DELAY = 10000; // 10 secondes
const BACKUP_BOT_TOKEN = process.env.BACKUP_TELEGRAM_BOT_TOKEN || null;

async function runBot() {
  for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
    try {
      console.log(`\n🚀 Lancement du bot Sentinel (tentative ${attempt}/${MAX_RETRIES})`);
      execSync("node scripts/bot-report.js", { stdio: "inherit" });
      console.log("✅ Bot exécuté avec succès !");
      return;
    } catch (err) {
      console.error(`⚠️ Erreur d’exécution (tentative ${attempt}) :`, err.message);
      if (attempt < MAX_RETRIES) {
        console.log(`⏳ Redémarrage dans ${RETRY_DELAY / 1000} secondes...`);
        await wait(RETRY_DELAY);
      } else {
        console.error("❌ Échec après plusieurs tentatives !");
        if (BACKUP_BOT_TOKEN) triggerFailover();
      }
    }
  }
}

function triggerFailover() {
  console.log("🚨 Activation du bot de secours (Failover Mode)...");
  try {
    execSync("node scripts/bot-report.js", {
      stdio: "inherit",
      env: {
        ...process.env,
        TELEGRAM_BOT_TOKEN: BACKUP_BOT_TOKEN,
      },
    });
    console.log("✅ Backup bot actif et opérationnel !");
  } catch (err) {
    console.error("❌ Erreur sur le bot de secours :", err.message);
  }
}

await runBot();

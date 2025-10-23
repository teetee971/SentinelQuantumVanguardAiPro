import TelegramBot from "node-telegram-bot-api";
import fs from "fs";
import path from "path";

const token = process.env.TELEGRAM_BOT_TOKEN;
const chatId = process.env.TELEGRAM_CHAT_ID;

if (!token || !chatId) {
  console.error("❌ Variables Telegram manquantes !");
  process.exit(1);
}

const bot = new TelegramBot(token, { polling: true });

console.log("🤖 Bot Sentinel /report actif et en écoute...");

bot.onText(/^\/report$/, (msg) => {
  const latestReport = getLatestReport();
  if (!latestReport) {
    bot.sendMessage(chatId, "⚠️ Aucun rapport PDF trouvé.");
    return;
  }

  bot.sendMessage(chatId, "📊 Envoi du dernier rapport Sentinel...");
  bot.sendDocument(chatId, latestReport)
    .then(() => console.log("✅ Rapport envoyé :", latestReport))
    .catch(err => console.error("❌ Erreur envoi :", err));
});

function getLatestReport() {
  const reportsDir = path.resolve("public/reports");
  if (!fs.existsSync(reportsDir)) return null;

  const files = fs.readdirSync(reportsDir)
    .filter(f => f.endsWith(".pdf"))
    .map(f => path.join(reportsDir, f))
    .sort((a, b) => fs.statSync(b).mtime - fs.statSync(a).mtime);

  return files[0] || null;
}

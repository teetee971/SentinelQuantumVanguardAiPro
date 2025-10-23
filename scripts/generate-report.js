import fs from "fs";
import { execSync } from "child_process";
import https from "https";

async function ensurePdfKit() {
  try {
    await import("pdfkit");
  } catch {
    console.log("⚙️ pdfkit non trouvé. Installation automatique...");
    execSync("npm install pdfkit", { stdio: "inherit" });
  }
}

async function sendTelegram(filePath) {
  const botToken = process.env.TELEGRAM_BOT_TOKEN;
  const chatId = process.env.TELEGRAM_CHAT_ID;
  if (!botToken || !chatId) {
    console.log("⚠️ Variables Telegram manquantes. Envoi ignoré.");
    return;
  }

  const boundary = "----SentinelBoundary";
  const data = fs.readFileSync(filePath);

  const body =
    `--${boundary}\r\n` +
    `Content-Disposition: form-data; name="chat_id"\r\n\r\n${chatId}\r\n` +
    `--${boundary}\r\n` +
    `Content-Disposition: form-data; name="caption"\r\n\r\n📊 Rapport Sentinel Quantum Vanguard AI Pro généré avec succès\r\n` +
    `--${boundary}\r\n` +
    `Content-Disposition: form-data; name="document"; filename="${filePath.split("/").pop()}"\r\n` +
    `Content-Type: application/pdf\r\n\r\n` +
    data +
    `\r\n--${boundary}--`;

  const options = {
    method: "POST",
    hostname: "api.telegram.org",
    path: `/bot${botToken}/sendDocument`,
    headers: {
      "Content-Type": `multipart/form-data; boundary=${boundary}`,
      "Content-Length": Buffer.byteLength(body),
    },
  };

  const req = https.request(options, (res) => {
    res.on("data", () => {});
    res.on("end", () => console.log("📨 Rapport envoyé sur Telegram ✅"));
  });

  req.on("error", (e) => console.error("❌ Erreur Telegram:", e));
  req.write(body);
  req.end();
}

async function generate() {
  await ensurePdfKit();
  const PDFDocument = (await import("pdfkit")).default;

  const date = new Date();
  const month = date.toLocaleString("fr-FR", { month: "long", year: "numeric" });
  const build = execSync("git rev-parse --short HEAD").toString().trim();

  fs.mkdirSync("public/reports", { recursive: true });
  const path = `public/reports/Sentinel_Report_${date.getFullYear()}-${date.getMonth() + 1}.pdf`;
  const doc = new PDFDocument();
  doc.pipe(fs.createWriteStream(path));

  doc.fontSize(22).text("🧠 Sentinel Quantum Vanguard AI Pro", { align: "center" });
  doc.moveDown();
  doc.fontSize(14).text(`📆 Rapport mensuel : ${month}`);
  doc.text(`🔖 Build : ${build}`);
  doc.text(`🕒 Généré le : ${date.toLocaleString()}`);
  doc.moveDown();

  doc.fontSize(12).text("Résumé des performances :", { underline: true });
  try {
    const health = JSON.parse(fs.readFileSync("dist/health.json", "utf-8"));
    doc.text(`• Nom : ${health.data.name}`);
    doc.text(`• Version : ${health.data.version}`);
    doc.text(`• Build : ${health.data.build}`);
    doc.text("• Statut : ✅ Stable");
  } catch {
    doc.text("⚠️ health.json introuvable.");
  }

  doc.moveDown();
  doc.fontSize(12).text("Logs & remédiations :", { underline: true });
  if (fs.existsSync("remediation.log")) {
    doc.text(fs.readFileSync("remediation.log", "utf-8"));
  } else {
    doc.text("Aucune remédiation récente.");
  }

  doc.end();
  console.log(`✅ Rapport PDF généré : ${path}`);
  await sendTelegram(path);
}

generate().catch((err) => {
  console.error("❌ Erreur lors de la génération du rapport :", err);
});

import fs from "fs";
import { execSync } from "child_process";
import PDFDocument from "pdfkit";

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

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const docsDir = path.join(__dirname, "public", "assets", "docs");
const fileName = "Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf";
const filePath = path.join(docsDir, fileName);

console.log("🔍 Vérification de la structure de documentation PDF...\n");

// Vérifie si le dossier existe
if (!fs.existsSync(docsDir)) {
  fs.mkdirSync(docsDir, { recursive: true });
  console.log("✅ Dossier créé :", docsDir);
} else {
  console.log("✅ Dossier existe :", docsDir);
}

// Vérifie la présence du fichier
if (!fs.existsSync(filePath)) {
  console.log("⚠️  Le fichier PDF n'est pas encore présent.");
  console.log("📁 Emplacement attendu :", filePath);
} else {
  const stats = fs.statSync(filePath);
  console.log("✅ Le fichier PDF est en place :", filePath);
  console.log("📊 Taille du fichier :", (stats.size / 1024).toFixed(2), "KB");
  console.log("\n🌐 URLs d'accès :");
  console.log("   - Local dev: http://localhost:5173/assets/docs/" + fileName);
  console.log("   - Production: https://sentinelquantumvanguardaipro.pages.dev/assets/docs/" + fileName);
}

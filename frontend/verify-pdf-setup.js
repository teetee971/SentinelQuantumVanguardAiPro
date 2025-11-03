import fs from "fs";
import path from "path";

const docsDir = path.join(process.cwd(), "public", "assets", "docs");
const fileName = "Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf";
const filePath = path.join(docsDir, fileName);

// Vérifie si le dossier existe
if (!fs.existsSync(docsDir)) {
  fs.mkdirSync(docsDir, { recursive: true });
  console.log("✅ Dossier créé :", docsDir);
} else {
  console.log("✅ Le dossier existe déjà :", docsDir);
}

// Vérifie la présence du fichier
if (!fs.existsSync(filePath)) {
  console.log("⚠️ Le fichier PDF n'est pas encore présent, ajoute-le ici :", filePath);
  console.log("\n📁 Pour ajouter le PDF:");
  console.log("   Placez le fichier 'Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf'");
  console.log("   dans le dossier:", docsDir);
  console.log("\n🌐 Une fois ajouté, le fichier sera accessible à:");
  console.log("   - Local: http://localhost:5173/assets/docs/Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf");
  console.log("   - Production: https://sentinelquantumvanguardaipro.pages.dev/assets/docs/Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf");
} else {
  console.log("✅ Le fichier PDF est déjà en place :", filePath);
  const stats = fs.statSync(filePath);
  console.log("   Taille du fichier:", Math.round(stats.size / 1024), "KB");
  console.log("\n🌐 Le fichier sera accessible à:");
  console.log("   - Local: http://localhost:5173/assets/docs/Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf");
  console.log("   - Production: https://sentinelquantumvanguardaipro.pages.dev/assets/docs/Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf");
}

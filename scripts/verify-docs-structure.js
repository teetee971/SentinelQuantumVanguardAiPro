#!/usr/bin/env node

/**
 * Script de vérification de la structure des documents
 * Vérifie que le dossier public/assets/docs existe et contient les fichiers nécessaires
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const docsDir = path.join(__dirname, "..", "public", "assets", "docs");
const fileName = "Sentinel_Quantum_Vanguard_AI_Pro_Executive_Brief.pdf";
const filePath = path.join(docsDir, fileName);

console.log("🔍 Vérification de la structure des documents...\n");

// Vérifie si le dossier existe
if (!fs.existsSync(docsDir)) {
  console.log("⚠️  Le dossier n'existe pas. Création en cours...");
  fs.mkdirSync(docsDir, { recursive: true });
  console.log("✅ Dossier créé :", docsDir);
} else {
  console.log("✅ Dossier existe :", docsDir);
}

// Liste les fichiers présents
console.log("\n📁 Contenu du dossier:");
const files = fs.readdirSync(docsDir);
if (files.length === 0) {
  console.log("   (vide)");
} else {
  files.forEach(file => {
    const stats = fs.statSync(path.join(docsDir, file));
    const size = (stats.size / 1024).toFixed(2);
    console.log(`   - ${file} (${size} KB)`);
  });
}

// Vérifie la présence du fichier PDF attendu
console.log("\n📄 Vérification du fichier PDF:");
if (!fs.existsSync(filePath)) {
  console.log("⚠️  Le fichier PDF n'est pas encore présent.");
  console.log("   Ajoute-le ici :", filePath);
  console.log("\n💡 Pour ajouter le fichier:");
  console.log(`   cp /chemin/vers/ton/pdf "${filePath}"`);
} else {
  const stats = fs.statSync(filePath);
  const size = (stats.size / 1024 / 1024).toFixed(2);
  console.log(`✅ Le fichier PDF est en place : ${fileName}`);
  console.log(`   Taille : ${size} MB`);
  console.log(`   Chemin complet : ${filePath}`);
}

// Instructions pour le déploiement
console.log("\n🚀 Accès au fichier:");
console.log("   Local : http://localhost:5173/assets/docs/" + fileName);
console.log("   Production : https://sentinelquantumvanguardaipro.pages.dev/assets/docs/" + fileName);

console.log("\n✅ Vérification terminée!\n");

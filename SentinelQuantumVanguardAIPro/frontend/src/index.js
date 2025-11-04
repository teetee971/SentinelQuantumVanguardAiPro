const { onRequest } = require("firebase-functions/v2/https");
const axios = require("axios");

exports.scanThreats = onRequest(async (req, res) => {
  try {
    const { fileUrl } = req.body;
    if (!fileUrl) {
      return res.status(400).json({ error: "Aucun fichier fourni" });
    }

    const simulatedThreats = [
      { name: "Trojan.Generic.AI", risk: "Élevé" },
      { name: "APT-Suspect.Pattern", risk: "Moyen" },
    ];

    const hasThreat = Math.random() < 0.25;
    if (hasThreat) {
      return res.json({
        status: "Menace détectée",
        threats: simulatedThreats.slice(0, Math.floor(Math.random() * 2) + 1),
        scanned: fileUrl,
        timestamp: new Date().toISOString(),
      });
    }

    res.json({
      status: "Aucune menace détectée",
      threats: [],
      scanned: fileUrl,
      timestamp: new Date().toISOString(),
    });
  } catch (err) {
    console.error("Erreur Scan IA:", err);
    res.status(500).json({ error: "Erreur interne du module IA Defender" });
  }
});

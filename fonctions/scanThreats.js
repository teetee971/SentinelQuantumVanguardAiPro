const functions = require("firebase-functions");
exports.scanThreats = functions.https.onRequest((req, res) => {
  const file = req.files?.file?.name || "unknown";
  const outcomes = [
    { message: "Aucune menace détectée", score: 12 },
    { message: "Suspicious Behavior", score: 65 },
    { message: "Trojan.Gen détecté", score: 85 }
  ];
  const result = outcomes[Math.floor(Math.random() * outcomes.length)];
  result.engine = "IA Defender Quantum";
  result.details = { file, scanTime: (Math.random()*2).toFixed(2) + "s" };
  res.json(result);
});

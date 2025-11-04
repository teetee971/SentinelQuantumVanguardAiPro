const functions = require("firebase-functions");
const admin = require("firebase-admin");
const cors = require("cors")({ origin: true });

admin.initializeApp();

// --- Route /ping (GET) ---
exports.ping = functions.https.onRequest((req, res) => {
  res.status(200).json({
    status: "ok",
    service: "IA Defender",
    region: "us-central1",
  });
});

// --- Route principale /scanThreats (POST) ---
exports.scanThreats = functions.https.onRequest((req, res) => {
  cors(req, res, async () => {
    if (req.method !== "POST") {
      return res.status(405).json({ error: "Méthode non autorisée" });
    }

    try {
      const { file } = req.body;
      if (!file) {
        return res.status(400).json({ error: "Aucun fichier reçu" });
      }

      // Simulation IA Defender
      const risk = Math.random();
      const result =
        risk < 0.85 ? "Aucun danger détecté" : "Menace potentielle détectée";

      // Enregistrement Firestore (optionnel)
      await admin.firestore().collection("scans").add({
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        filename: file,
        result,
        score: Math.round(risk * 100),
      });

      // Réponse finale
      res.status(200).json({
        message: result,
        score: Math.round(risk * 100),
        engine: "IA Defender Quantum v3",
      });
    } catch (err) {
      console.error("Erreur IA Defender :", err);
      res
        .status(500)
        .json({ error: "Erreur interne du serveur IA Defender" });
    }
  });
});

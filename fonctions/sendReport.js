const functions = require("firebase-functions");
exports.sendReport = functions.https.onRequest((req, res) => {
  const key = req.get("x-sentinel-key");
  if (!key) return res.status(401).json({error:"clé API manquante"});
  res.json({status:"Report stored successfully"});
});

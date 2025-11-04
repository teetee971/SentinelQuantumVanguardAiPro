const functions = require("firebase-functions");
const admin = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp();
}
const db = admin.firestore();

/**
 * Met à jour le statut des agents IA Sentinel en temps réel
 */
exports.updateAgentStatus = functions.pubsub
  .schedule("every 5 minutes")
  .onRun(async () => {
    const agentsRef = db.collection("agents_status");
    const snapshot = await agentsRef.get();
    const now = new Date();

    const updates = [];
    snapshot.forEach(doc => {
      const data = doc.data();
      const lastPing = data.lastPing?.toDate?.() || new Date(0);
      const diff = (now - lastPing) / 60000; // minutes écoulées

      let status = "offline";
      if (diff < 3) status = "active";
      else if (diff < 10) status = "idle";

      updates.push(agentsRef.doc(doc.id).update({
        status,
        lastChecked: now,
      }));
    });

    await Promise.all(updates);
    console.log("✅ Statuts agents IA mis à jour :", updates.length);
    return null;
  });

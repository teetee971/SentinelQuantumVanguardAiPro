/**
 * ✅ Sentinel Vanguard AI Pro — Function UpdateAgentStatus
 * 100% compatible Node 22 / Firebase v5 / ES Modules
 */

import { onSchedule } from "firebase-functions/v2/scheduler";
import { setGlobalOptions } from "firebase-functions/v2/options";
import * as logger from "firebase-functions/logger";
import admin from "firebase-admin";

// ✅ Initialisation Firestore (sécurisée, idempotente)
if (!admin.apps.length) {
  admin.initializeApp();
}
const db = admin.firestore();

// ✅ Configuration globale
setGlobalOptions({
  maxInstances: 10,
  region: "us-central1",
  memory: "256MiB"
});

// ✅ Fonction principale
export const updateAgentStatus = onSchedule("every 5 minutes", async () => {
  try {
    const agentsRef = db.collection("agents_status");
    const snapshot = await agentsRef.get();
    const now = new Date();
    const updates = [];

    snapshot.forEach((doc) => {
      const data = doc.data();
      const lastPing = data.lastPing?.toDate?.() || new Date(0);
      const diff = (now - lastPing) / 60000;
      let status = "offline";
      if (diff < 3) status = "active";
      else if (diff < 10) status = "idle";

      updates.push(
        agentsRef.doc(doc.id).update({
          status,
          lastChecked: now,
        })
      );
    });

    await Promise.all(updates);
    logger.info(`✅ ${updates.length} agents IA mis à jour avec succès.`);
    return null;
  } catch (error) {
    logger.error("❌ Erreur lors de la mise à jour :", error);
    throw new Error("updateAgentStatus failed");
  }
});

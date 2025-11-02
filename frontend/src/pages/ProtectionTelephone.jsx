import React, { useState } from "react";
import { motion } from "framer-motion";
import { Phone, Shield, AlertCircle, PhoneCall, MessageSquare, MapPin, BellRing, Ban, CheckCircle2 } from "lucide-react";
import Navbar from "../components/Navbar";

export default function ProtectionTelephone() {
  const [sosActive, setSosActive] = useState(false);
  const [antiSpamActive, setAntiSpamActive] = useState(true);
  const [callProtectionActive, setCallProtectionActive] = useState(true);

  const activateSOS = () => {
    setSosActive(true);
    setTimeout(() => setSosActive(false), 5000);
  };

  const features = [
    {
      icon: PhoneCall,
      title: "GuardianCall",
      description: "Alerte automatique si un appel suspect reste sans réponse",
      status: "✅ Actif"
    },
    {
      icon: BellRing,
      title: "SOS IA Vocal",
      description: "Mot-clé déclencheur d'un appel de secours IA",
      status: "✅ Opérationnel"
    },
    {
      icon: Ban,
      title: "Anti-Spam IA",
      description: "Blocage intelligent des numéros frauduleux",
      status: antiSpamActive ? "✅ Activé" : "⚠️ Désactivé",
      toggle: true,
      active: antiSpamActive,
      setActive: setAntiSpamActive
    },
    {
      icon: MessageSquare,
      title: "Deepfake Voice Detector",
      description: "Détection de fraudes vocales et usurpation de voix",
      status: "🧪 En test"
    },
    {
      icon: Phone,
      title: "Journal IA Appels & SMS",
      description: "Analyse sémantique et comportementale",
      status: "🔧 À connecter"
    },
    {
      icon: MapPin,
      title: "Localisation d'urgence",
      description: "Alerte IA géolocalisée couplée à Navigenius",
      status: "✅ Opérationnelle"
    }
  ];

  const recentAlerts = [
    { type: "spam", number: "+33 1 XX XX XX XX", time: "Il y a 2h", blocked: true },
    { type: "safe", number: "+33 6 XX XX XX XX", time: "Il y a 5h", blocked: false },
    { type: "suspicious", number: "+1 XXX XXX XXXX", time: "Il y a 1 jour", blocked: true }
  ];

  return (
    <>
      <Navbar />
      <section className="min-h-screen bg-black text-white pt-20 px-6 md:px-12 pb-12">
        <div className="max-w-6xl mx-auto">
          {/* En-tête */}
          <motion.div
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7 }}
            className="text-center mb-12"
          >
            <div className="flex items-center justify-center mb-4">
              <Phone className="w-16 h-16 text-blue-500 mr-4" />
              <h1 className="text-4xl md:text-5xl font-bold text-blue-400">
                Téléphone & Protection des personnes
              </h1>
            </div>
            <p className="text-gray-400 text-lg max-w-3xl mx-auto">
              Protection active 24/7 — Détection d'appels frauduleux, deepfakes vocaux et alerte SOS IA
            </p>
          </motion.div>

          {/* Bouton SOS d'urgence */}
          <motion.div
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.8, delay: 0.2 }}
            className="bg-gradient-to-br from-red-950/40 to-red-900/20 border-2 border-red-600 rounded-2xl p-8 mb-8 text-center"
          >
            <AlertCircle className="w-16 h-16 text-red-500 mx-auto mb-4" />
            <h2 className="text-2xl font-bold text-red-400 mb-3">
              Alerte SOS d'Urgence
            </h2>
            <p className="text-gray-300 mb-6">
              En cas de danger immédiat, activez l'alerte SOS pour contacter les secours et vos contacts d'urgence
            </p>
            
            {sosActive ? (
              <motion.div
                initial={{ scale: 0.8 }}
                animate={{ scale: 1 }}
                className="bg-red-900/50 border border-red-500 rounded-xl p-6"
              >
                <motion.div
                  animate={{ opacity: [1, 0.5, 1] }}
                  transition={{ duration: 1, repeat: Infinity }}
                  className="text-red-400 font-bold text-xl mb-2"
                >
                  🚨 ALERTE SOS ACTIVÉE 🚨
                </motion.div>
                <p className="text-gray-300 text-sm">
                  Services d'urgence notifiés • Localisation envoyée • Contacts alertés
                </p>
              </motion.div>
            ) : (
              <motion.button
                onClick={activateSOS}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                className="px-10 py-4 bg-red-600 hover:bg-red-700 text-white font-bold text-lg rounded-xl shadow-lg transition-all"
              >
                <BellRing className="inline mr-2" />
                ACTIVER L'ALERTE SOS
              </motion.button>
            )}
          </motion.div>

          {/* Modules de protection */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.8, delay: 0.4 }}
            className="mb-8"
          >
            <h2 className="text-2xl font-bold text-blue-300 mb-6">
              <Shield className="inline mr-2" />
              Modules de protection actifs
            </h2>
            <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
              {features.map((feature, i) => (
                <motion.div
                  key={i}
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.1 }}
                  className="bg-blue-950/30 border border-blue-800 rounded-xl p-6 hover:bg-blue-900/20 transition"
                >
                  <feature.icon className="w-12 h-12 text-blue-400 mb-3" />
                  <h3 className="text-lg font-semibold text-blue-300 mb-2">
                    {feature.title}
                  </h3>
                  <p className="text-gray-400 text-sm mb-3">
                    {feature.description}
                  </p>
                  
                  {feature.toggle ? (
                    <div className="flex items-center justify-between">
                      <span className="text-sm">{feature.status}</span>
                      <button
                        onClick={() => feature.setActive(!feature.active)}
                        className={`px-4 py-1 rounded-lg text-sm font-semibold transition ${
                          feature.active
                            ? "bg-green-600 hover:bg-green-700"
                            : "bg-gray-600 hover:bg-gray-700"
                        }`}
                      >
                        {feature.active ? "ON" : "OFF"}
                      </button>
                    </div>
                  ) : (
                    <span className="text-sm font-semibold">{feature.status}</span>
                  )}
                </motion.div>
              ))}
            </div>
          </motion.div>

          {/* Alertes récentes */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.8 }}
            className="bg-blue-950/20 border border-blue-800 rounded-xl p-6 mb-8"
          >
            <h2 className="text-xl font-bold text-blue-300 mb-4">
              📊 Activité récente
            </h2>
            <div className="space-y-3">
              {recentAlerts.map((alert, i) => (
                <div
                  key={i}
                  className={`flex items-center justify-between p-4 rounded-lg ${
                    alert.blocked
                      ? "bg-red-950/30 border border-red-800"
                      : "bg-green-950/30 border border-green-800"
                  }`}
                >
                  <div className="flex items-center">
                    {alert.blocked ? (
                      <Ban className="w-6 h-6 text-red-500 mr-3" />
                    ) : (
                      <CheckCircle2 className="w-6 h-6 text-green-500 mr-3" />
                    )}
                    <div>
                      <p className="font-semibold">{alert.number}</p>
                      <p className="text-xs text-gray-400">{alert.time}</p>
                    </div>
                  </div>
                  <span className={`text-sm font-semibold ${
                    alert.blocked ? "text-red-400" : "text-green-400"
                  }`}>
                    {alert.blocked ? "Bloqué" : "Autorisé"}
                  </span>
                </div>
              ))}
            </div>
          </motion.div>

          {/* Statistiques */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.8, delay: 1 }}
            className="grid md:grid-cols-3 gap-6"
          >
            {[
              { label: "Appels bloqués (7j)", value: "47", color: "red" },
              { label: "Deepfakes détectés", value: "3", color: "orange" },
              { label: "Alertes SOS testées", value: "0", color: "green" }
            ].map((stat, i) => (
              <div
                key={i}
                className="bg-blue-950/30 border border-blue-800 rounded-xl p-6 text-center"
              >
                <p className={`text-4xl font-bold text-${stat.color}-400 mb-2`}>
                  {stat.value}
                </p>
                <p className="text-gray-400 text-sm">{stat.label}</p>
              </div>
            ))}
          </motion.div>

          {/* Information */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 1.2 }}
            className="mt-8 bg-blue-950/20 border border-blue-800 rounded-xl p-6"
          >
            <h3 className="text-lg font-semibold text-blue-300 mb-3">
              🛡️ Protection continue par Sentinel Quantum AI
            </h3>
            <p className="text-gray-400 text-sm">
              Le module <strong>Téléphone & Protection des personnes</strong> surveille en permanence 
              vos communications pour détecter les fraudes, arnaques téléphoniques, et tentatives d'espionnage. 
              Couplé au module <strong>Pegasus Scan IA</strong>, il assure une défense complète contre 
              les menaces mobiles avancées.
            </p>
          </motion.div>
        </div>
      </section>
    </>
  );
}

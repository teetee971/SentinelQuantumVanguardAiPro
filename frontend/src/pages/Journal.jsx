import React, { useEffect, useState } from "react";
import { motion } from "framer-motion";

export default function Journal() {
  const [news, setNews] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchNews = async () => {
      try {
        const res = await fetch(
          "https://api.rss2json.com/v1/api.json?rss_url=https://www.cert.ssi.gouv.fr/feed/"
        );
        const data = await res.json();
        const formatted = data.items.slice(0, 8).map((item) => ({
          title: item.title,
          link: item.link,
          date: new Date(item.pubDate).toLocaleString(),
          description: item.description.replace(/<[^>]+>/g, ""),
        }));
        setNews(formatted);
      } catch (error) {
        console.error("⚠️ Erreur de chargement du flux RSS :", error);
      } finally {
        setLoading(false);
      }
    };

    fetchNews();
  }, []);

  return (
    <section className="min-h-screen bg-black text-white p-6 md:p-12">
      <div className="max-w-6xl mx-auto">
        <motion.h1
          className="text-3xl md:text-4xl font-bold text-blue-400 mb-4"
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.7 }}
        >
          🌐 Journal mondial des menaces — Sentinel Quantum AI
        </motion.h1>
        <p className="text-gray-400 mb-8">
          Mise à jour automatique : dernières alertes cybersécurité, incidents et rapports CERT internationaux.
        </p>

        {/* Carte mondiale dynamique (intégration ThreatMap Global) */}
        <div className="w-full mb-10 rounded-2xl overflow-hidden border border-blue-800 shadow-lg">
          <iframe
            src="https://threatmap.checkpoint.com/"
            title="Carte mondiale des menaces"
            width="100%"
            height="500"
            className="border-none"
          ></iframe>
        </div>

        {/* Liste des actualités */}
        {loading ? (
          <p className="text-gray-500">Chargement des alertes IA...</p>
        ) : (
          <div className="grid md:grid-cols-2 gap-6">
            {news.map((item, i) => (
              <motion.div
                key={i}
                className="bg-blue-950/30 border border-blue-800 rounded-xl p-5 shadow-lg hover:bg-blue-900/40 transition"
                initial={{ opacity: 0, y: 30 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.1 }}
              >
                <h3 className="text-lg text-blue-300 font-semibold mb-2">{item.title}</h3>
                <p className="text-gray-400 text-sm mb-3">
                  {item.description.length > 150
                    ? item.description.slice(0, 150) + "..."
                    : item.description}
                </p>
                <p className="text-xs text-gray-500 mb-2">{item.date}</p>
                <a
                  href={item.link}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-blue-400 hover:text-blue-300 text-sm"
                >
                  🔗 Lire l'article complet
                </a>
              </motion.div>
            ))}
          </div>
        )}

        <p className="text-center text-gray-500 mt-12 text-xs">
          Données synchronisées automatiquement — Sentinel Quantum Vanguard AI Pro
        </p>

        {/* Navigation */}
        <div className="mt-8 flex gap-4">
          <a
            href="/"
            className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 rounded-lg transition"
          >
            ← Retour à l'accueil
          </a>
        </div>
      </div>
    </section>
  );
}

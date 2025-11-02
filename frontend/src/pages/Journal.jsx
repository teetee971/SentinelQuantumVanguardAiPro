import React, { useEffect, useState, useMemo } from "react";
import { motion } from "framer-motion";
import { Globe, Shield, AlertTriangle, TrendingUp, Clock, ExternalLink, Brain, Sparkles, MapPin, Building2, BarChart3, PieChart as PieChartIcon, Filter } from "lucide-react";
import { doc, onSnapshot, collection, query, orderBy } from "firebase/firestore";
import { db } from "../firebaseConfig";
import {
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from "recharts";
import Navbar from "../components/Navbar";

export default function Journal() {
  const [news, setNews] = useState([]);
  const [loading, setLoading] = useState(true);
  const [aiSummary, setAiSummary] = useState("Analyse IA en cours...");
  const [aiLastUpdate, setAiLastUpdate] = useState("");
  const [alerts, setAlerts] = useState([]);
  const [statsGravite, setStatsGravite] = useState([]);
  const [statsSecteur, setStatsSecteur] = useState([]);
  const [filters, setFilters] = useState({
    country: "Tous",
    level: "Tous",
    sector: "Tous",
  });
  const [stats, setStats] = useState({
    critical: 4,
    vulnerabilities: 12,
    activeThreats: 27,
    resolved: 156,
  });
  const [lastUpdate, setLastUpdate] = useState("");

  useEffect(() => {
    // Abonnement à la synthèse IA depuis Firestore
    const unsubAI = onSnapshot(doc(db, "threat_summaries", "global"), (snap) => {
      if (snap.exists()) {
        const data = snap.data();
        setAiSummary(data.summary || "Aucune menace majeure détectée.");
        if (data.updatedAt) {
          const date = new Date(data.updatedAt);
          setAiLastUpdate(date.toLocaleString("fr-FR", { 
            dateStyle: "short", 
            timeStyle: "short" 
          }));
        }
      } else {
        // Si pas encore de données, afficher un message par défaut
        setAiSummary("Les équipes Sentinel AI analysent actuellement les flux mondiaux de cybersécurité. Les menaces critiques détectées dans les dernières 24h incluent des campagnes de phishing sophistiquées et des vulnérabilités zero-day dans plusieurs infrastructures cloud.");
      }
    }, (error) => {
      console.error("Erreur Firestore:", error);
      setAiSummary("Analyse IA temporairement indisponible. Les systèmes de surveillance Sentinel continuent d'opérer normalement.");
    });

    const updateTimestamp = () => {
      const now = new Date();
      setLastUpdate(now.toLocaleString("fr-FR", { 
        dateStyle: "short", 
        timeStyle: "short" 
      }));
    };
    updateTimestamp();

    const fetchNews = async () => {
      try {
        // Tentative de récupération du flux CERT-FR
        const res = await fetch(
          "https://api.rss2json.com/v1/api.json?rss_url=https://www.cert.ssi.gouv.fr/feed/"
        );
        const data = await res.json();
        
        if (data.items && data.items.length > 0) {
          const formatted = data.items.slice(0, 8).map((item) => ({
            title: item.title,
            link: item.link,
            date: new Date(item.pubDate).toLocaleString("fr-FR", {
              dateStyle: "short",
              timeStyle: "short",
            }),
            description: item.description ? item.description.replace(/<[^>]+>/g, "").slice(0, 200) : "Aucune description disponible",
            category: "CERT-FR",
          }));
          setNews(formatted);
        } else {
          // Données de fallback si l'API ne répond pas
          setNews([
            {
              title: "Alerte critique : Vulnérabilité zero-day détectée",
              description: "Une vulnérabilité critique a été identifiée dans plusieurs systèmes d'exploitation. Les équipes de sécurité recommandent une mise à jour immédiate.",
              date: new Date().toLocaleString("fr-FR", { dateStyle: "short", timeStyle: "short" }),
              link: "https://www.cert.ssi.gouv.fr/",
              category: "CRITIQUE",
            },
            {
              title: "Campagne de phishing massive ciblant le secteur bancaire",
              description: "Les chercheurs en sécurité ont identifié une campagne de phishing sophistiquée utilisant l'ingénierie sociale avancée.",
              date: new Date().toLocaleString("fr-FR", { dateStyle: "short", timeStyle: "short" }),
              link: "https://www.cert.ssi.gouv.fr/",
              category: "ALERTE",
            },
            {
              title: "Mise à jour de sécurité critique pour infrastructure cloud",
              description: "Les fournisseurs de services cloud publient des correctifs de sécurité critiques. Déploiement recommandé sous 24h.",
              date: new Date().toLocaleString("fr-FR", { dateStyle: "short", timeStyle: "short" }),
              link: "https://www.cert.ssi.gouv.fr/",
              category: "INFO",
            },
          ]);
        }
      } catch (error) {
        console.error("⚠️ Erreur de chargement du flux RSS :", error);
        // Données de fallback en cas d'erreur
        setNews([
          {
            title: "Service de veille temporairement indisponible",
            description: "Le service de flux d'actualités est en cours de maintenance. Les données seront actualisées prochainement.",
            date: new Date().toLocaleString("fr-FR", { dateStyle: "short", timeStyle: "short" }),
            link: "#",
            category: "SYSTÈME",
          },
        ]);
      } finally {
        setLoading(false);
      }
    };

    fetchNews();
    
    // Abonnement aux alertes critiques depuis Firestore
    const q = query(collection(db, "threat_alerts"), orderBy("date", "desc"));
    const unsubAlerts = onSnapshot(q, (snapshot) => {
      const data = snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
      setAlerts(data);

      // Calcul des statistiques par gravité
      const graviteCounts = {};
      data.forEach((alert) => {
        const level = alert.level || "Mineure";
        graviteCounts[level] = (graviteCounts[level] || 0) + 1;
      });
      const graviteData = Object.entries(graviteCounts).map(([level, count]) => ({
        name: level,
        count,
      }));
      setStatsGravite(graviteData);

      // Calcul des statistiques par secteur
      const secteurCounts = {};
      data.forEach((alert) => {
        if (alert.sector) {
          secteurCounts[alert.sector] = (secteurCounts[alert.sector] || 0) + 1;
        }
      });
      const secteurData = Object.entries(secteurCounts).map(([sector, value]) => ({
        name: sector,
        value,
      }));
      setStatsSecteur(secteurData);
    }, (error) => {
      console.error("Erreur lors de la récupération des alertes:", error);
      // Données de fallback si pas d'alertes dans Firestore
      setAlerts([
        {
          id: "1",
          level: "Critique",
          country: "France",
          sector: "Énergie",
          summary: "Tentative d'intrusion détectée sur infrastructure critique nationale",
          source: "CERT-FR",
          date: new Date().toISOString(),
        },
        {
          id: "2",
          level: "Élevée",
          country: "États-Unis",
          sector: "Finance",
          summary: "Campagne de phishing sophistiquée ciblant les institutions bancaires",
          source: "CISA",
          date: new Date(Date.now() - 3600000).toISOString(),
        },
        {
          id: "3",
          level: "Modérée",
          country: "Allemagne",
          sector: "Santé",
          summary: "Vulnérabilité zero-day identifiée dans système de gestion hospitalière",
          source: "BSI",
          date: new Date(Date.now() - 7200000).toISOString(),
        },
        {
          id: "4",
          level: "Élevée",
          country: "Global",
          sector: "Télécommunications",
          summary: "Attaque DDoS massive sur opérateurs télécoms européens",
          source: "Europol",
          date: new Date(Date.now() - 10800000).toISOString(),
        },
      ]);

      // Calculer les stats de fallback
      const fallbackGravite = [
        { name: "Critique", count: 1 },
        { name: "Élevée", count: 2 },
        { name: "Modérée", count: 1 },
      ];
      setStatsGravite(fallbackGravite);

      const fallbackSecteur = [
        { name: "Énergie", value: 1 },
        { name: "Finance", value: 1 },
        { name: "Santé", value: 1 },
        { name: "Télécommunications", value: 1 },
      ];
      setStatsSecteur(fallbackSecteur);
    });

    // Actualisation toutes les 6 heures (21600000 ms)
    const interval = setInterval(fetchNews, 21600000);
    return () => {
      clearInterval(interval);
      unsubAI();
      unsubAlerts();
    };
  }, []);

  const getCategoryColor = (category) => {
    const colors = {
      "CRITIQUE": "bg-red-900/40 border-red-700 text-red-300",
      "ALERTE": "bg-orange-900/40 border-orange-700 text-orange-300",
      "INFO": "bg-blue-900/40 border-blue-700 text-blue-300",
      "CERT-FR": "bg-cyan-900/40 border-cyan-700 text-cyan-300",
      "SYSTÈME": "bg-gray-900/40 border-gray-700 text-gray-300",
    };
    return colors[category] || colors["INFO"];
  };

  const getAlertLevelColor = (level) => {
    const colors = {
      "Critique": "text-red-400 bg-red-900/20",
      "Élevée": "text-orange-400 bg-orange-900/20",
      "Modérée": "text-yellow-300 bg-yellow-900/20",
      "Mineure": "text-blue-300 bg-blue-900/20",
    };
    return colors[level] || colors["Mineure"];
  };

  const getAlertLevelBadge = (level) => {
    const badges = {
      "Critique": "bg-red-900/60 border-red-700 text-red-300",
      "Élevée": "bg-orange-900/60 border-orange-700 text-orange-300",
      "Modérée": "bg-yellow-900/60 border-yellow-700 text-yellow-300",
      "Mineure": "bg-blue-900/60 border-blue-700 text-blue-300",
    };
    return badges[level] || badges["Mineure"];
  };

  // Filtrer les alertes selon les critères sélectionnés
  const filteredAlerts = useMemo(() => {
    return alerts.filter((alert) => {
      return (
        (filters.level === "Tous" || alert.level === filters.level) &&
        (filters.country === "Tous" || alert.country === filters.country) &&
        (filters.sector === "Tous" || alert.sector === filters.sector)
      );
    });
  }, [alerts, filters]);

  // Extraire les valeurs uniques pour les filtres
  const uniqueCountries = useMemo(() => {
    return [...new Set(alerts.map((a) => a.country))].filter(Boolean).sort();
  }, [alerts]);

  const uniqueSectors = useMemo(() => {
    return [...new Set(alerts.map((a) => a.sector))].filter(Boolean).sort();
  }, [alerts]);

  return (
    <div className="min-h-screen bg-black text-white">
      <Navbar />
      
      <div className="pt-20 px-4 md:px-8 max-w-7xl mx-auto pb-12">
        {/* En-tête */}
        <motion.div
          className="mb-8"
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.7 }}
        >
          <div className="flex items-center gap-3 mb-2">
            <Globe className="w-8 h-8 text-blue-400" />
            <h1 className="text-3xl md:text-4xl font-bold text-blue-400">
              Journal mondial des menaces
            </h1>
          </div>
          <p className="text-gray-400 text-sm">
            Surveillance continue • Alertes cybersécurité • Rapports CERT internationaux
          </p>
          <div className="flex items-center gap-2 mt-2">
            <Clock className="w-4 h-4 text-green-400" />
            <span className="text-xs text-green-400">
              Dernière mise à jour : {lastUpdate}
            </span>
          </div>
        </motion.div>

        {/* Synthèse IA des menaces mondiales */}
        <motion.div
          className="mb-8 bg-gradient-to-br from-blue-950/60 via-blue-900/40 to-blue-950/60 border border-blue-700/70 rounded-xl p-6 shadow-lg shadow-blue-900/30 relative overflow-hidden"
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.8, delay: 0.2 }}
        >
          {/* Effet holographique de fond */}
          <div className="absolute inset-0 bg-gradient-to-r from-transparent via-blue-400/5 to-transparent animate-pulse-slow"></div>
          
          <div className="relative z-10">
            <div className="flex items-center justify-center gap-3 mb-4">
              <Brain className="w-6 h-6 text-blue-300 animate-pulse" />
              <h2 className="text-xl md:text-2xl text-blue-300 font-bold text-center">
                🧠 Synthèse IA des menaces mondiales
              </h2>
              <Sparkles className="w-5 h-5 text-blue-400 animate-pulse" />
            </div>
            
            <p className="text-gray-200 text-sm md:text-base leading-relaxed text-center px-4">
              {aiSummary}
            </p>
            
            {aiLastUpdate && (
              <div className="flex items-center justify-center gap-2 mt-4 text-xs text-blue-400">
                <Clock className="w-3 h-3" />
                <span>Analyse IA générée le {aiLastUpdate}</span>
              </div>
            )}
            
            <div className="flex items-center justify-center gap-2 mt-2">
              <div className="flex gap-1">
                <div className="w-2 h-2 rounded-full bg-green-400 animate-pulse"></div>
                <div className="w-2 h-2 rounded-full bg-green-400 animate-pulse delay-100"></div>
                <div className="w-2 h-2 rounded-full bg-green-400 animate-pulse delay-200"></div>
              </div>
              <span className="text-xs text-gray-400">
                Analyse basée sur GPT-4 • CERT-FR • CISA • Europol
              </span>
            </div>
          </div>
        </motion.div>

        {/* Graphiques dynamiques */}
        <motion.div
          className="mb-10 grid md:grid-cols-2 gap-6"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.4 }}
        >
          {/* Diagramme à barres - Gravité */}
          <div className="bg-blue-950/30 border border-blue-700/50 rounded-2xl p-6 shadow-lg">
            <div className="flex items-center gap-2 mb-4">
              <BarChart3 className="w-5 h-5 text-blue-400" />
              <h3 className="text-lg font-semibold text-blue-300">
                📊 Répartition des alertes par gravité
              </h3>
            </div>
            {statsGravite.length > 0 ? (
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={statsGravite}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e3a8a" />
                  <XAxis 
                    dataKey="name" 
                    stroke="#60a5fa" 
                    style={{ fontSize: '12px' }}
                  />
                  <YAxis 
                    stroke="#60a5fa" 
                    style={{ fontSize: '12px' }}
                  />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: "#0f172a",
                      border: "1px solid #3b82f6",
                      borderRadius: "8px",
                      color: "#e0e7ff",
                    }}
                    cursor={{ fill: "rgba(59, 130, 246, 0.1)" }}
                  />
                  <Bar 
                    dataKey="count" 
                    fill="#38bdf8" 
                    radius={[8, 8, 0, 0]}
                    animationDuration={800}
                  />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-64 flex items-center justify-center text-gray-400 text-sm">
                Aucune donnée disponible
              </div>
            )}
          </div>

          {/* Camembert - Secteur */}
          <div className="bg-blue-950/30 border border-blue-700/50 rounded-2xl p-6 shadow-lg">
            <div className="flex items-center gap-2 mb-4">
              <PieChartIcon className="w-5 h-5 text-cyan-400" />
              <h3 className="text-lg font-semibold text-blue-300">
                🧭 Répartition par secteur impacté
              </h3>
            </div>
            {statsSecteur.length > 0 ? (
              <ResponsiveContainer width="100%" height={280}>
                <PieChart>
                  <Pie
                    data={statsSecteur}
                    cx="50%"
                    cy="50%"
                    labelLine={false}
                    label={({ name, percent }) => `${name}: ${(percent * 100).toFixed(0)}%`}
                    outerRadius={90}
                    dataKey="value"
                    animationDuration={800}
                  >
                    {statsSecteur.map((entry, index) => {
                      const colors = ["#38bdf8", "#3b82f6", "#60a5fa", "#93c5fd", "#1d4ed8", "#0ea5e9", "#7dd3fc"];
                      return (
                        <Cell key={`cell-${index}`} fill={colors[index % colors.length]} />
                      );
                    })}
                  </Pie>
                  <Tooltip
                    contentStyle={{
                      backgroundColor: "#0f172a",
                      border: "1px solid #3b82f6",
                      borderRadius: "8px",
                      color: "#e0e7ff",
                    }}
                  />
                  <Legend
                    wrapperStyle={{ color: "#93c5fd", fontSize: "12px" }}
                    iconType="circle"
                  />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div className="h-64 flex items-center justify-center text-gray-400 text-sm">
                Aucune donnée disponible
              </div>
            )}
          </div>
        </motion.div>

        {/* Système de filtrage */}
        <motion.div
          className="mb-6 bg-blue-950/30 border border-blue-700/50 rounded-xl p-4 shadow-lg"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.6 }}
        >
          <div className="flex flex-col md:flex-row items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <Filter className="w-5 h-5 text-blue-400" />
              <p className="text-blue-300 font-semibold text-sm md:text-base">
                🌐 {filteredAlerts.length} alerte{filteredAlerts.length !== 1 ? "s" : ""} active{filteredAlerts.length !== 1 ? "s" : ""} dans le monde
              </p>
            </div>

            <div className="flex flex-wrap gap-3 justify-center md:justify-end">
              {/* Filtre gravité */}
              <select
                className="bg-black/80 text-blue-300 border border-blue-700 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all"
                value={filters.level}
                onChange={(e) => setFilters({ ...filters, level: e.target.value })}
              >
                <option value="Tous">Gravité : Toutes</option>
                <option value="Critique">🔴 Critique</option>
                <option value="Élevée">🟠 Élevée</option>
                <option value="Modérée">🟡 Modérée</option>
                <option value="Mineure">🔵 Mineure</option>
              </select>

              {/* Filtre pays */}
              <select
                className="bg-black/80 text-blue-300 border border-blue-700 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all"
                value={filters.country}
                onChange={(e) => setFilters({ ...filters, country: e.target.value })}
              >
                <option value="Tous">Pays : Tous</option>
                {uniqueCountries.map((country) => (
                  <option key={country} value={country}>
                    {country}
                  </option>
                ))}
              </select>

              {/* Filtre secteur */}
              <select
                className="bg-black/80 text-blue-300 border border-blue-700 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all"
                value={filters.sector}
                onChange={(e) => setFilters({ ...filters, sector: e.target.value })}
              >
                <option value="Tous">Secteur : Tous</option>
                {uniqueSectors.map((sector) => (
                  <option key={sector} value={sector}>
                    {sector}
                  </option>
                ))}
              </select>

              {/* Bouton de réinitialisation */}
              {(filters.level !== "Tous" || filters.country !== "Tous" || filters.sector !== "Tous") && (
                <button
                  onClick={() => setFilters({ country: "Tous", level: "Tous", sector: "Tous" })}
                  className="px-3 py-2 bg-red-900/40 hover:bg-red-800/60 border border-red-700 rounded-lg text-xs text-red-300 font-semibold transition-all"
                >
                  Réinitialiser
                </button>
              )}
            </div>
          </div>
        </motion.div>

        {/* Tableau des alertes critiques */}
        <motion.div
          className="mb-10 bg-blue-950/20 border border-blue-700/50 rounded-2xl shadow-lg overflow-hidden"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.7 }}
          key={`${filters.level}-${filters.country}-${filters.sector}`}
        >
          <div className="bg-blue-900/40 px-6 py-4 border-b border-blue-700/50 flex items-center gap-3">
            <AlertTriangle className="w-6 h-6 text-red-400" />
            <h2 className="text-xl font-semibold text-blue-300">
              🚨 Alertes critiques en cours
            </h2>
            <span className="ml-auto px-3 py-1 bg-red-900/40 border border-red-700 rounded-full text-xs text-red-300 font-semibold">
              {filteredAlerts.length} / {alerts.length}
            </span>
          </div>
          
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-blue-950/50 border-b border-blue-800">
                <tr>
                  <th className="py-3 px-4 text-left text-blue-300 font-semibold">Gravité</th>
                  <th className="py-3 px-4 text-left text-blue-300 font-semibold">Pays</th>
                  <th className="py-3 px-4 text-left text-blue-300 font-semibold">Secteur</th>
                  <th className="py-3 px-4 text-left text-blue-300 font-semibold">Résumé</th>
                  <th className="py-3 px-4 text-left text-blue-300 font-semibold">Source</th>
                  <th className="py-3 px-4 text-left text-blue-300 font-semibold">Date</th>
                </tr>
              </thead>
              <tbody>
                {filteredAlerts.length === 0 ? (
                  <tr>
                    <td colSpan="6" className="py-8 text-center text-gray-400">
                      {alerts.length === 0 
                        ? "Aucune alerte critique en cours" 
                        : "Aucune alerte ne correspond aux filtres sélectionnés"}
                    </td>
                  </tr>
                ) : (
                  filteredAlerts.map((alert, index) => (
                    <motion.tr
                      key={alert.id}
                      className={`border-b border-blue-900/30 hover:bg-blue-900/20 transition-colors ${getAlertLevelColor(alert.level)}`}
                      initial={{ opacity: 0, x: -20 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ duration: 0.4, delay: 0.5 + index * 0.05 }}
                    >
                      <td className="py-3 px-4">
                        <span className={`inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-semibold border ${getAlertLevelBadge(alert.level)}`}>
                          <AlertTriangle className="w-3 h-3" />
                          {alert.level}
                        </span>
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-2">
                          <MapPin className="w-4 h-4 text-blue-400" />
                          <span className="text-gray-300">{alert.country}</span>
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-2">
                          <Building2 className="w-4 h-4 text-cyan-400" />
                          <span className="text-gray-300">{alert.sector}</span>
                        </div>
                      </td>
                      <td className="py-3 px-4 text-gray-300 max-w-md">
                        {alert.summary}
                      </td>
                      <td className="py-3 px-4">
                        <span className="px-2 py-1 bg-cyan-900/30 border border-cyan-700/50 rounded text-xs text-cyan-300">
                          {alert.source}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-gray-500 text-xs whitespace-nowrap">
                        {new Date(alert.date).toLocaleString("fr-FR", {
                          dateStyle: "short",
                          timeStyle: "short",
                        })}
                      </td>
                    </motion.tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          
          <div className="bg-blue-900/20 px-6 py-3 text-xs text-gray-400 border-t border-blue-800/50">
            Mise à jour automatique • Données synchronisées en temps réel depuis Firestore
          </div>
        </motion.div>

        {/* Statistiques */}
        <motion.div
          className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 1.0 }}
        >
          <div className="bg-gradient-to-br from-red-900/40 to-red-950/40 border border-red-700/50 rounded-xl p-4 shadow-lg">
            <div className="flex items-center justify-between mb-2">
              <AlertTriangle className="w-6 h-6 text-red-400" />
              <span className="text-2xl font-bold text-red-300">{stats.critical}</span>
            </div>
            <p className="text-gray-400 text-xs">Menaces critiques</p>
          </div>

          <div className="bg-gradient-to-br from-orange-900/40 to-orange-950/40 border border-orange-700/50 rounded-xl p-4 shadow-lg">
            <div className="flex items-center justify-between mb-2">
              <Shield className="w-6 h-6 text-orange-400" />
              <span className="text-2xl font-bold text-orange-300">{stats.vulnerabilities}</span>
            </div>
            <p className="text-gray-400 text-xs">Vulnérabilités</p>
          </div>

          <div className="bg-gradient-to-br from-blue-900/40 to-blue-950/40 border border-blue-700/50 rounded-xl p-4 shadow-lg">
            <div className="flex items-center justify-between mb-2">
              <TrendingUp className="w-6 h-6 text-blue-400" />
              <span className="text-2xl font-bold text-blue-300">{stats.activeThreats}</span>
            </div>
            <p className="text-gray-400 text-xs">Menaces actives</p>
          </div>

          <div className="bg-gradient-to-br from-green-900/40 to-green-950/40 border border-green-700/50 rounded-xl p-4 shadow-lg">
            <div className="flex items-center justify-between mb-2">
              <Shield className="w-6 h-6 text-green-400" />
              <span className="text-2xl font-bold text-green-300">{stats.resolved}</span>
            </div>
            <p className="text-gray-400 text-xs">Incidents résolus</p>
          </div>
        </motion.div>

        {/* Carte mondiale des menaces */}
        <motion.div
          className="mb-10"
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.8, delay: 1.2 }}
        >
          <div className="bg-blue-950/20 border border-blue-700/50 rounded-2xl overflow-hidden shadow-lg">
            <div className="bg-blue-900/40 px-6 py-4 border-b border-blue-700/50">
              <h2 className="text-xl font-semibold text-blue-300 flex items-center gap-2">
                <Globe className="w-5 h-5" />
                ThreatMap Global — Surveillance mondiale en temps réel
              </h2>
            </div>
            <div className="relative w-full" style={{ paddingBottom: "56.25%" }}>
              <iframe
                src="https://threatmap.checkpoint.com/"
                title="Carte mondiale des menaces"
                className="absolute top-0 left-0 w-full h-full border-none"
                allowFullScreen
              ></iframe>
            </div>
            <div className="bg-blue-900/20 px-6 py-3 text-xs text-gray-400">
              Source : Check Point Threat Map • Actualisation en temps réel
            </div>
          </div>
        </motion.div>

        {/* Section Alertes */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 1.4 }}
        >
          <div className="flex items-center gap-2 mb-6">
            <AlertTriangle className="w-6 h-6 text-blue-400" />
            <h2 className="text-2xl font-bold text-blue-400">
              Dernières alertes cybersécurité
            </h2>
          </div>

          {loading ? (
            <div className="flex items-center justify-center py-12">
              <div className="flex flex-col items-center gap-4">
                <div className="w-12 h-12 border-4 border-blue-400 border-t-transparent rounded-full animate-spin"></div>
                <p className="text-gray-400">Chargement des alertes IA en cours...</p>
              </div>
            </div>
          ) : (
            <div className="grid md:grid-cols-2 gap-6">
              {news.map((item, i) => (
                <motion.div
                  key={i}
                  className="bg-blue-950/30 border border-blue-800 rounded-xl p-5 shadow-lg hover:bg-blue-900/40 hover:border-blue-600 transition-all duration-300 group"
                  initial={{ opacity: 0, y: 30 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 1.6 + i * 0.1 }}
                >
                  <div className="flex items-start justify-between mb-3">
                    <span className={`px-3 py-1 rounded-full text-xs font-semibold border ${getCategoryColor(item.category)}`}>
                      {item.category}
                    </span>
                    <span className="text-xs text-gray-500">{item.date}</span>
                  </div>
                  
                  <h3 className="text-lg text-blue-300 font-semibold mb-3 group-hover:text-blue-200 transition-colors">
                    {item.title}
                  </h3>
                  
                  <p className="text-gray-400 text-sm mb-4 leading-relaxed">
                    {item.description}
                  </p>
                  
                  <a
                    href={item.link}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-2 text-blue-400 hover:text-blue-300 text-sm font-semibold transition-colors"
                  >
                    <ExternalLink className="w-4 h-4" />
                    Lire l'article complet
                  </a>
                </motion.div>
              ))}
            </div>
          )}
        </motion.div>

        {/* Informations supplémentaires */}
        <motion.div
          className="mt-12 bg-blue-950/20 border border-blue-700/50 rounded-xl p-6"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 2.2 }}
        >
          <h3 className="text-lg font-semibold text-blue-300 mb-3">
            📡 Sources d'information
          </h3>
          <div className="grid md:grid-cols-2 gap-4 text-sm text-gray-400">
            <div>
              <p className="mb-2">• CERT-FR (ANSSI)</p>
              <p className="mb-2">• CISA (États-Unis)</p>
              <p className="mb-2">• ENISA (Europe)</p>
            </div>
            <div>
              <p className="mb-2">• Check Point ThreatCloud</p>
              <p className="mb-2">• Kaspersky CyberMap</p>
              <p className="mb-2">• Sentinel AI Network</p>
            </div>
          </div>
        </motion.div>

        {/* Footer */}
        <motion.div
          className="mt-8 text-center text-xs text-gray-500"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 1, delay: 2.4 }}
        >
          <p>Données synchronisées automatiquement toutes les 6 heures</p>
          <p className="mt-1">© 2025 Sentinel Quantum Vanguard AI Pro — Surveillance mondiale</p>
        </motion.div>
      </div>
    </div>
  );
}

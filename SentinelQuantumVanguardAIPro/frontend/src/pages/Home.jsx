import React, { useState, useEffect, useRef } from "react";
import { motion, AnimatePresence } from "framer-motion";

// === Animation particules de fond ===
const ParticlesBackground = () => {
  const canvasRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const ctx = canvas.getContext("2d");
    let particles = [];
    let animationFrame;

    const resize = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
    };
    window.addEventListener("resize", resize);
    resize();

    for (let i = 0; i < 50; i++) {
      particles.push({
        x: Math.random() * canvas.width,
        y: Math.random() * canvas.height,
        r: Math.random() * 2 + 0.5,
        dx: Math.random() * 0.5 - 0.25,
        dy: Math.random() * 0.5 - 0.25,
      });
    }

    const draw = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      particles.forEach((p) => {
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
        ctx.fillStyle = "#00ffcc55";
        ctx.fill();
        p.x += p.dx;
        p.y += p.dy;
        if (p.x < 0 || p.x > canvas.width) p.dx *= -1;
        if (p.y < 0 || p.y > canvas.height) p.dy *= -1;
      });
      animationFrame = requestAnimationFrame(draw);
    };
    draw();

    return () => {
      cancelAnimationFrame(animationFrame);
      window.removeEventListener("resize", resize);
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        zIndex: 0,
        width: "100%",
        height: "100%",
      }}
    />
  );
};

// === Page principale ===
export default function Home() {
  const [activeModules, setActiveModules] = useState(0);
  const [time, setTime] = useState(new Date().toLocaleTimeString("fr-FR"));
  const [systemStatus, setSystemStatus] = useState("🟢 Réseau stable");

  useEffect(() => {
    const timer = setInterval(() => setTime(new Date().toLocaleTimeString("fr-FR")), 1000);
    const moduleCheck = setInterval(() => {
      const count = 2 + Math.floor(Math.random() * 3);
      setActiveModules(count);
      setSystemStatus(count >= 4 ? "🟢 Réseau optimal" : count >= 2 ? "🟠 Activité partielle" : "🔴 Alerte réseau");
    }, 7000);
    return () => {
      clearInterval(timer);
      clearInterval(moduleCheck);
    };
  }, []);

  const modules = [
    { name: "GPT-4", desc: "Génération de texte avancée", color: "#00ff88" },
    { name: "Gemini", desc: "Analyse contextuelle & vision", color: "#00ccff" },
    { name: "DeepL", desc: "Traduction multilingue IA", color: "#ffee33" },
    { name: "Modération", desc: "Filtrage contenu & sécurité", color: "#ff4444" },
  ];

  const handleNavigate = (path) => {
    document.body.classList.add("fade-out");
    setTimeout(() => {
      window.location.href = path;
    }, 400);
  };

  const ActionButton = ({ label, onClick, color }) => (
    <motion.button
      whileHover={{ scale: 1.05 }}
      whileTap={{ scale: 0.95 }}
      onClick={onClick}
      style={{
        backgroundColor: color,
        color: "#000",
        border: "none",
        borderRadius: "10px",
        padding: "0.8rem 1.6rem",
        margin: "0.4rem",
        fontWeight: "bold",
        cursor: "pointer",
        boxShadow: `0 0 10px ${color}55`,
        transition: "0.2s ease",
      }}
    >
      {label}
    </motion.button>
  );

  return (
    <div
      style={{
        position: "relative",
        color: "#fff",
        minHeight: "100vh",
        fontFamily: "Inter, sans-serif",
        overflowX: "hidden",
      }}
    >
      <ParticlesBackground />

      {/* --- HERO --- */}
      <div style={{ textAlign: "center", padding: "5rem 1rem 3rem", position: "relative", zIndex: 2 }}>
        <motion.h1
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8 }}
          style={{
            fontSize: "2.6rem",
            fontWeight: "700",
            color: "#00ffcc",
            textShadow: "0 0 25px #00ffcc88",
          }}
        >
          Sentinel Quantum Vanguard AI Pro
        </motion.h1>
        <p style={{ marginTop: "1rem", color: "#aaa" }}>
          Système de supervision, de défense et d’analyse IA mondiale
        </p>
        <div style={{ marginTop: "2.5rem" }}>
          <ActionButton label="Accéder à la Console" color="#00ffcc" onClick={() => handleNavigate("/console")} />
          <ActionButton label="Diagnostics Système" color="#00ccff" onClick={() => handleNavigate("/diagnostic")} />
        </div>
      </div>

      {/* --- MODULES --- */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
          gap: "1rem",
          padding: "0 1.5rem",
          position: "relative",
          zIndex: 2,
        }}
      >
        {modules.map((m, i) => (
          <motion.div
            key={i}
            whileHover={{ scale: 1.05, boxShadow: `0 0 25px ${m.color}77` }}
            transition={{ duration: 0.2 }}
            style={{
              backgroundColor: "#0a0a0a",
              borderRadius: "10px",
              padding: "1.2rem",
              border: `1px solid ${m.color}55`,
              textAlign: "center",
            }}
          >
            <h3 style={{ color: m.color, fontSize: "1.2rem", fontWeight: "600" }}>{m.name}</h3>
            <p style={{ color: "#aaa", marginTop: "0.4rem" }}>{m.desc}</p>
            <motion.div
              animate={{ opacity: [1, 0.5, 1], scale: [1, 1.1, 1] }}
              transition={{ repeat: Infinity, duration: 2 }}
              style={{ marginTop: "0.6rem", color: "#0f0", fontSize: "0.9rem" }}
            >
              🟢 Online
            </motion.div>
          </motion.div>
        ))}
      </div>

      {/* --- FONCTIONS --- */}
      <div style={{ textAlign: "center", marginTop: "4rem", zIndex: 2, position: "relative" }}>
        <h2 style={{ fontSize: "1.4rem", marginBottom: "1rem" }}>Fonctions rapides</h2>
        <ActionButton label="Connexion VPN instantanée" color="#0f0" onClick={() => alert("Connexion VPN activée (simulation)")} />
        <ActionButton label="Scan Sécurité" color="#ff0" onClick={() => handleNavigate("/test-ia")} />
        <ActionButton label="Analyse Pegasus" color="#f33" onClick={() => handleNavigate("/pegasus-scan")} />
      </div>

      {/* --- STATUT LIVE --- */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.5 }}
        style={{
          backgroundColor: "#0b0b0b",
          borderRadius: "12px",
          padding: "1.5rem",
          margin: "4rem auto",
          width: "90%",
          maxWidth: "520px",
          textAlign: "center",
          border: "1px solid #111",
          boxShadow: "0 0 20px #00ffcc33",
          zIndex: 2,
          position: "relative",
        }}
      >
        <h3 style={{ marginBottom: "0.5rem", fontWeight: "600", color: "#00ffcc" }}>
          📡 Panneau de statut en direct
        </h3>
        <p>Modules IA actifs : <strong>{activeModules}</strong> / 4</p>
        <p>État du réseau : {systemStatus}</p>
        <p>Heure système : {time}</p>
      </motion.div>

      {/* --- FOOTER --- */}
      <footer
        style={{
          textAlign: "center",
          color: "#666",
          fontSize: "0.9rem",
          borderTop: "1px solid #111",
          padding: "1.5rem 0",
          marginTop: "3rem",
          position: "relative",
          zIndex: 2,
        }}
      >
        <p>Sentinel Quantum Vanguard AI Network © 2025</p>
        <p>
          <a href="https://t.me/SentinelNetwork" style={{ color: "#00ffcc", textDecoration: "none" }}>
            Telegram
          </a>{" "}
          |{" "}
          <a href="https://github.com/teetee971/SentinelQuantumVanguardAiPro" style={{ color: "#00ffcc", textDecoration: "none" }}>
            GitHub
          </a>
        </p>
      </footer>
    </div>
  );
}

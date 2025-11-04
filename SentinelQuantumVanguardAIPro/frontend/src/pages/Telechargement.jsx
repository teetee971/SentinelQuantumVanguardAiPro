import React, { useEffect, useRef, useState } from "react";
import { motion } from "framer-motion";
import QRCode from "qrcode";

export default function Telechargement() {
  const [qrUrl, setQrUrl] = useState("");
  const qrCanvas = useRef(null);

  useEffect(() => {
    const siteURL = "https://sentinelquantumvanguardaipro.pages.dev";
    QRCode.toCanvas(qrCanvas.current, siteURL, {
      width: 180,
      color: {
        dark: "#00ffcc",
        light: "#000000",
      },
    });
    setQrUrl(siteURL);
  }, []);

  return (
    <div
      style={{
        minHeight: "100vh",
        background: "radial-gradient(circle at 20% 20%, #050505 0%, #000 100%)",
        color: "#fff",
        fontFamily: "Inter, sans-serif",
        padding: "6rem 1rem 3rem",
        textAlign: "center",
      }}
    >
      <motion.h1
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.8 }}
        style={{
          fontSize: "2rem",
          fontWeight: "700",
          color: "#00ffcc",
          textShadow: "0 0 15px #00ffcc55",
          marginBottom: "1rem",
        }}
      >
        📲 Téléchargement Sentinel Quantum Vanguard AI Pro
      </motion.h1>

      <p style={{ color: "#aaa", marginBottom: "2rem" }}>
        Installez l’application officielle Sentinel sur votre appareil ou scannez le QR code ci-dessous.
      </p>

      {/* QR CODE */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.5 }}
        style={{
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          flexDirection: "column",
          marginBottom: "2rem",
        }}
      >
        <canvas ref={qrCanvas} style={{ marginBottom: "1rem" }} />
        <p style={{ color: "#00ffcc" }}>Scannez pour accéder à : <br />{qrUrl}</p>
      </motion.div>

      {/* BOUTONS DE TÉLÉCHARGEMENT */}
      <div style={{ marginBottom: "2rem" }}>
        <motion.a
          href="https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases"
          target="_blank"
          whileHover={{ scale: 1.05 }}
          style={{
            display: "inline-block",
            backgroundColor: "#00ffcc",
            color: "#000",
            padding: "0.8rem 1.6rem",
            borderRadius: "10px",
            textDecoration: "none",
            fontWeight: "bold",
            margin: "0.4rem",
          }}
        >
          ⬇️ Télécharger APK / EXE
        </motion.a>

        <motion.a
          href="https://sentinelquantumvanguardaipro.pages.dev"
          target="_blank"
          whileHover={{ scale: 1.05 }}
          style={{
            display: "inline-block",
            backgroundColor: "#00ccff",
            color: "#000",
            padding: "0.8rem 1.6rem",
            borderRadius: "10px",
            textDecoration: "none",
            fontWeight: "bold",
            margin: "0.4rem",
          }}
        >
          🌐 Version Web PWA
        </motion.a>
      </div>

      {/* BADGES */}
      <div style={{ marginTop: "2rem" }}>
        <motion.img
          src="/badges/pwa-badge.png"
          alt="PWA installable"
          style={{ width: "130px", margin: "0.5rem" }}
          whileHover={{ scale: 1.05 }}
        />
        <motion.img
          src="/badges/security-certified.png"
          alt="Sécurité validée"
          style={{ width: "130px", margin: "0.5rem" }}
          whileHover={{ scale: 1.05 }}
        />
      </div>

      {/* FOOTER */}
      <footer
        style={{
          color: "#555",
          fontSize: "0.9rem",
          marginTop: "3rem",
        }}
      >
        Sentinel Quantum Vanguard AI Pro © 2025 — Installation & Distribution Officielle
      </footer>
    </div>
  );
}

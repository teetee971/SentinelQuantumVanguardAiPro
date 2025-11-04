import React, { useState } from "react";
import { motion } from "framer-motion";

export default function Navbar() {
  const [menuOpen, setMenuOpen] = useState(false);

  const links = [
    { name: "Accueil", path: "/" },
    { name: "Console", path: "/console" },
    { name: "Diagnostics", path: "/diagnostic" },
    { name: "Test IA", path: "/test-ia" },
    { name: "Pegasus Scan", path: "/pegasus-scan" },
  ];

  const navigate = (path) => {
    window.location.href = path;
  };

  return (
    <nav
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        width: "100%",
        backdropFilter: "blur(10px)",
        backgroundColor: "rgba(0, 0, 0, 0.4)",
        borderBottom: "1px solid rgba(0,255,204,0.15)",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "0.8rem 1.5rem",
        zIndex: 100,
      }}
    >
      {/* LOGO */}
      <motion.div
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        style={{ display: "flex", alignItems: "center", cursor: "pointer" }}
        onClick={() => navigate("/")}
      >
        <img
          src="/logo.svg"
          alt="Sentinel Logo"
          style={{ width: "38px", height: "38px", marginRight: "0.6rem" }}
        />
        <h1
          style={{
            fontSize: "1.1rem",
            color: "#00ffcc",
            fontWeight: "600",
            textShadow: "0 0 10px #00ffcc55",
          }}
        >
          Sentinel Quantum
        </h1>
      </motion.div>

      {/* MENU PRINCIPAL (Desktop) */}
      <div className="hidden md:flex space-x-4">
        {links.map((link, i) => (
          <motion.button
            key={i}
            whileHover={{ scale: 1.1, color: "#00ffcc" }}
            onClick={() => navigate(link.path)}
            style={{
              background: "none",
              border: "none",
              color: "#ddd",
              fontSize: "0.95rem",
              cursor: "pointer",
              transition: "0.3s",
            }}
          >
            {link.name}
          </motion.button>
        ))}
      </div>

      {/* MENU MOBILE (Hamburger) */}
      <div className="md:hidden">
        <button
          onClick={() => setMenuOpen(!menuOpen)}
          style={{
            background: "none",
            border: "none",
            color: "#00ffcc",
            fontSize: "1.6rem",
          }}
        >
          ☰
        </button>
      </div>

      {/* MENU MOBILE DÉROULANT */}
      {menuOpen && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -10 }}
          style={{
            position: "absolute",
            top: "60px",
            left: 0,
            width: "100%",
            backgroundColor: "rgba(0, 0, 0, 0.95)",
            borderTop: "1px solid #00ffcc33",
            textAlign: "center",
            padding: "1rem 0",
          }}
        >
          {links.map((link, i) => (
            <button
              key={i}
              onClick={() => navigate(link.path)}
              style={{
                display: "block",
                width: "100%",
                padding: "0.7rem 0",
                background: "none",
                border: "none",
                color: "#00ffcc",
                fontSize: "1rem",
              }}
            >
              {link.name}
            </button>
          ))}
        </motion.div>
      )}
    </nav>
  );
}

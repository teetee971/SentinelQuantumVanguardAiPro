// === Sentinel Quantum Vanguard AI Pro – ThreatMap v3 ===
// Auteur : Sentinel DevOps AI Network
// Description : Carte 3D IA + flux d’attaques mondiaux + statut agents actifs

import { Canvas, useFrame } from "@react-three/fiber";
import { OrbitControls, Stars } from "@react-three/drei";
import { motion } from "framer-motion";
import * as THREE from "three";
import { useRef } from "react";

function AttackBeam({ start, end, color }) {
  const ref = useRef();
  useFrame(() => {
    if (!ref.current) return;
    const t = (Date.now() % 2000) / 2000;
    ref.current.position.lerpVectors(start, end, t);
    ref.current.material.opacity = 1 - Math.abs(0.5 - t) * 2;
  });
  return (
    <mesh ref={ref}>
      <sphereGeometry args={[0.01, 16, 16]} />
      <meshBasicMaterial color={color} transparent opacity={0.7} />
    </mesh>
  );
}

export default function ThreatMap() {
  const attacks = [
    { from: [1, 0.3, 0], to: [-1, -0.2, 0.5], color: "red" },
    { from: [-0.7, 0.5, -0.4], to: [0.9, -0.1, 0.3], color: "orange" },
    { from: [0.4, -0.8, 0.2], to: [-0.5, 0.6, -0.3], color: "cyan" },
  ];

  return (
    <div className="min-h-screen flex flex-col md:flex-row bg-gradient-to-br from-gray-900 via-black to-cyan-900 text-white">
      {/* === Panneau de gauche === */}
      <motion.div
        className="md:w-1/3 p-6 md:p-10 flex flex-col justify-center"
        initial={{ opacity: 0, x: -40 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ duration: 1 }}
      >
        <h1 className="text-3xl md:text-5xl font-bold text-cyan-400 mb-4">
          Global Threat Map IA
        </h1>
        <p className="text-gray-300 mb-6">
          Surveillance mondiale en temps réel des vecteurs d’attaques cybernétiques.
          Données synchronisées avec le réseau Sentinel Quantum AI Pro.
        </p>

        <div className="bg-gray-800/40 rounded-xl p-4 shadow-md backdrop-blur">
          <h2 className="text-xl font-semibold text-cyan-300 mb-2">
            Agents Sentinel Actifs
          </h2>
          <ul className="space-y-2 text-sm text-gray-300">
            <li>🟢 QuantumFailover AI — actif</li>
            <li>🟢 FlowFinalizer — stabilisation réseau</li>
            <li>🟢 GlobalFailoverWatcher — synchro DNS</li>
            <li>🟢 CognitiveTraceAgent — analyse comportementale</li>
            <li>🟢 PerformanceAutoTuner — optimisation GPU</li>
          </ul>
        </div>
      </motion.div>

      {/* === Section Globe 3D === */}
      <motion.div
        className="flex-1 h-[400px] md:h-screen"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.5, duration: 1 }}
      >
        <Canvas camera={{ position: [0, 0, 3] }}>
          <ambientLight intensity={0.4} />
          <directionalLight position={[2, 2, 5]} />
          <Stars radius={300} depth={60} count={2000} factor={7} fade />

          {/* Terre */}
          <mesh rotation={[0.4, 0.8, 0]}>
            <sphereGeometry args={[1, 64, 64]} />
            <meshStandardMaterial
              map={new THREE.TextureLoader().load(
                "https://cdn.jsdelivr.net/gh/teetee971/assets@main/earth_night.jpg"
              )}
            />
          </mesh>

          {/* Flux d’attaques */}
          {attacks.map((a, i) => (
            <AttackBeam key={i} start={new THREE.Vector3(...a.from)} end={new THREE.Vector3(...a.to)} color={a.color} />
          ))}

          <OrbitControls enableZoom={false} autoRotate autoRotateSpeed={0.7} />
        </Canvas>
      </motion.div>
    </div>
  );
}

import { Canvas, useFrame } from "@react-three/fiber";
import { OrbitControls, Stars } from "@react-three/drei";
import { motion } from "framer-motion";
import { useRef, useState } from "react";
import { Link } from "react-router-dom";
import { Shield, Zap, Lock } from "lucide-react";

// --- Composant pour les points d'activité ---
function ActivityDots() {
  const group = useRef();
  const [points] = useState(() =>
    Array.from({ length: 30 }, () => ({
      x: (Math.random() - 0.5) * 2,
      y: (Math.random() - 0.5) * 2,
      z: (Math.random() - 0.5) * 2,
      intensity: 0.3 + Math.random() * 0.7,
    }))
  );

  useFrame(({ clock }) => {
    const t = clock.getElapsedTime();
    group.current.rotation.y = t * 0.05;
  });

  return (
    <group ref={group}>
      {points.map((p, i) => (
        <mesh key={i} position={[p.x, p.y, p.z]}>
          <sphereGeometry args={[0.02, 16, 16]} />
          <meshBasicMaterial
            color={`hsl(${Math.random() * 360},100%,60%)`}
            transparent
            opacity={p.intensity}
          />
        </mesh>
      ))}
    </group>
  );
}

// --- Composant principal ---
export default function Journal() {
  return (
    <div className="relative w-full h-[85vh] flex flex-col items-center justify-center overflow-hidden">
      <motion.h2
        className="text-sentinel-accent text-lg md:text-2xl mb-1 font-semibold z-10"
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
      >
        🌍 Threat Map — Réseau IA Sentinel
      </motion.h2>

      <motion.p
        className="text-gray-400 text-xs md:text-sm mb-4 z-10"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.3 }}
      >
        Surveillance mondiale en temps réel — activité IA détectée.
      </motion.p>

      {/* Globe 3D + points IA */}
      <div className="absolute inset-0">
        <Canvas camera={{ position: [0, 0, 3.5] }}>
          <ambientLight intensity={0.8} />
          <pointLight position={[10, 10, 10]} />

          {/* Arrière-plan */}
          <Stars radius={120} depth={60} count={4000} factor={3} saturation={0} fade />

          {/* Globe IA */}
          <mesh rotation={[0.3, 0.6, 0]}>
            <sphereGeometry args={[1, 64, 64]} />
            <meshStandardMaterial
              color="#00ffff"
              wireframe
              transparent
              opacity={0.2}
            />
          </mesh>

          {/* Points lumineux simulant les activités IA */}
          <ActivityDots />

          <OrbitControls enableZoom={false} autoRotate autoRotateSpeed={1.2} />
        </Canvas>
      </div>

      {/* Données temps réel */}
      <div className="absolute bottom-4 text-center text-gray-400 text-xs">
        <p>Dernière mise à jour : {new Date().toLocaleTimeString()}</p>
        <p className="text-sentinel-accent mt-1 animate-pulse">IA Network Monitor — ONLINE</p>
      </div>

      {/* Pricing CTA Banner */}
      <motion.div 
        className="absolute top-4 right-4 z-20"
        initial={{ opacity: 0, x: 20 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ delay: 0.8 }}
      >
        <Link 
          to="/pricing?utm_source=homepage&utm_medium=cta&utm_campaign=hero-banner"
          className="group flex items-center gap-2 px-4 py-2.5 rounded-lg bg-gradient-to-r from-sentinel-accent/90 to-sentinel-accent/70 hover:from-sentinel-accent hover:to-sentinel-accent/90 text-black font-semibold text-sm shadow-lg shadow-sentinel-accent/20 transition-all duration-300 hover:shadow-sentinel-accent/40"
        >
          <Shield size={18} className="group-hover:rotate-12 transition-transform" />
          <span>Protégez-vous dès aujourd'hui</span>
          <Zap size={16} className="group-hover:animate-pulse" />
        </Link>
      </motion.div>

      {/* Key Features - Bottom Banner */}
      <motion.div 
        className="absolute bottom-20 left-1/2 transform -translate-x-1/2 z-20 w-full max-w-3xl px-4"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 1 }}
      >
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <div className="backdrop-blur-sm bg-black/40 border border-sentinel-glow/20 rounded-lg p-3 text-center">
            <Shield size={24} className="mx-auto mb-2 text-sentinel-accent" />
            <h3 className="text-white text-sm font-semibold mb-1">Protection IA</h3>
            <p className="text-gray-400 text-xs">Détection avancée des menaces</p>
          </div>
          
          <div className="backdrop-blur-sm bg-black/40 border border-sentinel-glow/20 rounded-lg p-3 text-center">
            <Zap size={24} className="mx-auto mb-2 text-sentinel-accent" />
            <h3 className="text-white text-sm font-semibold mb-1">Temps Réel</h3>
            <p className="text-gray-400 text-xs">Surveillance 24/7 automatisée</p>
          </div>
          
          <div className="backdrop-blur-sm bg-black/40 border border-sentinel-glow/20 rounded-lg p-3 text-center">
            <Lock size={24} className="mx-auto mb-2 text-sentinel-accent" />
            <h3 className="text-white text-sm font-semibold mb-1">Plans Flexibles</h3>
            <p className="text-gray-400 text-xs">
              <Link to="/pricing" className="text-sentinel-accent hover:underline">
                À partir de 0€/mois
              </Link>
            </p>
          </div>
        </div>
      </motion.div>
    </div>
  );
}

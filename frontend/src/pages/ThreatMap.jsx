import { Canvas, useFrame } from "@react-three/fiber";
import { OrbitControls, Stars } from "@react-three/drei";
import { motion } from "framer-motion";
import { useRef } from "react";

// Petits points d'activité IA aléatoires
function ActivityDots({ count = 40 }) {
  const group = useRef();
  const dots = Array.from({ length: count }, (_, i) => ({
    id: i,
    pos: [
      Math.sin(i) * 1.2,
      Math.cos(i * 1.7) * 1.2,
      Math.sin(i * 2.1) * 1.2,
    ],
  }));

  useFrame(() => {
    group.current.rotation.y += 0.0015;
  });

  return (
    <group ref={group}>
      {dots.map((d) => (
        <mesh key={d.id} position={d.pos}>
          <sphereGeometry args={[0.015, 12, 12]} />
          <meshStandardMaterial emissive="#00ffff" emissiveIntensity={2} />
        </mesh>
      ))}
    </group>
  );
}

// Halo radar IA
function Halo() {
  const ref = useRef();
  useFrame(() => {
    ref.current.rotation.z += 0.002;
  });

  return (
    <mesh ref={ref}>
      <ringGeometry args={[1.05, 1.2, 64]} />
      <meshBasicMaterial
        color="#00ffff"
        transparent
        opacity={0.15}
        side={2}
      />
    </mesh>
  );
}

// Composant principal
export default function ThreatMap() {
  return (
    <div className="relative w-full h-[85vh] flex flex-col items-center justify-center">
      <motion.h2
        className="text-sentinel-accent text-lg md:text-2xl mb-2 font-semibold z-10"
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
      >
        🌐 Sentinel AI – Threat Map Live
      </motion.h2>

      <motion.p
        className="text-gray-400 text-xs md:text-sm mb-4 z-10"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.3 }}
      >
        Surveillance en temps réel des menaces et flux IA mondiaux.
      </motion.p>

      <div className="absolute inset-0">
        <Canvas camera={{ position: [0, 0, 3.5] }}>
          <ambientLight intensity={0.7} />
          <pointLight position={[10, 10, 10]} />
          <Stars radius={100} depth={50} count={5000} factor={4} fade />

          {/* Globe principal */}
          <mesh rotation={[0.3, 0.6, 0]}>
            <sphereGeometry args={[1, 64, 64]} />
            <meshStandardMaterial
              color="#00ffff"
              wireframe
              transparent
              opacity={0.25}
            />
          </mesh>

          <Halo />
          <ActivityDots />
          <OrbitControls enableZoom={false} autoRotate autoRotateSpeed={1} />
        </Canvas>
      </div>

      <div className="absolute bottom-4 text-center text-gray-400 text-xs">
        <p>AI Data Stream — {new Date().toLocaleTimeString()}</p>
      </div>
    </div>
  );
}

import { useEffect, useState } from "react";
import LoadingScreen from "./components/LoadingScreen";
import SentinelAI from "./SentinelAI";

export default function App() {
  const [isLoaded, setIsLoaded] = useState(false);

  // 🔊 Lecture du son spatial à l’ouverture (unique)
  useEffect(() => {
    const audio = new Audio("/enter.mp3");
    audio.volume = 0.6;
    audio.play().catch(() => {
      // iOS bloque parfois l’auto-play : on attend l’interaction utilisateur
      const unlock = () => {
        audio.play();
        window.removeEventListener("click", unlock);
      };
      window.addEventListener("click", unlock);
    });
  }, []);

  return (
    <div className="relative w-full h-screen overflow-hidden bg-black">
      {!isLoaded ? (
        // 🌀 Écran d’intro 3D (WebGL + halo)
        <LoadingScreen onFinish={() => setIsLoaded(true)} />
      ) : (
        // 🧠 Interface principale IA
        <div className="animate-fadeIn">
          <SentinelAI />
        </div>
      )}
    </div>
  );
}
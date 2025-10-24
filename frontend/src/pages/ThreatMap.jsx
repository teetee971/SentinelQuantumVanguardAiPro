import { useEffect, useRef } from "react";

export default function ThreatMap() {
  const canvasRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const ctx = canvas.getContext("2d");
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;

    // === Paramètres des particules ===
    const totalPoints = 120;
    const points = Array.from({ length: totalPoints }, () => ({
      x: Math.random() * canvas.width,
      y: Math.random() * canvas.height,
      r: Math.random() * 2 + 0.8,
      dx: (Math.random() - 0.5) * 1.2,
      dy: (Math.random() - 0.5) * 1.2,
      color:
        ["#00FFFF", "#0ff", "#00BFFF", "#39FF14", "#0088FF"][
          Math.floor(Math.random() * 5)
        ],
    }));

    // === Animation principale ===
    function animate() {
      ctx.fillStyle = "#050505";
      ctx.fillRect(0, 0, canvas.width, canvas.height);

      // Cercles dynamiques (points IA)
      points.forEach((p) => {
        p.x += p.dx;
        p.y += p.dy;
        if (p.x < 0 || p.x > canvas.width) p.dx *= -1;
        if (p.y < 0 || p.y > canvas.height) p.dy *= -1;

        ctx.beginPath();
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
        ctx.fillStyle = p.color;
        ctx.shadowBlur = 10;
        ctx.shadowColor = p.color;
        ctx.fill();
      });

      // Connexions entre points (réseau IA)
      ctx.lineWidth = 0.3;
      points.forEach((p1, i) => {
        for (let j = i + 1; j < points.length; j++) {
          const p2 = points[j];
          const dist = Math.hypot(p1.x - p2.x, p1.y - p2.y);
          if (dist < 100) {
            ctx.beginPath();
            ctx.moveTo(p1.x, p1.y);
            ctx.lineTo(p2.x, p2.y);
            ctx.strokeStyle = `rgba(0,255,255,${1 - dist / 100})`;
            ctx.stroke();
          }
        }
      });

      // Texte central animé
      ctx.font = "bold 28px Inter";
      ctx.fillStyle = "#00FFFF";
      ctx.textAlign = "center";
      ctx.shadowBlur = 20;
      ctx.shadowColor = "#00FFFF";
      ctx.fillText("SENTINEL QUANTUM VANGUARD AI PRO", canvas.width / 2, 70);

      ctx.font = "16px Inter";
      ctx.fillStyle = "#00BFFF";
      ctx.shadowBlur = 0;
      ctx.fillText(
        "Détection comportementale et cyber-menaces en temps réel",
        canvas.width / 2,
        100
      );

      requestAnimationFrame(animate);
    }

    animate();

    // Gestion du redimensionnement
    const handleResize = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
    };
    window.addEventListener("resize", handleResize);

    return () => window.removeEventListener("resize", handleResize);
  }, []);

  return (
    <div className="relative w-full h-screen bg-black overflow-hidden flex flex-col items-center justify-center">
      <canvas ref={canvasRef} className="absolute inset-0" />
      <div className="absolute bottom-10 text-center text-gray-400 text-sm z-10">
        <p>🛰️ Supervision IA Globale — Threat Map Live Network</p>
      </div>
    </div>
  );
}

## 🌐 Architecture IA — Réseau Global Sentinel

Ce module affiche en temps réel la topologie IA du réseau Sentinel (agents actifs, clusters IA, supervision).

<div id="globeViz" style="width:100%;height:500px;background:#000;border-radius:12px;"></div>

<script src="./assets/three.min.js"></script>
<script src="./assets/three-globe.min.js"></script>
<script>
  const renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setSize(window.innerWidth, 500);
  document.getElementById("globeViz").appendChild(renderer.domElement);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(55, window.innerWidth / 500, 0.1, 2000);
  camera.position.z = 350;

  const globe = new ThreeGlobe()
    .globeImageUrl("//unpkg.com/three-globe/example/img/earth-dark.jpg")
    .pointsData(Array.from({ length: 40 }).map(() => ({
      lat: (Math.random() - 0.5) * 180,
      lng: (Math.random() - 0.5) * 360,
      size: Math.random() * 1.4 + 0.4,
      color: ["#00ffff","#39ff14","#ff00ff","#ffaa00"][Math.floor(Math.random() * 4)]
    })))
    .pointAltitude("size")
    .pointColor("color");

  const light = new THREE.AmbientLight(0xffffff, 1);
  scene.add(light);
  scene.add(globe);

  function animate() {
    globe.rotation.y += 0.0015;
    renderer.render(scene, camera);
    requestAnimationFrame(animate);
  }
  animate();
</script>

### Détails techniques
- **IA Agents** : auto-supervision et communication inter-nœuds.
- **Réseau global** : synchronisation Cloudflare ↔ Sentinel.
- **Surveillance** : monitoring de latence, stabilité et charge IA.


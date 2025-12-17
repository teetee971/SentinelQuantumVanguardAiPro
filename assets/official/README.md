# Assets Officiels - Sentinel Quantum Vanguard AI Pro

Ce dossier contient les assets visuels officiels de la plateforme Sentinel.

## Structure

```
/assets/official/
├── README.md (ce fichier)
├── images/
│   ├── hero/
│   │   ├── hero-bg.jpg          # Image de fond hero (1920x1080)
│   │   └── soldier-tech.png     # Soldat technologique (optionnel)
│   ├── logos/
│   │   ├── logo-light.svg       # Logo clair (fond sombre)
│   │   └── logo-dark.svg        # Logo sombre (fond clair)
│   └── modules/
│       └── (déjà présent dans /assets/images/modules/)
└── videos/
    ├── hero.mp4                 # Vidéo hero (autoplay muted loop)
    ├── hero.webm                # Version WebM pour compatibilité
    └── hero-poster.jpg          # Image fallback si vidéo ne charge pas
```

## Directives Visuelles

### Contraintes Strictes

❌ **INTERDIT:**
- Emojis dans les visuels
- Images "dessin" / cartoon
- Couleurs flashy ou non professionnelles
- Animations trop lourdes (> 5 MB)

✅ **REQUIS:**
- Visuels futuristes cohérents
- Palette Sentinel Official (#0E141C, #4a90e2, #5ba3f5)
- Optimisation mobile (WebP, lazy loading)
- Accessibilité (alt text, contraste WCAG AA)

### Vidéo Hero

**Spécifications:**
- Format: MP4 (H.264) + WebM (VP9) pour compatibilité
- Résolution: 1920x1080 (Full HD)
- Durée: 10-30 secondes (loop)
- Taille max: 5 MB (fortement compressé)
- Attributs HTML:
  ```html
  <video autoplay muted loop playsinline poster="assets/official/videos/hero-poster.jpg">
    <source src="assets/official/videos/hero.mp4" type="video/mp4">
    <source src="assets/official/videos/hero.webm" type="video/webm">
  </video>
  ```

**Thèmes suggérés:**
- Réseaux de données abstraits
- Tableaux de bord cybersécurité
- Écrans de surveillance SOC
- Cartographie réseau en 3D
- Code/matrices en mouvement

**Fallback:**
- Si vidéo non disponible, utiliser `hero-poster.jpg`
- Détecter connexion lente (saveData API) et skip vidéo

### Images Hero

**hero-bg.jpg:**
- Résolution: 1920x1080
- Format: JPEG optimisé (qualité 80)
- Taille max: 200 KB
- Thème: Futuriste, sombre, professionnel

**soldier-tech.png (optionnel):**
- Format: PNG avec transparence
- Résolution: 800x1200 max
- Taille max: 300 KB
- Style: Silhouette technologique, pas de visage détaillé

### Logos

**logo-light.svg:**
- Usage: Fond sombre (header, hero)
- Couleur principale: #e8eaed (blanc cassé)
- Taille: 180x60 px (ratio 3:1)

**logo-dark.svg:**
- Usage: Fond clair (documents, exports)
- Couleur principale: #0E141C
- Taille: 180x60 px (ratio 3:1)

## Optimisation Performance

### Images
```bash
# Optimisation JPEG
jpegoptim --max=80 --strip-all hero-bg.jpg

# Optimisation PNG
pngquant --quality=65-80 soldier-tech.png

# Conversion WebP (optionnel, meilleure compression)
cwebp -q 80 hero-bg.jpg -o hero-bg.webp
```

### Vidéos
```bash
# Compression MP4 (H.264)
ffmpeg -i input.mp4 -vcodec h264 -acodec aac -b:v 1M -maxrate 1M -bufsize 2M hero.mp4

# Conversion WebM (VP9)
ffmpeg -i input.mp4 -c:v libvpx-vp9 -b:v 1M -c:a libopus hero.webm

# Extraction poster frame
ffmpeg -i hero.mp4 -ss 00:00:01 -vframes 1 hero-poster.jpg
```

## Intégration dans le Code

### index.html (Hero Section)

```html
<section class="hero">
    <!-- Video background -->
    <video 
        class="hero-video" 
        autoplay 
        muted 
        loop 
        playsinline
        poster="assets/official/videos/hero-poster.jpg"
        id="heroVideo">
        <source src="assets/official/videos/hero.mp4" type="video/mp4">
        <source src="assets/official/videos/hero.webm" type="video/webm">
    </video>
    
    <!-- Fallback background -->
    <div class="hero-video-fallback" style="background-image: url('assets/official/images/hero/hero-bg.jpg')"></div>
    
    <!-- Content au-dessus de la vidéo -->
    <div class="hero-content">
        <h1 class="hero-title">Sentinel Quantum Vanguard</h1>
        <p class="hero-tagline">Cybersécurité souveraine par IA</p>
    </div>
</section>
```

### CSS pour Vidéo Responsive

```css
.hero-video {
    position: absolute;
    top: 50%;
    left: 50%;
    min-width: 100%;
    min-height: 100%;
    width: auto;
    height: auto;
    transform: translate(-50%, -50%);
    object-fit: cover;
    z-index: 0;
    opacity: 0.4; /* Semi-transparent pour lisibilité texte */
}

@media (max-width: 768px) {
    .hero-video {
        display: none; /* Désactiver vidéo sur mobile pour performance */
    }
}
```

### JavaScript pour Détection Connexion

```javascript
// Détection data-saver mode
const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
const saveData = connection?.saveData || false;

if (saveData) {
    document.getElementById('heroVideo').style.display = 'none';
}
```

## Sources et Licences

**Assets fournis par:**
- Créations internes équipe Sentinel
- Banques d'images libres de droits (Unsplash, Pexels)
- Vidéos sous licence Creative Commons (avec attribution)

**Licences acceptables:**
- CC0 (domaine public)
- CC BY (attribution requise)
- Licence MIT/Apache pour assets code

**Vérification copyright obligatoire avant ajout.**

## Contribution

Pour ajouter de nouveaux assets:

1. Respecter les directives ci-dessus
2. Optimiser AVANT commit (images < 300 KB, vidéos < 5 MB)
3. Tester sur mobile (Samsung S24+)
4. Vérifier accessibilité (alt text, contraste)
5. Documenter la source et licence dans ce README

## Contact

Pour questions sur les assets:
- Repository: https://github.com/teetee971/SentinelQuantumVanguardAiPro
- Issues: https://github.com/teetee971/SentinelQuantumVanguardAiPro/issues

---

**Dernière mise à jour:** Décembre 2025
**Mainteneur:** Équipe Sentinel Quantum

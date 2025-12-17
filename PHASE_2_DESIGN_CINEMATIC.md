# Phase 2: Design Cinématique - Implementation Guide

## ✅ Status: Infrastructure Ready, Assets Pending

### Objectif
Appliquer un design cinématique premium avec:
- Hero avec vidéo Sentinel (soldat / IA / cybersécurité)
- Images réalistes pour chaque module
- Effet Liquid Glass / Glassmorphism maîtrisé
- Animations légères (scroll, fade, reveal)

---

## 🎨 Implementation actuelle

### ✅ Déjà implémenté

#### 1. Hero Section avec Video Background
- Structure HTML prête pour vidéo `<video>` tag
- Fallback SVG pattern intégré
- Liquid Glass overlay avec `backdrop-filter: blur(2px)`
- Animations fadeInUp sur titre/tagline/CTA
- Support responsive mobile

**Code location:** `index.html` lignes 58-200

#### 2. Glassmorphism Effects
- Backgrounds avec `backdrop-filter` et transparence
- Radial gradients subtils
- Effet "Liquid Glass" sur cards et sections
- Support thème Glass (`body.theme-glass`)

**Exemples:**
```css
background: rgba(255, 255, 255, 0.04);
backdrop-filter: blur(20px);
border: 1px solid rgba(255, 255, 255, 0.08);
```

#### 3. Micro-animations
- Scroll-triggered fade-in/fade-out
- Hover effects sur cards
- Smooth transitions (0.3s ease)
- Support `prefers-reduced-motion`

#### 4. Color Palette Sentinel Official
- Base: `#0E141C` (softer than previous `#0B0F14`)
- Accents: `#4a90e2`, `#5ba3f5`
- Text: `#e8eaed`, `#c5cdd5`, `#8a8f9a`
- Backgrounds: Gradients + radial overlays

---

## 📁 Assets Structure (Ready for Content)

### Directory: `/assets/cinematic/`

```
/assets/cinematic/
├── README.md ✅ (Guide complet + Prompt IA canonique)
├── .gitkeep ✅
├── hero-soldier.webp ⏳ (À créer)
├── command-center.webp ⏳ (À créer)
├── global-monitoring.webp ⏳ (Optionnel)
└── hero-background.mp4 ⏳ (Optionnel)
```

### Références Canoniques (Locked Character)

**Image Canon:** https://sora.chatgpt.com/g/gen_01kcghfxn4fw49xt0zz2s30qga
**Vidéo Canon:** https://sora.chatgpt.com/g/gen_01kcgjnp4pf869wrek64ncjfca

**Règles strictes:**
- Même opérateur Sentinel dans TOUS les assets
- Casque, visière ambrée, armure: IDENTIQUES
- Photoréaliste uniquement
- Style militaire tactique futuriste
- Aucune reconception, aucune variation

---

## 🚀 Next Steps pour compléter Phase 2

### Option A: Génération IA (Recommandé)

1. **Utiliser Sora / DALL-E / Midjourney**
   - Suivre le prompt maître dans `/assets/cinematic/README.md`
   - Générer 3-5 variations de scènes:
     - Opérateur dans command center
     - Opérateur devant écrans monitoring
     - Opérateur en posture défensive
     - Close-up casque/visière
   
2. **Format requis:**
   - Images: WebP (compression optimale)
   - Résolution: 1920x1080 min
   - Vidéo (optionnelle): MP4 H.264, 10-15s loop

3. **Nommage:**
   - `hero-soldier.webp` - Hero principal
   - `command-center.webp` - SOC background
   - `global-monitoring.webp` - Threat Intel background
   - `hero-background.mp4` - Video loop (optionnel)

### Option B: Assets temporaires (Placeholder)

Si génération IA non disponible immédiatement:

1. **Utiliser SVG patterns actuels** (déjà en place)
2. **Gradients cinématiques** (déjà stylés)
3. **Focus sur animations et UX** (déjà optimisé)

### Option C: Assets libres de droits

Chercher sur:
- Unsplash (mots-clés: cybersecurity, command center, futuristic military)
- Pexels (chercher: tactical, monitoring, security operations)

**Attention:** Vérifier licences et cohérence visuelle

---

## 🎬 Integration Instructions

### Pour ajouter vidéo hero:

```html
<!-- Dans index.html, section .hero -->
<video class="hero-video" autoplay muted loop playsinline>
  <source src="/assets/cinematic/hero-background.mp4" type="video/mp4">
</video>
```

### Pour ajouter images modules:

```css
/* Dans sections modules */
.module-card {
  background-image: 
    linear-gradient(rgba(0,0,0,0.5), rgba(0,0,0,0.7)),
    url('/assets/cinematic/command-center.webp');
  background-size: cover;
  background-position: center;
}
```

### Performance Optimization

```html
<!-- Lazy loading -->
<img src="placeholder.jpg" 
     data-src="/assets/cinematic/hero-soldier.webp"
     loading="lazy"
     alt="Sentinel Operator">

<!-- Video avec fallback -->
<video class="hero-video" 
       poster="/assets/cinematic/hero-soldier.webp"
       autoplay muted loop playsinline>
  <source src="/assets/cinematic/hero-background.mp4" type="video/mp4">
</video>
```

---

## 📊 Performance Checklist

- [ ] Images WebP < 500KB chacune
- [ ] Vidéo MP4 < 5MB (si utilisée)
- [ ] Lazy loading implémenté
- [ ] `prefers-reduced-motion` respecté
- [ ] Fallbacks SVG fonctionnels
- [ ] Mobile-first responsive

---

## 🎯 Impact UX Attendu

### Avant (Actuel)
- Hero avec SVG pattern générique ✅
- Glassmorphism léger ✅
- Animations subtiles ✅

### Après (Avec Assets)
- Hero avec vidéo opérateur Sentinel 🎥
- Images photoréalistes modules 📸
- Immersion cinématique totale 🎬
- Crédibilité institutionnelle renforcée 💼

---

## 🔒 Compliance

### Exigences respectées:
- ✅ Pas d'emojis dans visuels
- ✅ Style photoréaliste uniquement
- ✅ Palette sombre maîtrisée
- ✅ Terminologie professionnelle
- ✅ Aucune promesse irréaliste
- ✅ Mobile-optimized
- ✅ Performance-conscious

### Règles strictes:
- ❌ Aucune illustration cartoon
- ❌ Aucune couleur vive/flashy
- ❌ Aucun élément fantaisiste
- ❌ Aucune reconception du personnage canon

---

## 📝 Notes Techniques

### Theme Toggle Integration

Le système de thème est déjà prêt:
```javascript
// Déjà implémenté dans index.html
function setTheme(theme) {
  document.body.className = `theme-${theme}`;
  localStorage.setItem('sentinel-theme', theme);
}
```

Thèmes disponibles:
- `theme-cinematic` - Avec vidéos/images (par défaut)
- `theme-glass` - Glassmorphism pur sans vidéo

### Cinematic Mode Script

```javascript
// Détection préférences utilisateur
const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

if (prefersReducedMotion.matches) {
  // Désactiver vidéo, garder image statique
  document.querySelector('.hero-video')?.remove();
}
```

---

## ✅ Validation

### Tests requis après ajout assets:

1. **Performance**
   - Lighthouse score > 85
   - First Contentful Paint < 2s
   - Largest Contentful Paint < 3s

2. **Responsive**
   - Mobile (320px-768px): Hero adapté
   - Tablet (769px-1024px): Full background
   - Desktop (1025px+): Cinematic complet

3. **Accessibility**
   - Alt text sur toutes images
   - Video avec poster fallback
   - Contraste texte/background > 4.5:1

4. **Browser Support**
   - Chrome/Edge: Full support
   - Firefox: Full support
   - Safari: Fallback si backdrop-filter non supporté
   - Mobile browsers: Video autoplay mobile-safe

---

## 🎬 Conclusion

**Phase 2 Infrastructure: 100% Complete ✅**

**Assets Creation: En attente**

Le code est production-ready. Dès que les assets conformes sont disponibles:
1. Placer fichiers dans `/assets/cinematic/`
2. Tester performance
3. Valider responsive
4. Deploy

**Recommandation:** Générer assets via IA (Sora/DALL-E) en suivant prompt maître canonique.

---

## 📞 Support

Pour questions techniques:
- Infrastructure: Voir ce document
- Assets guidelines: `/assets/cinematic/README.md`
- Character canon: Références Sora (locked)
- Prompt IA: Inclus dans README cinematic

**Status:** Ready for assets integration 🚀

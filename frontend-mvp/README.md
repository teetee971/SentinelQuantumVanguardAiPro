# Frontend MVP - Comparateur de Prix DOM/Métropole

Application web de comparaison de prix entre la Métropole et les DOM (Départements d'Outre-Mer).

## Stack Technique

- **Framework**: Vite + React 19
- **Routing**: React Router DOM  
- **Styling**: CSS vanilla (Mobile First)
- **Déploiement**: Cloudflare Pages
- **Backend API**: Via variable d'environnement VITE_API_BASE_URL

## Installation

```bash
npm install
npm run dev
```

## Pages MVP

1. **Accueil (/)** - Recherche et présentation
2. **Connexion (/connexion)** - Authentication
3. **Produits (/produits)** - Résultats de recherche
4. **Résultats (/resultats/:productId)** - Comparaison des prix
5. **Profil (/profil)** - Gestion utilisateur

## Déploiement Cloudflare Pages

Build command: `npm run build`  
Output directory: `dist`  
Root directory: `frontend-mvp`

## License

MIT

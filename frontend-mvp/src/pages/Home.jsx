/**
 * Home Page (Accueil)
 * Landing page with product search
 */

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './Home.css';

export default function Home() {
  const [searchQuery, setSearchQuery] = useState('');
  const navigate = useNavigate();

  const handleSearch = (e) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/produits?q=${encodeURIComponent(searchQuery)}`);
    }
  };

  return (
    <div className="home-page">
      <header className="home-header">
        <h1>Comparateur de Prix</h1>
        <p>Comparez les prix entre la Métropole et les DOM</p>
      </header>

      <section className="search-section">
        <form onSubmit={handleSearch} className="search-form">
          <input
            type="text"
            placeholder="Rechercher un produit..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="search-input"
            autoFocus
          />
          <button type="submit" className="search-button">
            Rechercher
          </button>
        </form>
      </section>

      <section className="features">
        <div className="feature-card">
          <h3>🔍 Recherche</h3>
          <p>Trouvez rapidement vos produits</p>
        </div>
        <div className="feature-card">
          <h3>💰 Prix</h3>
          <p>Comparez par enseigne</p>
        </div>
        <div className="feature-card">
          <h3>📊 Écarts</h3>
          <p>Visualisez les différences</p>
        </div>
        <div className="feature-card">
          <h3>🌍 Régions</h3>
          <p>DOM et Métropole</p>
        </div>
      </section>
    </div>
  );
}

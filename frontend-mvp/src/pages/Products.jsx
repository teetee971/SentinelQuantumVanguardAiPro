/**
 * Products Page (Produits)
 * Display and search products
 */

import { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import apiService from '../services/api';
import './Products.css';

export default function Products() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [searchQuery, setSearchQuery] = useState(searchParams.get('q') || '');
  const [selectedRegion, setSelectedRegion] = useState(user?.region || 'metropole');

  useEffect(() => {
    const query = searchParams.get('q');
    if (query) {
      searchProducts(query);
    } else {
      setLoading(false);
    }
  }, [searchParams]);

  const searchProducts = async (query) => {
    setLoading(true);
    setError('');
    try {
      const results = await apiService.searchProducts(query, selectedRegion);
      setProducts(results.products || []);
    } catch (err) {
      setError(err.message || 'Erreur lors de la recherche');
      setProducts([]);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = (e) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/produits?q=${encodeURIComponent(searchQuery)}`);
    }
  };

  const handleProductClick = (productId) => {
    navigate(`/resultats/${productId}?region=${selectedRegion}`);
  };

  return (
    <div className="products-page">
      <div className="products-header">
        <h1>Recherche de Produits</h1>
        
        <form onSubmit={handleSearch} className="search-bar">
          <input
            type="text"
            placeholder="Rechercher un produit..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="search-input"
          />
          <button type="submit" className="search-button">
            Rechercher
          </button>
        </form>

        <div className="region-selector">
          <label htmlFor="region">Région :</label>
          <select
            id="region"
            value={selectedRegion}
            onChange={(e) => setSelectedRegion(e.target.value)}
          >
            <option value="metropole">Métropole</option>
            <option value="guadeloupe">Guadeloupe</option>
            <option value="martinique">Martinique</option>
            <option value="guyane">Guyane</option>
            <option value="reunion">Réunion</option>
            <option value="mayotte">Mayotte</option>
          </select>
        </div>
      </div>

      {loading ? (
        <div className="loading">Chargement...</div>
      ) : error ? (
        <div className="error">{error}</div>
      ) : products.length === 0 && searchParams.get('q') ? (
        <div className="no-results">
          <p>Aucun produit trouvé pour "{searchParams.get('q')}"</p>
        </div>
      ) : products.length > 0 ? (
        <div className="products-grid">
          {products.map((product) => (
            <div
              key={product.id}
              className="product-card"
              onClick={() => handleProductClick(product.id)}
            >
              {product.image && (
                <img src={product.image} alt={product.name} className="product-image" />
              )}
              <div className="product-info">
                <h3>{product.name}</h3>
                <p className="product-category">{product.category}</p>
                <div className="product-price">
                  <span className="price-label">À partir de</span>
                  <span className="price-value">{product.minPrice?.toFixed(2)} €</span>
                </div>
                <button className="compare-button">
                  Comparer les prix →
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="welcome-message">
          <p>Recherchez un produit pour commencer</p>
        </div>
      )}
    </div>
  );
}

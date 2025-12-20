/**
 * Results/Comparison Page (Résultats)
 * Display price comparison by retailer and highlight price differences
 */

import { useState, useEffect } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import apiService from '../services/api';
import './Results.css';

export default function Results() {
  const { productId } = useParams();
  const [searchParams] = useSearchParams();
  const region = searchParams.get('region') || 'metropole';

  const [product, setProduct] = useState(null);
  const [prices, setPrices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [comparisonRegion, setComparisonRegion] = useState('metropole');
  const [comparisonPrices, setComparisonPrices] = useState([]);

  useEffect(() => {
    loadProductData();
  }, [productId, region]);

  const loadProductData = async () => {
    setLoading(true);
    setError('');
    try {
      const [productData, pricesData] = await Promise.all([
        apiService.getProduct(productId),
        apiService.getProductPrices(productId, region),
      ]);
      
      setProduct(productData);
      setPrices(pricesData.prices || []);
    } catch (err) {
      setError(err.message || 'Erreur lors du chargement des données');
    } finally {
      setLoading(false);
    }
  };

  const loadComparisonPrices = async (compareRegion) => {
    try {
      const data = await apiService.getProductPrices(productId, compareRegion);
      setComparisonPrices(data.prices || []);
    } catch (err) {
      console.error('Error loading comparison prices:', err);
      setComparisonPrices([]);
    }
  };

  useEffect(() => {
    if (comparisonRegion !== region && comparisonRegion) {
      loadComparisonPrices(comparisonRegion);
    } else {
      setComparisonPrices([]);
    }
  }, [comparisonRegion, region]);

  const calculatePriceDifference = (price1, price2) => {
    if (!price1 || !price2) return null;
    const diff = price1 - price2;
    const percentage = ((diff / price2) * 100).toFixed(1);
    return { diff: diff.toFixed(2), percentage };
  };

  const getLowestPrice = () => {
    if (prices.length === 0) return null;
    return Math.min(...prices.map((p) => p.price));
  };

  const getHighestPrice = () => {
    if (prices.length === 0) return null;
    return Math.max(...prices.map((p) => p.price));
  };

  const regionNames = {
    metropole: 'Métropole',
    guadeloupe: 'Guadeloupe',
    martinique: 'Martinique',
    guyane: 'Guyane',
    reunion: 'Réunion',
    mayotte: 'Mayotte',
  };

  if (loading) {
    return <div className="results-page loading">Chargement...</div>;
  }

  if (error) {
    return <div className="results-page error">{error}</div>;
  }

  if (!product) {
    return <div className="results-page error">Produit non trouvé</div>;
  }

  const lowestPrice = getLowestPrice();
  const highestPrice = getHighestPrice();
  const priceRange = highestPrice && lowestPrice ? highestPrice - lowestPrice : 0;

  return (
    <div className="results-page">
      <div className="product-header">
        <div className="product-main">
          {product.image && (
            <img src={product.image} alt={product.name} className="product-image" />
          )}
          <div className="product-details">
            <h1>{product.name}</h1>
            <p className="product-category">{product.category}</p>
            <div className="price-summary">
              <div className="price-stat">
                <span className="label">Prix minimum</span>
                <span className="value">{lowestPrice?.toFixed(2)} €</span>
              </div>
              <div className="price-stat">
                <span className="label">Prix maximum</span>
                <span className="value">{highestPrice?.toFixed(2)} €</span>
              </div>
              <div className="price-stat highlight">
                <span className="label">Écart de prix</span>
                <span className="value">{priceRange.toFixed(2)} €</span>
              </div>
            </div>
          </div>
        </div>

        <div className="region-info">
          <strong>Région actuelle :</strong> {regionNames[region]}
        </div>
      </div>

      <div className="comparison-section">
        <h2>Prix par Enseigne</h2>
        
        <div className="comparison-controls">
          <label htmlFor="compare-region">Comparer avec :</label>
          <select
            id="compare-region"
            value={comparisonRegion}
            onChange={(e) => setComparisonRegion(e.target.value)}
          >
            <option value="">-- Sélectionner une région --</option>
            {Object.entries(regionNames)
              .filter(([key]) => key !== region)
              .map(([key, name]) => (
                <option key={key} value={key}>
                  {name}
                </option>
              ))}
          </select>
        </div>

        {prices.length === 0 ? (
          <div className="no-prices">Aucun prix disponible pour cette région</div>
        ) : (
          <div className="prices-table">
            <div className="table-header">
              <div className="col-retailer">Enseigne</div>
              <div className="col-price">{regionNames[region]}</div>
              {comparisonPrices.length > 0 && (
                <>
                  <div className="col-price">{regionNames[comparisonRegion]}</div>
                  <div className="col-diff">Différence</div>
                </>
              )}
            </div>
            {prices.map((priceData) => {
              const compPrice = comparisonPrices.find(
                (cp) => cp.retailer === priceData.retailer
              );
              const diff = compPrice
                ? calculatePriceDifference(priceData.price, compPrice.price)
                : null;

              return (
                <div key={priceData.retailer} className="table-row">
                  <div className="col-retailer">
                    <strong>{priceData.retailer}</strong>
                  </div>
                  <div className="col-price">
                    <span className="price">{priceData.price.toFixed(2)} €</span>
                  </div>
                  {comparisonPrices.length > 0 && (
                    <>
                      <div className="col-price">
                        {compPrice ? (
                          <span className="price">{compPrice.price.toFixed(2)} €</span>
                        ) : (
                          <span className="no-data">--</span>
                        )}
                      </div>
                      <div className="col-diff">
                        {diff ? (
                          <div className={`diff ${diff.diff > 0 ? 'higher' : 'lower'}`}>
                            <span className="diff-amount">
                              {diff.diff > 0 ? '+' : ''}
                              {diff.diff} €
                            </span>
                            <span className="diff-percent">
                              ({diff.diff > 0 ? '+' : ''}
                              {diff.percentage}%)
                            </span>
                          </div>
                        ) : (
                          <span className="no-data">--</span>
                        )}
                      </div>
                    </>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {comparisonPrices.length > 0 && (
        <div className="vie-chere-info">
          <h3>💡 Comprendre les écarts de prix (Vie chère)</h3>
          <p>
            Les écarts de prix entre la Métropole et les DOM (Départements d'Outre-Mer) 
            s'expliquent par plusieurs facteurs :
          </p>
          <ul>
            <li>Coûts de transport et d'acheminement des marchandises</li>
            <li>Taxes locales spécifiques (octroi de mer)</li>
            <li>Marchés plus restreints avec moins de concurrence</li>
            <li>Coûts de stockage et logistique plus élevés</li>
          </ul>
          <p>
            <strong>Les prix affichés sont indicatifs et peuvent varier selon les magasins.</strong>
          </p>
        </div>
      )}
    </div>
  );
}

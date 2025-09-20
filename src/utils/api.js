/**
 * Utility functions for external API integrations
 * Part of Sentinel Quantum Vanguard AI Pro
 */

const BASE = "https://data.economie.gouv.fr/api/records/1.0/search/";

/**
 * Fetch pricing data from French government open data API
 * @param {string} produit - Product name to search for
 * @param {string} territoire - Territory/region to filter by
 * @returns {Promise<Array>} Array of pricing records with enseigne, prix, date
 */
export async function fetchPrixDOM(produit, territoire) {
  const url = `${BASE}?dataset=opmr-prix&q=${encodeURIComponent(produit)}&refine.territoire=${encodeURIComponent(territoire)}&rows=10`;
  try {
    const res = await fetch(url);
    const data = await res.json();
    return data.records.map(r => ({
      enseigne: r.fields.enseigne,
      prix: r.fields.prix,
      date: r.fields.date,
    }));
  } catch (e) {
    console.error("Erreur OPMR:", e);
    return [];
  }
}
// OPMR Price Monitor Module
// Observatoire des prix et des marges de la distribution
// Monitoring prices in French overseas territories (DOM)

const BASE = "https://data.economie.gouv.fr/api/records/1.0/search/";

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

// Utility function to get available territories
export async function getAvailableTerritoires() {
  const url = `${BASE}?dataset=opmr-prix&facet=territoire&rows=0`;
  try {
    const res = await fetch(url);
    const data = await res.json();
    return data.facet_groups?.[0]?.facets?.map(f => f.name) || [];
  } catch (e) {
    console.error("Erreur lors de la récupération des territoires:", e);
    return [];
  }
}

// Utility function to search products
export async function searchProducts(searchTerm) {
  const url = `${BASE}?dataset=opmr-prix&q=${encodeURIComponent(searchTerm)}&facet=produit&rows=0`;
  try {
    const res = await fetch(url);
    const data = await res.json();
    return data.facet_groups?.[0]?.facets?.map(f => f.name) || [];
  } catch (e) {
    console.error("Erreur lors de la recherche de produits:", e);
    return [];
  }
}
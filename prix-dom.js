// Prix DOM - Module de surveillance des prix via l'API OPMR
// Observatoire des prix et des marges des produits alimentaires

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
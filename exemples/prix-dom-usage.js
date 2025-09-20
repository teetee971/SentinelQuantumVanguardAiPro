// Exemple d'utilisation du module Prix DOM
// Surveillance des prix dans les territoires d'outre-mer

import { fetchPrixDOM } from '../prix-dom.js';

// Exemple 1: Recherche simple
async function exempleRecherchePrix() {
    try {
        const prix = await fetchPrixDOM('pain', 'Martinique');
        console.log('Prix du pain en Martinique:', prix);
    } catch (error) {
        console.error('Erreur lors de la recherche:', error);
    }
}

// Exemple 2: Surveillance de plusieurs produits
async function surveillanceMultipleProduits() {
    const produits = ['lait', 'pain', 'riz'];
    const territoires = ['Martinique', 'Guadeloupe', 'Guyane', 'Réunion'];
    
    for (const territoire of territoires) {
        console.log(`\n📍 Territoire: ${territoire}`);
        for (const produit of produits) {
            const prix = await fetchPrixDOM(produit, territoire);
            if (prix.length > 0) {
                const prixMoyen = prix.reduce((sum, item) => sum + parseFloat(item.prix || 0), 0) / prix.length;
                console.log(`${produit}: ${prix.length} prix trouvés, moyenne: ${prixMoyen.toFixed(2)}€`);
            } else {
                console.log(`${produit}: Aucun prix trouvé`);
            }
        }
    }
}

// Exemple 3: Alertes sur les variations de prix
async function alerteVariationPrix(produit, territoire, seuilVariation = 0.1) {
    try {
        const prix = await fetchPrixDOM(produit, territoire);
        
        if (prix.length < 2) {
            console.log('Pas assez de données pour détecter des variations');
            return;
        }
        
        const prixRecents = prix.sort((a, b) => new Date(b.date) - new Date(a.date));
        const prixMin = Math.min(...prixRecents.map(p => parseFloat(p.prix || 0)));
        const prixMax = Math.max(...prixRecents.map(p => parseFloat(p.prix || 0)));
        
        const variation = (prixMax - prixMin) / prixMin;
        
        if (variation > seuilVariation) {
            console.log(`🚨 ALERTE: Variation importante de prix détectée pour ${produit} en ${territoire}`);
            console.log(`📊 Écart: ${(variation * 100).toFixed(1)}% (${prixMin}€ - ${prixMax}€)`);
        } else {
            console.log(`✅ Prix stables pour ${produit} en ${territoire}`);
        }
    } catch (error) {
        console.error('Erreur lors de l\'analyse des variations:', error);
    }
}

// Export des fonctions d'exemple
export {
    exempleRecherchePrix,
    surveillanceMultipleProduits,
    alerteVariationPrix
};

// Exécution automatique si le script est lancé directement
if (import.meta.url === `file://${process.argv[1]}`) {
    console.log('🔍 Démonstration du module Prix DOM\n');
    
    await exempleRecherchePrix();
    console.log('\n' + '='.repeat(50));
    
    await surveillanceMultipleProduits();
    console.log('\n' + '='.repeat(50));
    
    await alerteVariationPrix('lait', 'Martinique');
}
// Test script for OPMR Price Monitor
// This script tests the fetchPrixDOM function and related utilities

import { fetchPrixDOM, getAvailableTerritoires, searchProducts } from './src/modules/opmrPriceMonitor.js';

console.log('🧪 Testing OPMR Price Monitor Module');

async function runTests() {
  console.log('\n1. Testing getAvailableTerritoires...');
  try {
    const territoires = await getAvailableTerritoires();
    console.log('✅ Territoires disponibles:', territoires.length > 0 ? territoires.slice(0, 5) : 'Aucun');
  } catch (error) {
    console.error('❌ Erreur territoires:', error.message);
  }

  console.log('\n2. Testing searchProducts...');
  try {
    const products = await searchProducts('pain');
    console.log('✅ Produits trouvés pour "pain":', products.length > 0 ? products.slice(0, 3) : 'Aucun');
  } catch (error) {
    console.error('❌ Erreur produits:', error.message);
  }

  console.log('\n3. Testing fetchPrixDOM with sample data...');
  try {
    // Test with a common product and territory
    const prices = await fetchPrixDOM('pain', 'Martinique');
    console.log('✅ Prix trouvés pour "pain" en "Martinique":', prices.length, 'résultats');
    if (prices.length > 0) {
      console.log('Exemple:', prices[0]);
    }
  } catch (error) {
    console.error('❌ Erreur fetchPrixDOM:', error.message);
  }

  console.log('\n4. Testing fetchPrixDOM with encoded parameters...');
  try {
    // Test with special characters
    const prices = await fetchPrixDOM('pain de mie', 'Guadeloupe');
    console.log('✅ Prix trouvés pour "pain de mie" en "Guadeloupe":', prices.length, 'résultats');
  } catch (error) {
    console.error('❌ Erreur avec caractères spéciaux:', error.message);
  }

  console.log('\n5. Testing error handling with invalid parameters...');
  try {
    const prices = await fetchPrixDOM('', '');
    console.log('✅ Gestion d\'erreur OK:', prices.length, 'résultats');
  } catch (error) {
    console.log('✅ Erreur capturée comme attendu:', error.message);
  }

  console.log('\n🏁 Tests terminés');
}

// Run tests if this file is executed directly
if (typeof window === 'undefined') {
  runTests();
}

export { runTests };
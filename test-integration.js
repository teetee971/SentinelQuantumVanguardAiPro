// Basic integration test for OPMR module
// Tests that the module can be imported and functions are available

async function testOPMRIntegration() {
  console.log('🔬 Testing OPMR Module Integration...');
  
  try {
    // Test 1: Module can be imported
    const module = await import('./src/modules/opmrPriceMonitor.js');
    console.log('✅ Module imported successfully');
    
    // Test 2: Functions are available
    const { fetchPrixDOM, getAvailableTerritoires, searchProducts } = module;
    
    if (typeof fetchPrixDOM === 'function') {
      console.log('✅ fetchPrixDOM function available');
    } else {
      console.error('❌ fetchPrixDOM function missing');
    }
    
    if (typeof getAvailableTerritoires === 'function') {
      console.log('✅ getAvailableTerritoires function available');
    } else {
      console.error('❌ getAvailableTerritoires function missing');
    }
    
    if (typeof searchProducts === 'function') {
      console.log('✅ searchProducts function available');
    } else {
      console.error('❌ searchProducts function missing');
    }
    
    // Test 3: Error handling works (with invalid data)
    const result = await fetchPrixDOM('', '');
    if (Array.isArray(result)) {
      console.log('✅ Error handling works - returns array:', result.length, 'items');
    } else {
      console.error('❌ Error handling failed - should return array');
    }
    
    console.log('🎉 Integration test completed');
    return true;
    
  } catch (error) {
    console.error('❌ Integration test failed:', error.message);
    return false;
  }
}

// Run test if this file is executed directly
if (typeof window === 'undefined') {
  testOPMRIntegration().then(success => {
    process.exit(success ? 0 : 1);
  });
}

export { testOPMRIntegration };
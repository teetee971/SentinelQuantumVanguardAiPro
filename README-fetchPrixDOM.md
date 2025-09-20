# fetchPrixDOM Function Implementation

## Overview
The `fetchPrixDOM` function has been successfully implemented and integrated into the Sentinel Quantum Vanguard AI Pro application. This function fetches pricing data from the French government's open data API (data.economie.gouv.fr).

## Function Details

### Location
- **File**: `public/assets/api-utils.js`
- **Also available in**: `src/utils/api.js` (for React/module-based usage)

### Function Signature
```javascript
async function fetchPrixDOM(produit, territoire)
```

### Parameters
- `produit` (string): Product name to search for (e.g., "carburant", "essence")
- `territoire` (string): Territory/region to filter by (e.g., "Martinique", "Guadeloupe")

### Return Value
Returns a Promise that resolves to an array of objects with the following structure:
```javascript
[
  {
    enseigne: "Store/Brand name",
    prix: "Price value",
    date: "Date of record"
  },
  // ... more records
]
```

### Error Handling
- Uses try-catch to handle API errors
- Logs errors to console with "Erreur OPMR:" prefix
- Returns empty array `[]` on error

## Usage Examples

### Direct Function Call
```javascript
// Using the global function
const data = await window.fetchPrixDOM("essence", "Martinique");
console.log(data);
```

### ES Module Import
```javascript
// In a module environment
import { fetchPrixDOM } from './utils/api.js';
const data = await fetchPrixDOM("carburant", "Guadeloupe");
```

## UI Integration
The function is integrated into the main application with:
- Search form in the "Intelligence Économique - Prix DOM" section
- Input fields for product and territory
- Results display with styled cards
- Error messages and loading states

## Testing
A test file is available at `public/assets/test-prix-dom.js` that verifies:
- Function availability
- Parameter structure
- Error handling implementation
- Data mapping correctness

## API Endpoint
The function queries: `https://data.economie.gouv.fr/api/records/1.0/search/`
With parameters:
- `dataset=opmr-prix`
- `q={encoded_product}`
- `refine.territoire={encoded_territory}`
- `rows=10`

## Notes
- The function uses `encodeURIComponent` for safe URL parameter encoding
- CORS restrictions may prevent direct browser access to the API in development
- The function is designed to be robust and will return an empty array if the API is unavailable
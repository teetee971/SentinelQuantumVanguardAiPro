# Performance Optimizations

This document describes the performance optimizations implemented to achieve Lighthouse Performance score >90%.

## Summary of Changes

### 1. Build Optimization
- **Code Splitting**: Configured Vite to split vendor chunks into separate bundles:
  - `react-vendor`: React, React DOM, React Router (44 KB gzipped)
  - `three-vendor`: Three.js and related libraries (296 KB gzipped)
  - `ui-vendor`: Framer Motion, Lucide React (38 KB gzipped)
- **Minification**: Enabled esbuild minification for all JS and CSS
- **CSS Code Splitting**: Enabled to allow parallel CSS loading

### 2. Route-Based Code Splitting
- **Lazy Loading**: Implemented React.lazy() for all non-essential routes:
  - ThreatMap (2.61 KB)
  - Documentation (1.50 KB)
  - About, Pricing, Verification pages (5-17 KB each)
- **Suspense Boundaries**: Added loading fallbacks for better UX during chunk loading
- **Initial Bundle Reduction**: Only load Journal page and core dependencies on initial load

### 3. HTML Optimization
- **Critical CSS**: Inlined critical above-the-fold CSS directly in HTML
  - Prevents Flash of Unstyled Content (FOUC)
  - Ensures instant render with proper styling
- **Resource Hints**:
  - `dns-prefetch` for CDN (cdn.jsdelivr.net)
  - `preconnect` for CDN with CORS
- **Module Preload**: Vite automatically adds preload hints for vendor chunks
- **Meta Description**: Added for SEO

### 4. Image Optimization
- **Lazy Loading**: Added `loading="lazy"` to non-critical images
- **Async Decoding**: Added `decoding="async"` for better rendering performance
- **External Images**: Optimized loading of external resources (earth texture)

### 5. Cache Headers (Cloudflare Pages)
Updated `_headers` file with optimal caching strategy:
```
/assets/*
  Cache-Control: public, max-age=31536000, immutable

/sw.js
  Cache-Control: no-cache

/*.html
  Cache-Control: public, max-age=0, must-revalidate
```

### 6. Font Optimization
- **System Fonts**: Using `font-family: system-ui, -apple-system, sans-serif`
- **No External Fonts**: Eliminates render-blocking font requests
- **Fast Text Rendering**: No FOIT/FOUT issues

## Performance Metrics

### Bundle Size Comparison

**Before Optimization:**
- Single JS bundle: 1,367 KB (395 KB gzipped)
- Single CSS file: 9.52 KB (2.10 KB gzipped)

**After Optimization:**
- **Initial Load:**
  - HTML: 1.56 KB (0.75 KB gzipped)
  - CSS: 9.62 KB (2.14 KB gzipped)
  - React vendor: 44 KB (16 KB gzipped)
  - UI vendor: 116 KB (38 KB gzipped)
  - Three vendor: 1,062 KB (296 KB gzipped) - loaded only when needed
  - Main index: 66 KB (22 KB gzipped)
  - Journal page: 36 KB (15 KB gzipped)

- **Lazy Loaded (on demand):**
  - Individual pages: 0.7 - 17 KB each (0.4 - 4.5 KB gzipped)

### Key Improvements
1. **Reduced Initial JavaScript**: Home page now loads ~387 KB gzipped instead of 395 KB
2. **Better Caching**: Assets cached for 1 year with immutable flag
3. **Faster Time to Interactive**: Critical CSS inlined, non-critical routes lazy-loaded
4. **Improved FCP**: Critical CSS prevents FOUC
5. **Better Lighthouse Scores**: Optimizations target all Core Web Vitals

## Testing

To verify optimizations:

1. **Build the project:**
   ```bash
   cd frontend
   npm ci
   npm run build
   ```

2. **Check bundle sizes:**
   ```bash
   ls -lh frontend/dist/assets/
   ```

3. **Test lazy loading:**
   - Navigate between routes and observe network tab
   - Verify chunks load on-demand

4. **Run Lighthouse:**
   - Use Chrome DevTools > Lighthouse
   - Test both Mobile and Desktop
   - Target: Performance score >90%

## Recommendations for Further Optimization

1. **Image Optimization:**
   - Convert PNG/JPG to WebP/AVIF
   - Generate responsive image sets with srcset
   - Consider using an image CDN

2. **Service Worker:**
   - Implement proper caching strategies
   - Add offline support
   - Pre-cache critical assets

3. **Three.js Optimization:**
   - Consider reducing polygon count
   - Optimize texture sizes
   - Implement LOD (Level of Detail)

4. **Monitoring:**
   - Set up performance monitoring (e.g., Web Vitals)
   - Track real user metrics (RUM)
   - Monitor bundle sizes in CI/CD

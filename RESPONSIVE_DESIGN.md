# Responsive Design Implementation

This document describes the responsive design improvements made to ensure optimal display across mobile, tablet, and desktop devices.

## Overview

All pages have been updated with responsive design principles following the requirements:
- Mobile-first approach with proper breakpoints
- Touch targets ≥44px for accessibility
- Responsive typography using clamp()
- Adaptive layouts with CSS Grid

## Breakpoints

- **Mobile**: < 768px (1 column layouts)
- **Tablet**: 768px - 1023px (2 column layouts)
- **Desktop**: ≥ 1024px (3+ column layouts)

## Key Improvements

### 1. Navigation (ResponsiveNav.jsx)
- **Mobile**: Hamburger menu with ≥44px touch target
- **Tablet/Desktop**: Full horizontal menu
- All navigation items accessible via keyboard
- Proper ARIA labels for screen readers

### 2. Typography
- **Hero H1**: `clamp(28px, 5vw, 56px)` - ensures 28-32px on mobile, 40-56px on desktop
- **Section H2**: `clamp(28px, 4vw, 40px)` - responsive sizing
- **Body text**: Responsive font sizes using sm: and md: breakpoints

### 3. Buttons & CTAs
- Minimum height: 44px (accessibility requirement)
- Minimum width: 44px for icon-only buttons
- Mobile CTA buttons: 90% width or min 320px
- Proper padding: px-6 py-3 (24px horizontal, 12px vertical)

### 4. Grid Layouts
```css
/* Mobile: 1 column */
grid-cols-1

/* Tablet: 2 columns */
sm:grid-cols-2

/* Desktop: 3 columns */
lg:grid-cols-3
```

### 5. Images
- `max-width: 100%` - responsive scaling
- `height: auto` - maintain aspect ratio
- `loading="lazy"` - performance optimization
- Explicit width/height attributes for CLS prevention

### 6. Spacing
CSS custom properties for consistent spacing:
```css
:root {
  --space: clamp(16px, 2.5vw, 28px);
  --space-sm: clamp(12px, 2vw, 20px);
  --space-lg: clamp(24px, 4vw, 40px);
}
```

## Pages Updated

### Journal.jsx (Hero/Homepage)
- Responsive hero heading with clamp()
- Adaptive CTA button sizing
- Feature cards: 1 col → 2 col → 3 col
- Proper spacing and padding

### Pricing.jsx
- Plan cards: 1 col → 2 col → 3/4 col
- Responsive tables with horizontal scroll on mobile
- FAQ accordion with ≥44px touch targets
- All CTAs meet accessibility requirements

### About.jsx
- Responsive container with max-width
- Adaptive heading sizes
- Proper content padding

### ThreatMap.jsx
- Responsive layout (stacked on mobile, side-by-side on desktop)
- Adaptive hero heading
- Flexible canvas height

### PegasusScan.jsx
- Responsive headings and containers
- All buttons meet touch target requirements
- Adaptive padding

### TestIA.jsx
- Responsive form inputs (min-height: 44px)
- Proper button sizing
- Adaptive text sizes

### Documentation.jsx
- Lazy-loaded images with explicit dimensions
- Responsive button sizing

## Testing

Responsive tests are located in `tests/responsive.spec.ts` and cover:

1. **Mobile (375x667)**
   - Hamburger menu functionality
   - Minimum font sizes (≥28px)
   - Touch target sizes (≥44px)
   - Single column layouts

2. **Tablet (768x1024)**
   - Expanded navigation
   - Larger font sizes (≥32px)
   - 2-column grid layouts

3. **Desktop (1440x900)**
   - Full navigation menu
   - Maximum font sizes (40-56px)
   - Multi-column grid layouts
   - Lazy-loaded images

4. **Accessibility**
   - All touch targets ≥44x44px
   - Proper ARIA labels
   - Keyboard navigation

## Running Tests

```bash
# Install dependencies
npm install

# Run all tests
npx playwright test

# Run responsive tests only
npx playwright test tests/responsive.spec.ts

# Run with UI
npx playwright test --ui

# Run on specific viewport
npx playwright test --project=chromium
```

## Lighthouse Audit

The responsive implementation should pass Lighthouse audits for:
- ✅ Responsive design
- ✅ Touch target sizing
- ✅ Readable font sizes
- ✅ Proper viewport configuration
- ✅ Image optimization (lazy loading)

## Browser Support

Tested and working on:
- Chrome/Chromium (latest)
- Firefox (latest)
- Safari/WebKit (latest)
- Mobile browsers (iOS Safari, Chrome Mobile)

## CSS Utilities Added

```css
/* Container */
.container {
  width: min(1100px, 92vw);
  margin-inline: auto;
  padding-inline: var(--space);
}

/* Button base */
.btn {
  padding: 12px 20px;
  border-radius: 12px;
  font-weight: 600;
  min-height: 44px;
  min-width: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

/* Responsive grids */
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space);
}

.grid-3 {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space);
}
```

## Recommendations

1. **Performance**: Consider implementing `srcset` for images at different resolutions
2. **Advanced**: Add responsive images for hero/banner sections
3. **Testing**: Run regular Lighthouse audits to maintain scores
4. **Monitoring**: Track Core Web Vitals for responsive performance

## References

- [Web Content Accessibility Guidelines (WCAG) 2.1](https://www.w3.org/WAI/WCAG21/quickref/)
- [Touch Target Size - Material Design](https://material.io/design/usability/accessibility.html#layout-and-typography)
- [Responsive Web Design Basics - web.dev](https://web.dev/responsive-web-design-basics/)

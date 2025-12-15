# Cinematic Mode Assets

This directory contains assets used only in Cinematic visual mode.

## Asset Requirements

### Background Video
- **File**: `cinematic-bg.mp4` (or .webm)
- **Format**: MP4 (H.264) or WebM (VP9)
- **Resolution**: 1920x1080 minimum
- **Duration**: 10-30 seconds (looped)
- **Content**: Tactical/military themed, grayscale or muted colors
- **Size**: < 5MB recommended for performance
- **Notes**: 
  - Must be muted (no audio)
  - Should be professional/institutional in nature
  - Will be displayed at 30% opacity with grayscale filter
  - Respects `prefers-reduced-motion` (won't load if user prefers reduced motion)

### Soldier/Operator Imagery
- **File**: `soldier-hero.jpg` (or .webp, .png)
- **Format**: JPEG, WebP, or PNG
- **Resolution**: 1200x800 minimum
- **Content**: Realistic military/security operator imagery
- **Style**: Professional, government-grade, no decorative elements
- **Notes**:
  - Must be rights-cleared for use
  - Should convey professionalism and security
  - Grayscale or muted color palette preferred
  - No emojis, no saturated colors

## Lazy Loading

All assets in this directory are **lazy loaded** only when:
1. User explicitly switches to Cinematic mode
2. JavaScript is enabled
3. Motion preferences allow (for video)

Assets are **never loaded** in Institutional mode (default).

## Design Constraints

- No emojis
- No saturated colors
- No decorative icons
- Government/defense compliant
- Professional and institutional appearance
- Realistic imagery only

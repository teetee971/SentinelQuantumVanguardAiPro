# PWA Icons Placeholder

This directory should contain PWA icons in the following sizes:
- icon-72x72.png
- icon-96x96.png
- icon-128x128.png
- icon-144x144.png
- icon-152x152.png
- icon-192x192.png
- icon-384x384.png
- icon-512x512.png

## Icon Design Guidelines

**Style**: Professional, institutional, cybersecurity-focused
**Colors**: 
- Primary: #4a90e2 (Sentinel Blue)
- Background: #1a2230 (Dark Background)
- Accent: #5ba3f5 (Light Blue)

**Content**: Shield or hexagon shape with "S" or full Sentinel logo

## Generation

Icons can be generated from the base SVG logo using tools like:
- ImageMagick: `convert icon.svg -resize 512x512 icon-512x512.png`
- Online tools: https://realfavicongenerator.net/
- Figma/Sketch export

## Temporary Solution

Until proper icons are designed, the PWA will still function but may show:
- Default browser icon
- No splash screen
- Basic install dialog

This does NOT prevent PWA installation, but reduces visual polish.

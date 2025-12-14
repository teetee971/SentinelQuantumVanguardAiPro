#!/bin/bash
# Script to copy static site to Android assets

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_ROOT/android/app/src/main/assets/www"

echo "🔧 Copying static site to Android assets..."

# Create assets directory
mkdir -p "$ASSETS_DIR"

# Copy HTML files
cp "$PROJECT_ROOT"/*.html "$ASSETS_DIR/" 2>/dev/null || true

# Copy public directory
if [ -d "$PROJECT_ROOT/public" ]; then
    cp -r "$PROJECT_ROOT/public" "$ASSETS_DIR/"
    echo "✅ Copied public/"
fi

# Copy assets directory
if [ -d "$PROJECT_ROOT/assets" ]; then
    cp -r "$PROJECT_ROOT/assets" "$ASSETS_DIR/"
    echo "✅ Copied assets/"
fi

# Copy docs if needed
if [ -d "$PROJECT_ROOT/docs" ]; then
    cp -r "$PROJECT_ROOT/docs" "$ASSETS_DIR/"
    echo "✅ Copied docs/"
fi

echo "✅ Static site copied to Android assets successfully"
echo "📁 Location: $ASSETS_DIR"

# Count files
FILE_COUNT=$(find "$ASSETS_DIR" -type f | wc -l)
echo "📊 Total files: $FILE_COUNT"

ls -lh "$ASSETS_DIR"

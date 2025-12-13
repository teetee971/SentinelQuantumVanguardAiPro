#!/bin/bash

# Script to resolve phase-b merge conflicts with main
# This script automates the resolution process for PR #134

set -e  # Exit on error

echo "=== Phase B Merge Conflict Resolution Script ==="
echo ""
echo "This script will:"
echo "1. Checkout the phase-b branch"
echo "2. Merge main into phase-b using the 'theirs' strategy"
echo "3. Verify the merge was successful"
echo ""
echo "Press Ctrl+C to cancel, or Enter to continue..."
read

# Step 1: Ensure we're in a git repository
if [ ! -d ".git" ]; then
    echo "Error: Not in a git repository"
    exit 1
fi

# Step 2: Fetch latest changes
echo "Fetching latest changes from remote..."
git fetch origin

# Step 3: Checkout phase-b
echo "Checking out phase-b branch..."
git checkout phase-b

# Step 4: Ensure phase-b is up to date
echo "Pulling latest changes for phase-b..."
git pull origin phase-b || true

# Step 5: Ensure we have the latest main
echo "Fetching latest main..."
git fetch origin main:main || git fetch origin main

# Step 6: Perform the merge
echo ""
echo "Merging main into phase-b with 'theirs' strategy..."
echo "This will prefer main's version for all conflicts."
echo ""

if git merge main -X theirs --allow-unrelated-histories -m "Merge main into phase-b - resolve conflicts preferring main"; then
    echo ""
    echo "✅ Merge successful!"
    echo ""
    
    # Step 7: Verify no conflict markers remain
    echo "Verifying no conflict markers remain..."
    if git grep -l "<<<<<<< HEAD" -- '*.md' '*.html' '*.yml' '*.gradle' 2>/dev/null; then
        echo "❌ Warning: Conflict markers found! Please review manually."
        exit 1
    else
        echo "✅ No conflict markers found"
    fi
    
    # Step 8: Show summary
    echo ""
    echo "=== Merge Summary ==="
    git log --oneline -1
    echo ""
    echo "Files changed:"
    git diff --stat HEAD~1
    echo ""
    echo "=== Next Steps ==="
    echo "Review the changes with: git log --oneline --graph -10"
    echo "If everything looks good, push with: git push origin phase-b"
    echo "To undo this merge: git reset --hard HEAD~1"
    echo ""
else
    echo "❌ Merge failed with errors"
    echo "Current status:"
    git status
    echo ""
    echo "To abort: git merge --abort"
    exit 1
fi

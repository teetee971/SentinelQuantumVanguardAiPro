# Cloudflare Pages - Troubleshooting Guide

## ❌ Error: Output directory "frontend/dist" not found

### Problem
Cloudflare Pages deployment fails with:
```
Error: Output directory "frontend/dist" not found.
Failed: build output directory not found
```

### Root Cause
The Cloudflare Pages UI has the output directory **locked/grayed out** to `frontend/dist`, but this is a static site served from the root directory.

### Solution

#### ✅ Use wrangler.toml (AUTOMATIC - RECOMMENDED)

The repository includes a `wrangler.toml` file that **overrides the locked UI configuration**:

```toml
name = "sentinelquantumvanguardaipro"
pages_build_output_dir = "."
```

**How it works:**
- The `wrangler.toml` file forces Cloudflare Pages to use the root directory (`.`) as output
- This overrides any locked/grayed settings in the Cloudflare UI
- No build command is needed - the site is already static HTML/CSS/JS at the root
- No manual UI changes required

**Next deployment will automatically:**
1. Detect `wrangler.toml` in the repository
2. Ignore the locked `frontend/dist` setting
3. Serve content from the root directory
4. Deploy successfully

#### Option 2: Manual UI Update (if wrangler.toml doesn't work)

If the UI allows editing (not grayed out):

1. Log in to your Cloudflare dashboard
2. Navigate to **Pages** → Your project
3. Go to **Settings** → **Builds & deployments**
4. Click **Edit configuration**
5. Update the **Build output directory** to `.` (root directory)
6. Set **Build command** to empty
7. Click **Save**
8. Retry the deployment

**Correct Configuration for Static Site:**
```
Build command: (empty - no build needed)
Build output directory: .
Root directory: (leave empty)
```

---

## ✅ Verification

After fixing the configuration, verify the deployment:

1. **Check Deployment Logs**
   - The deployment should complete successfully
   - No build step should run (static site)
   - Content served directly from root directory

2. **Test the Deployed Site**
   - Homepage should load: `https://sentinelquantumvanguardaipro.pages.dev/`
   - All pages should be accessible
   - Check browser console for errors

3. **Verify Static Content**
   ```
   Root directory contains:
   ├── index.html
   ├── public/
   │   ├── about.html
   │   ├── glossary.html
   │   └── ... (other pages)
   ├── style.css
   └── ... (other static files)
   ```

---

## 🔧 Additional Troubleshooting

### Site Shows 404

**Problem:** Deployment succeeds but site shows "404 Not Found"

**Solution:**
1. Verify that `index.html` exists at the root of the repository
2. Check that `pages_build_output_dir = "."` is set in `wrangler.toml`
3. Clear Cloudflare cache and retry

### Assets Not Loading (CSS/JS missing)

**Problem:** Site loads but styles/scripts are broken

**Solution:**
1. Check browser console for 404 errors
2. Verify that asset paths in HTML are relative (not absolute)
3. Ensure all CSS/JS files are in the root or `public/` directory

---

## 📚 Related Documentation

- **Main Configuration:** `CLOUDFLARE_PAGES_CONFIG.md`
- **Wrangler Configuration:** `wrangler.toml`
- **Static Site Structure:** Root directory serves `index.html` and `public/` folder

---

## 🆘 Still Having Issues?

If you continue to experience problems:

1. **Check Cloudflare Build Logs**
   - Go to your project → Deployments
   - Click on the failed deployment
1. **Check Cloudflare Deployment Logs**
   - Go to your project → Deployments
   - Click on the failed deployment
   - Review the full deployment log

2. **Verify Static Site Structure**
   - Ensure `index.html` is at the root
   - Ensure `wrangler.toml` is at the root
   - Ensure `public/` directory contains additional pages
   - Ensure no `frontend/` or `dist/` directories are required

3. **Contact Support**
   - Cloudflare Pages Support: https://support.cloudflare.com/
   - Cloudflare Community: https://community.cloudflare.com/

---

**Last Updated:** 2024-12-14  
**Status:** ✅ Issue Resolved with wrangler.toml (Static Site Configuration)

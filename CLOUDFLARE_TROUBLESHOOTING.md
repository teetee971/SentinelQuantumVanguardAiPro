# Cloudflare Pages - Troubleshooting Guide

## ❌ Error: Output directory "frontend/dist" not found

### Problem
Cloudflare Pages deployment fails with:
```
Error: Output directory "frontend/dist" not found.
Failed: build output directory not found
```

### Root Cause
The Cloudflare Pages project is configured to look for `frontend/dist` as the build output directory, but the actual output directory is `dist` (not `frontend/dist`).

### Solution

#### Option 1: Update Cloudflare Pages Settings (RECOMMENDED)

1. Log in to your Cloudflare dashboard
2. Navigate to **Pages** → Your project
3. Go to **Settings** → **Builds & deployments**
4. Click **Edit configuration**
5. Update the **Build output directory** from `frontend/dist` to `dist`
6. Click **Save**
7. Retry the deployment

**Correct Configuration:**
```
Build command: npm install && npm run build
Build output directory: dist
Root directory: (leave empty)
Node.js version: 18.x or higher
```

#### Option 2: Use wrangler.toml (AUTOMATIC)

The repository now includes a `wrangler.toml` file that automatically configures the correct output directory:

```toml
name = "sentinel-quantum-vanguard-ai-pro"
compatibility_date = "2024-12-14"

[site]
bucket = "./dist"
```

This file should be automatically detected by Cloudflare Pages on the next deployment.

If the error persists after adding `wrangler.toml`:
1. Go to Cloudflare Pages settings
2. Verify that the project is detecting the `wrangler.toml` file
3. Manually update the build output directory to `dist` as described in Option 1

---

## ✅ Verification

After fixing the configuration, verify the deployment:

1. **Check Build Logs**
   - The build should complete successfully
   - You should see: `✓ built in Xms`
   - The `dist` directory should be created

2. **Test the Deployed Site**
   - Homepage should load: `https://[your-project].pages.dev/`
   - All pages should be accessible
   - Check browser console for errors

3. **Verify Build Output**
   ```
   dist/
   ├── index.html
   ├── about.html
   ├── glossary.html
   ├── style.css
   └── ... (other files)
   ```

---

## 🔧 Additional Troubleshooting

### Build Fails with "Command not found"

**Problem:** `npm: command not found` or similar errors

**Solution:** 
1. Go to Cloudflare Pages settings
2. Set **Node.js version** to `18.x` or higher
3. Retry the deployment

### Build Succeeds but Site Shows 404

**Problem:** Deployment succeeds but site shows "404 Not Found"

**Solution:**
1. Verify that `dist/index.html` exists after build
2. Check that the build output directory is set to `dist` (not `/dist` or `./dist`)
3. Clear Cloudflare cache and retry

### Assets Not Loading (CSS/JS missing)

**Problem:** Site loads but styles/scripts are broken

**Solution:**
1. Check browser console for 404 errors
2. Verify that asset paths in HTML are relative (not absolute)
3. Ensure `dist/` contains all required files (CSS, JS, images)

---

## 📚 Related Documentation

- **Main Configuration:** `CLOUDFLARE_PAGES_CONFIG.md`
- **Build Configuration:** `vite.config.js`
- **Package Configuration:** `package.json`

---

## 🆘 Still Having Issues?

If you continue to experience problems:

1. **Check Cloudflare Build Logs**
   - Go to your project → Deployments
   - Click on the failed deployment
   - Review the full build log

2. **Test Build Locally**
   ```bash
   npm install
   npm run build
   ls -la dist/
   ```
   If the local build works, the issue is in the Cloudflare configuration.

3. **Verify Repository Structure**
   - Ensure `package.json` is at the root
   - Ensure `vite.config.js` is at the root
   - Ensure `wrangler.toml` is at the root
   - Ensure no `frontend/` directory exists

4. **Contact Support**
   - Cloudflare Pages Support: https://support.cloudflare.com/
   - Cloudflare Community: https://community.cloudflare.com/

---

**Last Updated:** 2024-12-14  
**Status:** ✅ Issue Resolved with wrangler.toml

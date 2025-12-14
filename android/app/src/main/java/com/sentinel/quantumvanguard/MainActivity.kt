package com.sentinel.quantumvanguard

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Sentinel Quantum Vanguard AI Pro - Main Activity
 * 
 * Native Android WebView wrapper for the Sentinel static site
 * - Secure WebView configuration
 * - Bottom navigation menu
 * - No external data collection
 * - Local-first approach
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var bottomNav: BottomNavigationView
    
    private val baseUrl = "file:///android_asset/www/index.html"

    companion object {
        private const val TAG = "MainActivity"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Sentinel Quantum Vanguard"

        // Initialize WebView
        webView = findViewById(R.id.webview)
        setupWebView()

        // Initialize Bottom Navigation
        bottomNav = findViewById(R.id.bottom_navigation)
        setupBottomNavigation()

        // Load initial page
        loadPage(baseUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = false
                allowFileAccess = true
                allowContentAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                blockNetworkLoads = false  // Allow loading from CDN if needed
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            }

            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            
            // JavaScript interface for app communication (future use)
            addJavascriptInterface(SentinelJSInterface(this@MainActivity), "SentinelAndroid")
        }
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadPage("file:///android_asset/www/index.html")
                    true
                }
                R.id.nav_phone -> {
                    loadPage("file:///android_asset/www/public/telephone-security.html")
                    true
                }
                R.id.nav_soc -> {
                    loadPage("file:///android_asset/www/public/mobile-edr-soc.html")
                    true
                }
                R.id.nav_status -> {
                    loadPage("file:///android_asset/www/public/system-status.html")
                    true
                }
                R.id.nav_permissions -> {
                    loadPage("file:///android_asset/www/public/android-apk-official.html")
                    true
                }
                else -> false
            }
        }
    }

    private fun loadPage(url: String) {
        webView.loadUrl(url)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_about -> {
                showAboutDialog()
                true
            }
            R.id.menu_permissions -> {
                loadPage("file:///android_asset/www/public/android-apk-official.html")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAboutDialog() {
        Toast.makeText(
            this,
            "Sentinel Quantum Vanguard AI Pro v${SentinelApplication.VERSION}\n" +
                    "Defensive Cybersecurity Platform\n" +
                    "RGPD Compliant - No Spyware",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}

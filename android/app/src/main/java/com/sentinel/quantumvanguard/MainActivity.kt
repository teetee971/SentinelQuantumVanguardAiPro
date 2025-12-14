package com.sentinel.quantumvanguard

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.sentinel.quantumvanguard.activities.*
import com.sentinel.quantumvanguard.data.SecurityEvent
import com.sentinel.quantumvanguard.database.SentinelDatabase
import com.sentinel.quantumvanguard.repository.EventRepository
import kotlinx.coroutines.launch

/**
 * Sentinel Quantum Vanguard AI Pro - Main Activity
 * 
 * Native Android app with WebView + native security screens
 * - Secure WebView configuration
 * - Native bottom navigation
 * - Local event logging
 * - No external data collection
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var repository: EventRepository
    
    private val baseUrl = "file:///android_asset/www/index.html"

    companion object {
        private const val TAG = "MainActivity"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize database
        val database = SentinelDatabase.getDatabase(this)
        repository = EventRepository(database.eventDao())
        
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
        
        // Initialize with sample data
        initializeSampleData()
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
                blockNetworkLoads = false
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            }

            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            
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
                R.id.nav_dialer -> {
                    startActivity(Intent(this, DialerActivity::class.java))
                    true
                }
                R.id.nav_contacts -> {
                    startActivity(Intent(this, ContactsActivity::class.java))
                    true
                }
                R.id.nav_soc -> {
                    startActivity(Intent(this, SocMobileActivity::class.java))
                    true
                }
                R.id.nav_status -> {
                    startActivity(Intent(this, SystemStatusActivity::class.java))
                    true
                }
                R.id.nav_permissions -> {
                    startActivity(Intent(this, PermissionsManagerActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun loadPage(url: String) {
        webView.loadUrl(url)
    }
    
    private fun initializeSampleData() {
        lifecycleScope.launch {
            val count = repository.getEventCount()
            if (count == 0) {
                val sampleEvents = listOf(
                    SecurityEvent(0, System.currentTimeMillis() - 3600000, "CALL", "WARNING", 
                        "Appel suspect détecté", "Numéro inconnu +33 6 XX XX XX XX", 
                        "CallScreening", 65, true),
                    SecurityEvent(0, System.currentTimeMillis() - 7200000, "SMS", "CRITICAL", 
                        "SMS phishing détecté", "Lien malveillant dans message", 
                        "SMSAnalyzer", 85, true),
                    SecurityEvent(0, System.currentTimeMillis() - 10800000, "APP", "INFO", 
                        "Permission suspecte détectée", "App demande accès contacts", 
                        "AppMonitor", 40, true)
                )
                repository.insertMultiple(sampleEvents)
            }
        }
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
                startActivity(Intent(this, TransparencyActivity::class.java))
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

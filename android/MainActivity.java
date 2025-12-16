package com.sentinel.quantumvanguard;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.view.KeyEvent;
import android.graphics.Color;

/**
 * Sentinel Quantum Vanguard AI Pro - Android WebView Wrapper
 * 
 * This activity wraps the Sentinel PWA in a WebView for native Android distribution.
 * The PWA handles all business logic - this is just a container.
 */
public class MainActivity extends Activity {
    
    // IMPORTANT: Update this URL to your actual PWA deployment
    private static final String PWA_URL = "https://sentinel-quantum-vanguard.pages.dev";
    
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Create WebView
        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#1a2230")); // Match PWA background
        setContentView(webView);
        
        // Configure WebView settings
        configureWebView();
        
        // Load PWA
        webView.loadUrl(PWA_URL);
    }

    /**
     * Configure WebView for optimal PWA performance and security
     */
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        
        // Essential PWA features
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        
        // Caching for offline support
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAppCacheEnabled(true);
        settings.setAppCachePath(getApplicationContext().getCacheDir().getPath());
        
        // Security settings
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        
        // Performance settings
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        settings.setEnableSmoothTransition(true);
        
        // Responsive design
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        
        // Modern web standards
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setGeolocationEnabled(false); // Disable unless needed
        
        // WebViewClient for navigation control
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Keep navigation within the app
                if (url.startsWith(PWA_URL) || url.startsWith("https://")) {
                    return false; // Let WebView handle it
                }
                return true; // Block other protocols
            }
        });
        
        // WebChromeClient for JavaScript dialogs, progress, etc.
        webView.setWebChromeClient(new WebChromeClient());
    }

    /**
     * Handle back button navigation
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Clean up WebView on destroy
     */
    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}

package com.sentinel.quantumvanguard

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast

/**
 * JavaScript Interface for Sentinel WebView
 * 
 * Allows web pages to communicate with native Android
 * - Show native toasts
 * - Future: Request permissions (opt-in)
 * - Future: Access local storage
 */
class SentinelJSInterface(private val context: Context) {

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun getAppVersion(): String {
        return SentinelApplication.VERSION
    }

    @JavascriptInterface
    fun isNativeApp(): Boolean {
        return true
    }
}

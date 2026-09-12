package com.sentinel.quantum.security

class EmailHeaderAnalyzer {
    fun analyzeHeaders(headers: String): Boolean {
        if (headers.contains("X-Spam-Flag: YES") || headers.contains("Authentication-Results: fail")) {
            return false
        }
        return true
    }
}

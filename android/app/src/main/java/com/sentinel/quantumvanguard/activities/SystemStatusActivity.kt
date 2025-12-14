package com.sentinel.quantumvanguard.activities

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sentinel.quantumvanguard.R
import com.sentinel.quantumvanguard.SentinelApplication

class SystemStatusActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_status)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Statut Système"
        
        val statusText: TextView = findViewById(R.id.system_status_text)
        
        val permissionStatus = getPermissionStatus()
        
        statusText.text = """
            ℹ️ STATUT SYSTÈME
            
            📱 App: Sentinel Quantum Vanguard
            🔢 Version: ${SentinelApplication.VERSION}
            📦 Package: com.sentinel.quantumvanguard.debug
            
            🔐 PERMISSIONS:
            $permissionStatus
            
            ✅ WebView: Activé
            ✅ JavaScript: ON
            ✅ Storage Local: ON
            
            ⚠️ Mode: Debug/Démonstration
        """.trimIndent()
    }
    
    private fun getPermissionStatus(): String {
        val permissions = listOf(
            android.Manifest.permission.READ_PHONE_STATE to "Téléphone",
            android.Manifest.permission.READ_CALL_LOG to "Journal appels",
            android.Manifest.permission.READ_SMS to "SMS"
        )
        
        return permissions.joinToString("\n") { (perm, name) ->
            val granted = ContextCompat.checkSelfPermission(this, perm) == 
                PackageManager.PERMISSION_GRANTED
            val status = if (granted) "✅ Accordée" else "❌ Refusée"
            "  • $name: $status"
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

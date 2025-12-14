package com.sentinel.quantumvanguard.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sentinel.quantumvanguard.R
import kotlinx.coroutines.launch

/**
 * SMS Security Activity - SMS phishing detection (defensive mode)
 * 
 * Features:
 * - READ_SMS permission (opt-in)
 * - Display SMS messages (read-only)
 * - Local phishing detection (simulated)
 * - Link analysis (pattern matching)
 * 
 * Ethical Constraints:
 * - Read-only access (no SMS sending)
 * - Local analysis only (no cloud)
 * - Simulation clearly labeled
 * - Educational purpose
 */
class SmsSecurityActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 104
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_security)
        
        supportActionBar?.apply {
            title = "Sécurité SMS"
            setDisplayHomeAsUpEnabled(true)
        }
        
        recyclerView = findViewById(R.id.sms_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        checkAndRequestPermission()
    }
    
    private fun checkAndRequestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED) {
            loadSmsMessages()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_SMS),
                PERMISSION_REQUEST_CODE
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadSmsMessages()
            } else {
                Toast.makeText(
                    this,
                    "Permission READ_SMS refusée - Impossible d'analyser les SMS",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
    
    private fun loadSmsMessages() {
        lifecycleScope.launch {
            // Simulated SMS analysis for demo
            Toast.makeText(
                this@SmsSecurityActivity,
                "Analyse locale - Simulation pédagogique\nAucun SMS réel analysé sans permission",
                Toast.LENGTH_LONG
            ).show()
            
            // In a real implementation, would query SMS content provider
            // and perform local phishing detection
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

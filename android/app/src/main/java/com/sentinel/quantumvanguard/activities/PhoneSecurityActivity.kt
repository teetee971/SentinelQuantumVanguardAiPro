package com.sentinel.quantumvanguard.activities

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sentinel.quantumvanguard.R
import com.sentinel.quantumvanguard.data.SecurityEvent
import com.sentinel.quantumvanguard.database.SentinelDatabase
import com.sentinel.quantumvanguard.repository.EventRepository
import kotlinx.coroutines.launch

class PhoneSecurityActivity : AppCompatActivity() {
    
    private lateinit var repository: EventRepository
    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var statusText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_security)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sécurité Téléphone"
        
        // Initialize database
        val database = SentinelDatabase.getDatabase(this)
        repository = EventRepository(database.eventDao())
        
        statusText = findViewById(R.id.phone_security_status)
        eventsRecyclerView = findViewById(R.id.events_recycler_view)
        
        eventsRecyclerView.layoutManager = LinearLayoutManager(this)
        
        loadEvents()
        checkPermissions()
    }
    
    private fun loadEvents() {
        lifecycleScope.launch {
            val events = repository.getRecentEvents(20)
            statusText.text = "📊 ${events.size} événements enregistrés\n" +
                "⚠️ Analyse probabiliste - Pas de certitude absolue"
            
            // In real implementation, set up adapter here
            Toast.makeText(this@PhoneSecurityActivity, 
                "Chargé ${events.size} événements (démonstration)", 
                Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkPermissions() {
        val hasCallPerm = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasCallPerm) {
            Toast.makeText(this, 
                "Mode démonstration - Permissions non accordées", 
                Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

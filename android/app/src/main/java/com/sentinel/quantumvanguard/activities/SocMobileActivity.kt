package com.sentinel.quantumvanguard.activities

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sentinel.quantumvanguard.R
import com.sentinel.quantumvanguard.database.SentinelDatabase
import com.sentinel.quantumvanguard.repository.EventRepository
import kotlinx.coroutines.launch

class SocMobileActivity : AppCompatActivity() {
    
    private lateinit var repository: EventRepository
    private lateinit var statusText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_soc_mobile)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "SOC Mobile"
        
        val database = SentinelDatabase.getDatabase(this)
        repository = EventRepository(database.eventDao())
        
        statusText = findViewById(R.id.soc_status_text)
        
        loadSecurityStatus()
    }
    
    private fun loadSecurityStatus() {
        lifecycleScope.launch {
            val count = repository.getEventCount()
            val latest = repository.getLatestTimestamp() ?: 0
            
            statusText.text = "🛡️ SOC Mobile - Statut: OK\n" +
                "📊 Événements: $count\n" +
                "⚠️ Démonstration - Événements simulés"
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

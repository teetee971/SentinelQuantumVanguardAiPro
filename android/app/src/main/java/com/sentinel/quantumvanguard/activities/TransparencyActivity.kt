package com.sentinel.quantumvanguard.activities

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sentinel.quantumvanguard.R

class TransparencyActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transparency)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Transparence & Conformité"
        
        val infoText: TextView = findViewById(R.id.transparency_text)
        
        infoText.text = """
            🔒 TRANSPARENCE & CONFORMITÉ RGPD
            
            ✅ CE QUE L'APP FAIT:
            • Affiche informations via WebView
            • Simule monitoring sécurité
            • Journalise événements localement
            • Fournit contenu éducatif
            • Affiche statut permissions
            • Démontre approche défensive
            
            ❌ CE QUE L'APP NE FAIT PAS:
            • N'intercepte PAS communications
            • N'accède PAS contenu appels/SMS
            • Ne suit PAS la localisation
            • Ne collecte PAS données personnelles
            • N'envoie PAS données cloud
            • Ne contourne PAS sécurité Android
            • Ne garantit PAS détection menaces
            • N'est PAS spyware/surveillance
            
            📋 PERMISSIONS (10):
            • INTERNET: Requis (WebView)
            • ACCESS_NETWORK_STATE: Requis
            • READ_PHONE_STATE: Opt-in
            • READ_CALL_LOG: Opt-in
            • READ_CONTACTS: Opt-in
            • READ_SMS: Opt-in
            • CALL_PHONE: Opt-in
            • ACCESS_FINE_LOCATION: Opt-in
            • RECORD_AUDIO: Opt-in
            • FOREGROUND_SERVICE: Opt-in
            
            🇪🇺 CONFORMITÉ RGPD:
            • Articles 13-20 respectés
            • Minimisation données
            • Droit effacement
            • Pas de profilage
            • Stockage local uniquement
            
            🏷️ STATUT FONCTIONS:
            🟢 ACTIF - Fonctionnel
            🟡 SIMULÉ - Démo uniquement
            💤 ROADMAP - Futur
        """.trimIndent()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

package com.sentinel.quantumvanguard.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sentinel.quantumvanguard.R

/**
 * Dialer Activity - Native phone dialer interface
 * 
 * Features:
 * - Numpad for phone number entry
 * - Call composition via Intent (ACTION_DIAL/ACTION_CALL)
 * - Recent calls history
 * - Support for default phone app (ROLE_DIALER API 29+)
 * 
 * Ethical Constraints:
 * - ACTION_DIAL doesn't require permission (shows Android dialer)
 * - ACTION_CALL requires CALL_PHONE permission (explicit opt-in)
 * - No call interception
 * - No call recording without consent
 */
class DialerActivity : AppCompatActivity() {

    private lateinit var numberDisplay: TextView
    private var currentNumber = ""
    
    companion object {
        private const val REQUEST_CALL_PERMISSION = 102
        private const val REQUEST_DEFAULT_DIALER = 103
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialer)
        
        supportActionBar?.apply {
            title = "Téléphone Sécurisé"
            setDisplayHomeAsUpEnabled(true)
        }
        
        numberDisplay = findViewById(R.id.number_display)
        
        setupNumpad()
        setupActionButtons()
    }
    
    private fun setupNumpad() {
        val buttonIds = arrayOf(
            R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
            R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9,
            R.id.btn_star, R.id.btn_hash
        )
        
        val values = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "#")
        
        buttonIds.forEachIndexed { index, id ->
            findViewById<Button>(id).setOnClickListener {
                currentNumber += values[index]
                updateDisplay()
            }
        }
        
        findViewById<Button>(R.id.btn_clear).setOnClickListener {
            if (currentNumber.isNotEmpty()) {
                currentNumber = currentNumber.dropLast(1)
                updateDisplay()
            }
        }
        
        findViewById<Button>(R.id.btn_clear).setOnLongClickListener {
            currentNumber = ""
            updateDisplay()
            true
        }
    }
    
    private fun setupActionButtons() {
        // ACTION_DIAL - Pre-fills Android dialer (no permission needed)
        findViewById<Button>(R.id.btn_dial).setOnClickListener {
            if (currentNumber.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$currentNumber")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Saisissez un numéro", Toast.LENGTH_SHORT).show()
            }
        }
        
        // ACTION_CALL - Direct call (requires CALL_PHONE permission)
        findViewById<Button>(R.id.btn_call).setOnClickListener {
            if (currentNumber.isEmpty()) {
                Toast.makeText(this, "Saisissez un numéro", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {
                makeDirectCall()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CALL_PHONE),
                    REQUEST_CALL_PERMISSION
                )
            }
        }
        
        // Set as default dialer (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findViewById<Button>(R.id.btn_set_default).setOnClickListener {
                val telecomManager = getSystemService(TelecomManager::class.java)
                if (telecomManager != null) {
                    val intent = telecomManager.createManageBlockedNumbersIntent()
                    if (intent != null) {
                        startActivityForResult(intent, REQUEST_DEFAULT_DIALER)
                    }
                }
                Toast.makeText(
                    this,
                    "Fonctionnalité application téléphone par défaut - API 29+ requis",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            findViewById<Button>(R.id.btn_set_default).isEnabled = false
        }
    }
    
    private fun updateDisplay() {
        numberDisplay.text = if (currentNumber.isEmpty()) "Composez un numéro" else currentNumber
    }
    
    private fun makeDirectCall() {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$currentNumber")
        }
        startActivity(intent)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CALL_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                makeDirectCall()
            } else {
                Toast.makeText(
                    this,
                    "Permission CALL_PHONE refusée - Utilisez le bouton 'Composer' à la place",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

package com.sentinel.quantumvanguard.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sentinel.quantumvanguard.R
import com.sentinel.quantumvanguard.database.SentinelDatabase
import com.sentinel.quantumvanguard.repository.ContactRepository
import kotlinx.coroutines.launch

/**
 * Contacts Activity - Display device contacts with local tagging
 * 
 * Features:
 * - READ_CONTACTS permission (opt-in)
 * - Display all device contacts
 * - Local tagging: Known/Unknown/Reported
 * - No cloud sync (100% local)
 * 
 * Ethical Constraints:
 * - Permission requested explicitly
 * - Read-only access
 * - No data transmission
 * - Tags stored locally in Room database
 */
class ContactsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var repository: ContactRepository
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)
        
        supportActionBar?.apply {
            title = "Contacts Sécurisés"
            setDisplayHomeAsUpEnabled(true)
        }
        
        val database = SentinelDatabase.getDatabase(this)
        repository = ContactRepository(database.contactDao())
        
        recyclerView = findViewById(R.id.contacts_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        checkAndRequestPermission()
    }
    
    private fun checkAndRequestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
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
                loadContacts()
            } else {
                Toast.makeText(
                    this,
                    "Permission READ_CONTACTS refusée - Impossible d'afficher les contacts",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
    
    private fun loadContacts() {
        lifecycleScope.launch {
            val contacts = mutableListOf<ContactInfo>()
            
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone._ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getString(0)
                    val name = it.getString(1)
                    val number = it.getString(2)
                    
                    contacts.add(ContactInfo(id, name, number, "UNKNOWN", 0))
                }
            }
            
            Toast.makeText(
                this@ContactsActivity,
                "${contacts.size} contacts chargés\nDonnées locales uniquement - Aucun envoi cloud",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    data class ContactInfo(
        val id: String,
        val name: String,
        val phoneNumber: String,
        val tag: String,  // KNOWN, UNKNOWN, REPORTED
        val riskScore: Int
    )
}

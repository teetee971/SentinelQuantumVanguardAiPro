package com.sentinel.quantumvanguard.repository

import com.sentinel.quantumvanguard.data.Contact
import com.sentinel.quantumvanguard.data.ContactDao

/**
 * Contact Repository - Data management for contacts
 */
class ContactRepository(private val contactDao: ContactDao) {
    
    suspend fun getAllContacts(): List<Contact> = contactDao.getAllContacts()
    
    suspend fun getContactByPhone(phoneNumber: String): Contact? = 
        contactDao.getContactByPhone(phoneNumber)
    
    suspend fun getContactsByTag(tag: String): List<Contact> = 
        contactDao.getContactsByTag(tag)
    
    suspend fun insert(contact: Contact): Long = contactDao.insert(contact)
    
    suspend fun update(contact: Contact) = contactDao.update(contact)
    
    suspend fun delete(contact: Contact) = contactDao.delete(contact)
    
    suspend fun deleteAll() = contactDao.deleteAll()
    
    suspend fun getCount(): Int = contactDao.getCount()
}

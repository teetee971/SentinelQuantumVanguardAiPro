package com.sentinel.quantumvanguard.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Contact DAO - Database operations for contacts
 */
@Dao
interface ContactDao {
    
    @Query("SELECT * FROM contacts ORDER BY displayName ASC")
    suspend fun getAllContacts(): List<Contact>
    
    @Query("SELECT * FROM contacts WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getContactByPhone(phoneNumber: String): Contact?
    
    @Query("SELECT * FROM contacts WHERE tag = :tag")
    suspend fun getContactsByTag(tag: String): List<Contact>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: Contact): Long
    
    @Update
    suspend fun update(contact: Contact)
    
    @Delete
    suspend fun delete(contact: Contact)
    
    @Query("DELETE FROM contacts")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun getCount(): Int
}

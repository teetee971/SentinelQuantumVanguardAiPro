package com.sentinel.quantum.security

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * LocalLogger - Gestionnaire de logs locaux
 * Stocke les événements de sécurité de manière locale et visible
 */
class LocalLogger(private val context: Context) {
    
    private val logFile: File by lazy {
        File(context.filesDir, "sentinel_security.log")
    }
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    /**
     * Ajoute une entrée de log
     */
    fun log(level: LogLevel, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] [${level.name}] [$tag] $message\n"
        
        try {
            logFile.appendText(logEntry)
        } catch (e: Exception) {
            // Log silencieux si échec
            e.printStackTrace()
        }
    }
    
    /**
     * Récupère tous les logs
     */
    fun getLogs(): List<LogEntry> {
        return try {
            if (!logFile.exists()) {
                emptyList()
            } else {
                logFile.readLines()
                    .filter { it.isNotBlank() }
                    .mapNotNull { parseLogEntry(it) }
                    .reversed() // Plus récents en premier
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Efface tous les logs
     */
    fun clearLogs() {
        try {
            logFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun parseLogEntry(line: String): LogEntry? {
        return try {
            val timestampMatch = Regex("\\[(.*?)\\]").find(line) ?: return null
            val timestamp = timestampMatch.groupValues[1]
            
            val parts = line.split("] [")
            if (parts.size < 4) return null
            
            val level = parts[1].trim('[', ']')
            val tag = parts[2].trim('[', ']')
            val message = parts.drop(3).joinToString("] [").trim('[', ']')
            
            LogEntry(
                timestamp = timestamp,
                level = LogLevel.valueOf(level),
                tag = tag,
                message = message
            )
        } catch (e: Exception) {
            null
        }
    }
    
    enum class LogLevel {
        INFO, WARNING, ERROR, SECURITY
    }
    
    data class LogEntry(
        val timestamp: String,
        val level: LogLevel,
        val tag: String,
        val message: String
    )
}

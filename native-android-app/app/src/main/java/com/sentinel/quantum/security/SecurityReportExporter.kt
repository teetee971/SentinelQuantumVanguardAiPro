package com.sentinel.quantum.security

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * SecurityReportExporter (MODULE F) - Export security audit proof
 * 
 * Exports comprehensive security reports to local files for:
 * - Audit trail preservation
 * - Compliance documentation
 * - Offline review
 * - Evidence archival
 * 
 * Features:
 * - JSON export (machine-readable)
 * - Plain text export (human-readable)
 * - Timestamped filenames
 * - Secure local storage (app private directory)
 * 
 * Privacy guarantee:
 * - All files stored locally in app private directory
 * - No cloud upload
 * - No external transmission
 * - User controls data retention
 * 
 * GDPR/ANSSI compliant
 */
class SecurityReportExporter(private val context: Context) {

    private val exportDir: File by lazy {
        File(context.filesDir, "security_reports").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Export JSON security report
     * 
     * @param json The JSON report string
     * @param customFilename Optional custom filename (without extension)
     * @return File object pointing to the exported report
     */
    fun exportJson(
        json: String,
        customFilename: String? = null
    ): File {
        val filename = customFilename ?: generateFilename("json")
        val file = File(exportDir, filename)
        
        file.writeText(json)
        
        return file
    }

    /**
     * Export human-readable text report
     * 
     * @param report The human-readable report string
     * @param customFilename Optional custom filename (without extension)
     * @return File object pointing to the exported report
     */
    fun exportText(
        report: String,
        customFilename: String? = null
    ): File {
        val filename = customFilename ?: generateFilename("txt")
        val file = File(exportDir, filename)
        
        file.writeText(report)
        
        return file
    }

    /**
     * Export both JSON and text reports
     * 
     * @param json JSON report
     * @param text Human-readable report
     * @return Pair of File objects (JSON file, text file)
     */
    fun exportBoth(
        json: String,
        text: String
    ): Pair<File, File> {
        val timestamp = getTimestamp()
        val baseFilename = "security_report_$timestamp"
        
        val jsonFile = exportJson(json, "$baseFilename.json")
        val textFile = exportText(text, "$baseFilename.txt")
        
        return Pair(jsonFile, textFile)
    }

    /**
     * List all exported reports
     * 
     * @return List of report files sorted by date (newest first)
     */
    fun listReports(): List<File> {
        if (!exportDir.exists()) return emptyList()
        
        return exportDir.listFiles()
            ?.filter { it.isFile && (it.extension == "json" || it.extension == "txt") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Get report file by date
     * 
     * @param date Date string in format "yyyy-MM-dd"
     * @return List of reports from that date
     */
    fun getReportsByDate(date: String): List<File> {
        return listReports().filter { file ->
            file.name.contains(date)
        }
    }

    /**
     * Delete old reports
     * 
     * @param daysOld Reports older than this many days will be deleted
     * @return Number of files deleted
     */
    fun deleteOldReports(daysOld: Int = 30): Int {
        if (!exportDir.exists()) return 0
        
        val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        var deletedCount = 0
        
        listReports().forEach { file ->
            if (file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    deletedCount++
                }
            }
        }
        
        return deletedCount
    }

    /**
     * Delete all reports
     * 
     * @return Number of files deleted
     */
    fun deleteAllReports(): Int {
        if (!exportDir.exists()) return 0
        
        var deletedCount = 0
        listReports().forEach { file ->
            if (file.delete()) {
                deletedCount++
            }
        }
        
        return deletedCount
    }

    /**
     * Get total size of all reports in bytes
     */
    fun getTotalReportsSize(): Long {
        return listReports().sumOf { it.length() }
    }

    /**
     * Get formatted size string (e.g., "2.5 MB")
     */
    fun getFormattedTotalSize(): String {
        val bytes = getTotalReportsSize()
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    /**
     * Get storage statistics
     */
    fun getStorageStats(): StorageStats {
        val reports = listReports()
        return StorageStats(
            totalReports = reports.size,
            jsonReports = reports.count { it.extension == "json" },
            textReports = reports.count { it.extension == "txt" },
            totalSizeBytes = getTotalReportsSize(),
            oldestReport = reports.minByOrNull { it.lastModified() }?.lastModified(),
            newestReport = reports.maxByOrNull { it.lastModified() }?.lastModified(),
            exportDirectory = exportDir.absolutePath
        )
    }

    private fun generateFilename(extension: String): String {
        val timestamp = getTimestamp()
        return "security_report_$timestamp.$extension"
    }

    private fun getTimestamp(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        return dateFormat.format(Date())
    }

    /**
     * Create export summary with metadata
     */
    fun createExportSummary(
        jsonFile: File,
        textFile: File? = null
    ): String {
        return buildString {
            appendLine("=== Security Report Export Summary ===")
            appendLine()
            appendLine("Export Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine()
            appendLine("JSON Report:")
            appendLine("  Path: ${jsonFile.absolutePath}")
            appendLine("  Size: ${formatBytes(jsonFile.length())}")
            appendLine()
            
            textFile?.let {
                appendLine("Text Report:")
                appendLine("  Path: ${it.absolutePath}")
                appendLine("  Size: ${formatBytes(it.length())}")
                appendLine()
            }
            
            val stats = getStorageStats()
            appendLine("Storage Statistics:")
            appendLine("  Total Reports: ${stats.totalReports}")
            appendLine("  Total Size: ${getFormattedTotalSize()}")
            appendLine("  Storage Location: ${stats.exportDirectory}")
            appendLine()
            appendLine("Privacy Notice:")
            appendLine("  ✓ All reports stored locally")
            appendLine("  ✓ No cloud synchronization")
            appendLine("  ✓ User controls retention")
            appendLine("  ✓ GDPR compliant")
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}

/**
 * Storage statistics data class
 */
data class StorageStats(
    val totalReports: Int,
    val jsonReports: Int,
    val textReports: Int,
    val totalSizeBytes: Long,
    val oldestReport: Long?,
    val newestReport: Long?,
    val exportDirectory: String
)

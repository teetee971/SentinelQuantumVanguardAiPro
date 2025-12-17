package com.sentinel.quantum.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sentinel.quantum.R
import com.sentinel.quantum.security.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SecurityDashboardActivity (MODULE E) - Security Dashboard UI
 * 
 * Provides comprehensive security analysis visualization:
 * - Overall security score
 * - Detailed breakdown by category
 * - Human-readable recommendations
 * - Export functionality
 * 
 * Features:
 * - Real-time analysis
 * - Color-coded security levels
 * - Export to JSON/text
 * - Refresh capability
 * 
 * All processing is 100% local - no network calls
 */
class SecurityDashboardActivity : AppCompatActivity() {

    // UI Components
    private lateinit var scoreTextView: TextView
    private lateinit var levelTextView: TextView
    private lateinit var riskLevelTextView: TextView
    private lateinit var systemScoreTextView: TextView
    private lateinit var networkScoreTextView: TextView
    private lateinit var integrityScoreTextView: TextView
    private lateinit var permissionsScoreTextView: TextView
    private lateinit var detailsTextView: TextView
    private lateinit var exportButton: Button
    private lateinit var refreshButton: Button

    // Security modules
    private lateinit var systemAudit: SystemAuditModule
    private lateinit var networkAudit: NetworkSecurityModule
    private lateinit var permissionScanner: PermissionScanner
    private lateinit var integrityVerifier: IntegrityVerifier
    private lateinit var riskDetector: RiskEnvironmentDetector
    private lateinit var reportBuilder: SecurityReportBuilder
    private lateinit var reportExporter: SecurityReportExporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_dashboard)

        initializeViews()
        initializeModules()
        setupListeners()
        runSecurityAnalysis()
    }

    private fun initializeViews() {
        scoreTextView = findViewById(R.id.securityScore)
        levelTextView = findViewById(R.id.securityLevel)
        riskLevelTextView = findViewById(R.id.riskLevel)
        systemScoreTextView = findViewById(R.id.systemScore)
        networkScoreTextView = findViewById(R.id.networkScore)
        integrityScoreTextView = findViewById(R.id.integrityScore)
        permissionsScoreTextView = findViewById(R.id.permissionsScore)
        detailsTextView = findViewById(R.id.securityDetails)
        exportButton = findViewById(R.id.exportButton)
        refreshButton = findViewById(R.id.refreshButton)
    }

    private fun initializeModules() {
        systemAudit = SystemAuditModule(this)
        networkAudit = NetworkSecurityModule(this)
        permissionScanner = PermissionScanner(this)
        integrityVerifier = IntegrityVerifier(this) // Add expected signature if available
        riskDetector = RiskEnvironmentDetector()
        reportBuilder = SecurityReportBuilder()
        reportExporter = SecurityReportExporter(this)
    }

    private fun setupListeners() {
        exportButton.setOnClickListener {
            exportReport()
        }

        refreshButton.setOnClickListener {
            runSecurityAnalysis()
        }
    }

    private fun runSecurityAnalysis() {
        // Disable buttons during analysis
        exportButton.isEnabled = false
        refreshButton.isEnabled = false
        detailsTextView.text = "Running comprehensive security analysis..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Run all security modules
                val systemResult = systemAudit.runAudit()
                val networkResult = networkAudit.runAudit()
                val permissions = permissionScanner.scan()
                val integrityResult = integrityVerifier.verify()
                val riskResult = riskDetector.analyze()

                // Generate comprehensive report
                val jsonReport = reportBuilder.buildJson(
                    systemResult,
                    networkResult,
                    permissions,
                    integrityResult
                )

                val humanReport = reportBuilder.buildHumanReadableReport(
                    systemResult,
                    networkResult,
                    permissions,
                    integrityResult
                )

                // Extract score information
                val scoreObj = jsonReport.getJSONObject("score")
                val scoreValue = scoreObj.getInt("value")
                val scoreLevel = scoreObj.getString("level")
                val breakdown = scoreObj.getJSONObject("breakdown")

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    updateUI(
                        scoreValue,
                        scoreLevel,
                        riskResult.overallRiskLevel.name,
                        breakdown.getInt("system"),
                        breakdown.getInt("network"),
                        breakdown.getInt("integrity"),
                        breakdown.getInt("permissions"),
                        humanReport
                    )

                    // Re-enable buttons
                    exportButton.isEnabled = true
                    refreshButton.isEnabled = true

                    Toast.makeText(
                        this@SecurityDashboardActivity,
                        "Security analysis complete",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    detailsTextView.text = "Error during analysis: ${e.message}"
                    exportButton.isEnabled = true
                    refreshButton.isEnabled = true

                    Toast.makeText(
                        this@SecurityDashboardActivity,
                        "Analysis failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun updateUI(
        score: Int,
        level: String,
        riskLevel: String,
        systemScore: Int,
        networkScore: Int,
        integrityScore: Int,
        permissionsScore: Int,
        details: String
    ) {
        // Update score and level
        scoreTextView.text = "Score: $score/100"
        levelTextView.text = "Level: $level"
        riskLevelTextView.text = "Environment: $riskLevel"

        // Color code the level
        val levelColor = when (level) {
            "SECURE" -> Color.rgb(76, 175, 80)      // Green
            "MODERATE" -> Color.rgb(255, 152, 0)    // Orange
            "WEAK" -> Color.rgb(255, 87, 34)        // Deep Orange
            "CRITICAL" -> Color.rgb(244, 67, 54)    // Red
            else -> Color.GRAY
        }
        levelTextView.setTextColor(levelColor)

        // Color code risk level
        val riskColor = when (riskLevel) {
            "LOW" -> Color.rgb(76, 175, 80)         // Green
            "MEDIUM" -> Color.rgb(255, 152, 0)      // Orange
            "HIGH" -> Color.rgb(255, 87, 34)        // Deep Orange
            "CRITICAL" -> Color.rgb(244, 67, 54)    // Red
            else -> Color.GRAY
        }
        riskLevelTextView.setTextColor(riskColor)

        // Update breakdown
        systemScoreTextView.text = "System: $systemScore/25"
        networkScoreTextView.text = "Network: $networkScore/25"
        integrityScoreTextView.text = "Integrity: $integrityScore/30"
        permissionsScoreTextView.text = "Permissions: $permissionsScore/20"

        // Update details
        detailsTextView.text = details
    }

    private fun exportReport() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Re-run analysis to get fresh data
                val systemResult = systemAudit.runAudit()
                val networkResult = networkAudit.runAudit()
                val permissions = permissionScanner.scan()
                val integrityResult = integrityVerifier.verify()

                // Generate reports
                val jsonReport = reportBuilder.buildJson(
                    systemResult,
                    networkResult,
                    permissions,
                    integrityResult
                )

                val humanReport = reportBuilder.buildHumanReadableReport(
                    systemResult,
                    networkResult,
                    permissions,
                    integrityResult
                )

                // Export both formats
                val (jsonFile, textFile) = reportExporter.exportBoth(
                    jsonReport.toString(2),
                    humanReport
                )

                // Create summary
                val summary = reportExporter.createExportSummary(jsonFile, textFile)

                withContext(Dispatchers.Main) {
                    // Show success message with file paths
                    Toast.makeText(
                        this@SecurityDashboardActivity,
                        "Reports exported successfully!\n" +
                                "JSON: ${jsonFile.name}\n" +
                                "Text: ${textFile.name}",
                        Toast.LENGTH_LONG
                    ).show()

                    // Optionally display summary
                    detailsTextView.text = summary
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SecurityDashboardActivity,
                        "Export failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Optionally refresh on resume to catch environment changes
    }
}

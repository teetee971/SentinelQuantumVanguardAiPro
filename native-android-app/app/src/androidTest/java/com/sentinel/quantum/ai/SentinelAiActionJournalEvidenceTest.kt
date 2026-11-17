package com.sentinel.quantum.ai

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only window check after MANUAL real model/Keyguard interaction. Never fabricates a result.
 * Default baseline mode is not an executed-action qualification and reports verified=false.
 */
@RunWith(AndroidJUnit4::class)
class SentinelAiActionJournalEvidenceTest {
    @Test fun actionJournalEvidenceProtocol() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val args=InstrumentationRegistry.getArguments()
        val phase=args.getString("sentinel_ai_action_phase") ?: "baseline"
        check(phase in setOf("baseline","verify"))
        if (phase=="verify") check(context.packageName=="com.sentinel.quantum.aivalidation")
        val directory=File(context.noBackupFilesDir,"sentinel-ai-audit-v1")
        val keys=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val present=keys.containsAlias("sentinel_ai_audit_v1")
        check(present==directory.isDirectory) { "Application journal/key state inconsistent" }
        if (!present) {
            check(phase=="baseline") { "Real application journal is missing" }
            instrumentation.sendStatus(0,Bundle().apply {
                putString("cycle9_baseline_sequence","0")
                putString("cycle9_baseline_digest","0".repeat(64))
                putString("cycle9_action_window_verified","false")
            })
            return
        }
        val journal=SentinelAiAndroidAuditJournal.open(context)
        val receipt=journal.verify()
        val records=File(directory,"events").readText().removeSuffix("\n").split('\n').drop(1)
        assertEquals(receipt,journal.verify()) // A concurrent write makes this snapshot inconclusive.
        assertEquals(receipt.sequence,records.size.toLong())
        if (records.isNotEmpty()) assertEquals(receipt.digest,records.last().split('|').last())
        var verified=false
        if (phase=="verify") {
            val start=checkNotNull(args.getString("sentinel_ai_start_sequence")).toInt()
            val digest=checkNotNull(args.getString("sentinel_ai_start_digest"))
            check(start in 0..records.size)
            assertEquals(if(start==0) "0".repeat(64) else records[start-1].split('|').last(),digest)
            val kinds=records.drop(start).map { it.split('|')[2] }
            val expected=checkNotNull(args.getString("sentinel_ai_expected_checkpoints")).toInt()
            check(expected in 0..1)
            assertEquals(expected,kinds.count { it=="CHECKPOINT" })
            if(expected==1) {
                val authenticated=kinds.indexOf("AUTHENTICATED")
                assertTrue("Authenticated event must precede the checkpoint",authenticated>=0 && authenticated<kinds.indexOf("CHECKPOINT"))
            }
            verified=true
        }
        instrumentation.sendStatus(0,Bundle().apply {
            putString("cycle9_baseline_sequence",receipt.sequence.toString())
            putString("cycle9_baseline_digest",receipt.digest)
            putString("cycle9_action_window_verified",verified.toString())
        })
    }
}

package com.sentinel.quantum.security

import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SentinelCleanerSessionTest {
    @get:Rule val temp=TemporaryFolder()
    private val now=1_800_000_000_000L
    private fun files():List<File> = listOf("first.txt","second.txt").map { File(temp.root,"sentinel_log_export/$it").apply {
        parentFile!!.mkdirs();writeText("copy");assertTrue(setLastModified(now-172_800_000))
    } }
    private fun create(scope:CoroutineScope, action:((SentinelCleanupPolicy.Candidate)->SentinelCleanupPolicy.Result)?=null):SentinelCleanerSession {
        val collector=SentinelOwnedCleanupCollector(listOf(temp.root)){now}
        val executor=SentinelOwnedCleanupExecutor(listOf(temp.root)){now}
        return SentinelCleanerSession(scope,{collector.scan()},action ?: executor::execute,Dispatchers.Unconfined)
    }
    private fun prepare(s:SentinelCleanerSession):List<SentinelCleanupPolicy.Candidate> {
        assertTrue(s.scan());val c=s.state.value.discovery!!.candidates;s.select(c.map{it.stableId}.toSet());return c
    }
    @Test fun publishesEachVerifiedResultBeforeNextDeletion() {
        val files=files();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val executor=SentinelOwnedCleanupExecutor(listOf(temp.root)){now}
        lateinit var session:SentinelCleanerSession;var calls=0
        session=create(scope){candidate -> if(calls++==1) assertEquals(1,session.state.value.results!!.size);executor.execute(candidate)}
        assertTrue(session.clean(prepare(session)));assertTrue(files.none{it.exists()});assertEquals(2,session.state.value.results!!.size);scope.cancel()
    }
    @Test fun oneFilesystemFailureDoesNotDiscardPriorSuccess() {
        val files=files();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined);var calls=0
        val executor=SentinelOwnedCleanupExecutor(listOf(temp.root)){now}
        val session=create(scope){if(calls++==1)throw SecurityException("revoked");executor.execute(it)}
        assertTrue(session.clean(prepare(session)));assertEquals(2,session.state.value.results!!.size)
        assertEquals(1,session.state.value.results!!.count{it.after.state==SentinelCleanupPolicy.ActionState.FAILED})
        assertFalse(files[0].exists());assertTrue(files[1].exists());scope.cancel()
    }
    @Test fun cancellationPreservesCompletedResultAndLeavesRemainderUntouched() {
        val files=files();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val executor=SentinelOwnedCleanupExecutor(listOf(temp.root)){now}
        val session=create(scope){executor.execute(it).also{scope.cancel()}}
        assertTrue(session.clean(prepare(session)));assertFalse(files[0].exists());assertTrue(files[1].exists())
        assertEquals(1,session.state.value.results!!.size);assertFalse(session.state.value.busy)
    }
    @Test fun scanNeverDeletesAndCleaningNeedsCurrentExplicitSelection() {
        val files=files();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined);val session=create(scope)
        assertTrue(session.scan());val snapshot=session.state.value.discovery!!.candidates
        assertFalse(session.clean(snapshot));assertTrue(files.all{it.exists()});scope.cancel()
    }
    @Test fun duplicateSubmissionDuringExecutionIsRejected() {
        val files=files();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val executor=SentinelOwnedCleanupExecutor(listOf(temp.root)){now};lateinit var session:SentinelCleanerSession
        var snapshot=emptyList<SentinelCleanupPolicy.Candidate>();var calls=0
        session=create(scope){calls++;assertFalse(session.clean(snapshot));executor.execute(it)}
        snapshot=prepare(session);assertTrue(session.clean(snapshot));assertEquals(2,calls);assertTrue(files.none{it.exists()});scope.cancel()
    }
}

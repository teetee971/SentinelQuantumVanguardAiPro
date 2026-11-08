package com.sentinel.quantum.security

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE,application=Application::class)
class MmsOutgoingAbandonJournalTest {
    private class NoProviderAccess : ContentProvider() {
        var accesses=0
        override fun onCreate()=true
        private fun forbidden():Nothing {accesses++;throw AssertionError("Provider accessed without SMS role")}
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,order:String?):Cursor?=forbidden()
        override fun insert(uri:Uri,values:ContentValues?):Uri?=forbidden()
        override fun delete(uri:Uri,selection:String?,args:Array<out String>?):Int=forbidden()
        override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?):Int=forbidden()
        override fun getType(uri:Uri):String?=forbidden()
    }
    private val token="12345678-1234-1234-1234-123456789012"
    private fun setup():Pair<MmsProviderJournal,NoProviderAccess> {
        val context=RuntimeEnvironment.getApplication()
        assertFalse(context.readSmsRoleStateFailClosed()==SmsActivationDiagnostics.SmsRoleState.HELD)
        val provider=NoProviderAccess();ShadowContentResolver.registerProviderInternal("mms",provider)
        val journal=MmsProviderJournal(context)
        assertTrue(journal.begin(token,"test-transaction",1));assertTrue(journal.recordRoot(token,42,2));assertTrue(journal.markReady(token,42,3))
        return journal to provider
    }
    @Test fun lossOfRoleRetainsDurableLocalFailureWithoutProviderAccess() {
        val (journal,provider)=setup();val context=RuntimeEnvironment.getApplication()
        assertFalse(MmsConversationStore(context).abandonBeforeTransport(token,42))
        val saved=requireNotNull(MmsProviderJournal(context).read(token))
        assertEquals(MmsProviderJournal.Phase.RESULT_FAILED,saved.phase)
        assertEquals("test-transaction",saved.transactionId);assertEquals(42L,saved.providerMessageId)
        assertEquals(0,journal.reconcileReadyAfterProcessDeath(10));assertEquals(MmsProviderJournal.Phase.RESULT_FAILED,journal.read(token)?.phase)
        assertEquals(0,provider.accesses)
    }
    @Test fun wrongProviderIdCannotRewriteRecoveryRecord() {
        val (journal,provider)=setup();assertFalse(MmsConversationStore(RuntimeEnvironment.getApplication()).abandonBeforeTransport(token,99))
        assertEquals(MmsProviderJournal.Phase.READY,journal.read(token)?.phase);assertEquals(42L,journal.read(token)?.providerMessageId);assertEquals(0,provider.accesses)
    }
    @Test fun submittedMessageMustNeverBeAbandonedAsUnsent() {
        val (journal,provider)=setup();assertTrue(journal.markSubmitted(token,42,4))
        assertFalse(MmsConversationStore(RuntimeEnvironment.getApplication()).abandonBeforeTransport(token,42))
        assertEquals(MmsProviderJournal.Phase.SUBMITTED,journal.read(token)?.phase);assertEquals(0,provider.accesses)
    }

    @Test fun callbackSuccessRemainsJournaledWhenRoleIsMissing() {
        val (journal,provider)=setup();assertTrue(journal.markSubmitted(token,42,4))
        assertFalse(MmsConversationStore(RuntimeEnvironment.getApplication()).applyTransportResult(token,42,true,5))
        assertEquals(MmsProviderJournal.Phase.RESULT_SENT,journal.read(token)?.phase)
        assertEquals(0,journal.reconcileReadyAfterProcessDeath(10));assertEquals(0,provider.accesses)
    }
    @Test fun callbackFailureRemainsJournaledWhenRoleIsMissing() {
        val (journal,provider)=setup();assertTrue(journal.markSubmitted(token,42,4))
        assertFalse(MmsConversationStore(RuntimeEnvironment.getApplication()).applyTransportResult(token,42,false,5))
        assertEquals(MmsProviderJournal.Phase.RESULT_FAILED,journal.read(token)?.phase)
        assertEquals(0,provider.accesses)
    }
}

package com.sentinel.quantum.security

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(StrictJpegRobolectricRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk=[35], manifest=Config.NONE, application=Application::class)
class MmsOutgoingSenderTest {
    private class Fake : SentinelMmsSender.Effects {
        var realPreparation=false
        var role=true;var feature=true;var missingPermission:String?=null;var ineffectivePermission:String?=null
        var active=setOf(1);val limits=mutableMapOf<Int,Int>();val carrierRequests=mutableListOf<Int>();val transportSubscriptions=mutableListOf<Int>();var providerSubscription=-1;var prepareBudget=-1;var limit=20_000;var preparationCalls=0;var stageCalls=0;var persistenceCalls=0;var sendCalls=0
        var deleteCalls=0;var submitted=0;var unknown=0;var abandoned=0
        var afterPersist:(()->Unit)?=null;var throwProvider=false;var throwPrepareMemory=false;var throwCarrier=false;var throwDefault=false;var beforePrepared:(()->Unit)?=null;var preparationFailure:String?=null;var throwPreparation=false;var throwStage=false;var rejectStage=false;var rejectProvider=false;var throwTransport=false
        var stagedPdu:ByteArray?=null;var providerParts:List<MmsConversationStore.Attachment> = emptyList()
        var preparedTransaction="";var providerTransaction="";var providerDestination="";var providerText=""
        override fun hasMessagingFeature()=feature
        override fun roleHeld()=role
        override fun permissionGranted(permission:String)=permission!=missingPermission
        override fun permissionEffective(permission:String)=permission!=ineffectivePermission
        override fun activeSubscriptionIds()=active
        override fun defaultSubscriptionId():Int {if(throwDefault)throw IllegalStateException("default SIM");return 1}
        override fun carrier(subscriptionId:Int):SentinelMmsSender.Carrier {
            if(throwCarrier)throw IllegalStateException("carrier unavailable")
            check(subscriptionId in active);carrierRequests+=subscriptionId
            return object:SentinelMmsSender.Carrier {
                override val maxPduBytes:Int get()=limits[subscriptionId]?:limit
                override fun send(uri:Uri,callback:PendingIntent) {sendCalls++;transportSubscriptions+=subscriptionId;if(throwTransport)throw IllegalStateException("transport uncertain")}
            }
        }
        override fun prepare(destination:String,transactionId:String,text:String,attachments:List<SentinelMmsSendPduComposer.Part>,maxPduBytes:Int):MmsOutgoingPreparation.Result {
            preparationCalls++;preparedTransaction=transactionId;prepareBudget=maxPduBytes
            if(throwPrepareMemory)throw OutOfMemoryError("controlled codec allocation failure")
            if(throwPreparation)throw IllegalStateException("prepare")
            preparationFailure?.let{return MmsOutgoingPreparation.Result.Rejected(it)}
            val result=if (realPreparation) MmsOutgoingPreparation.prepare(destination,transactionId,text,attachments,maxPduBytes) else MmsOutgoingPreparation.prepare(destination,transactionId,text,attachments,maxPduBytes){bytes,budget->MmsOutgoingJpegCompressor.Result.Compressed(if(bytes.size<=budget)bytes else ByteArray(budget){42})}
            beforePrepared?.invoke();return result
        }
        override fun stage(pdu:ByteArray):MmsSendPduStager.Result {
            stageCalls++;stagedPdu=pdu.copyOf();if(throwStage)throw IllegalStateException("stage")
            if(rejectStage)return MmsSendPduStager.Result.Rejected("MMS_PDU_STAGE_FAILED")
            val token="12345678-1234-1234-1234-123456789012"
            return MmsSendPduStager.Result.Staged(token,"$token.pdu",Uri.parse("content://test/$token.pdu"))
        }
        override fun delete(fileName:String):Boolean {deleteCalls++;return true}
        override fun repairJournal()=0
        override fun persistOutgoingOutbox(token:String,transactionId:String,destination:String,text:String,attachments:List<MmsConversationStore.Attachment>,subscriptionId:Int):MmsConversationStore.PersistResult {
            persistenceCalls++;providerSubscription=subscriptionId;providerParts=attachments;providerTransaction=transactionId;providerDestination=destination;providerText=text
            afterPersist?.invoke();if(throwProvider)throw IllegalStateException("provider uncertain")
            return if(rejectProvider)MmsConversationStore.PersistResult.Rejected("MMS_PROVIDER_FAILED",true) else MmsConversationStore.PersistResult.Ready(42)
        }
        override fun markSubmitted(token:String,providerMessageId:Long):Boolean {submitted++;return true}
        override fun markSubmissionUnknown(token:String,providerMessageId:Long):Boolean {unknown++;return true}
        override fun abandonBeforeTransport(token:String,providerMessageId:Long):Boolean {abandoned++;return true}
    }
    private fun sender(fake:Fake)=SentinelMmsSender(RuntimeEnvironment.getApplication(),fake)
    private fun image(size:Int)=SentinelMmsSender.Attachment("image/jpeg",ByteArray(size){17})
    private fun assertNoEffects(fake:Fake) {assertEquals(0,fake.stageCalls);assertEquals(0,fake.persistenceCalls);assertEquals(0,fake.sendCalls)}
    private fun realJpeg(): ByteArray {
        val bitmap=android.graphics.Bitmap.createBitmap(800,600,android.graphics.Bitmap.Config.ARGB_8888)
        try {
            val pixels=IntArray(800*600){i->val x=i%800;val y=i/800;android.graphics.Color.rgb((x*37+y*17)and 255,(x*11+y*43)and 255,(x*29+y*7)and 255)}
            bitmap.setPixels(pixels,0,800,0,0,800,600)
            return java.io.ByteArrayOutputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,95,it));it.toByteArray() }
        } finally { bitmap.recycle() }
    }
    @Test fun realJpegValidationAndCompressionKeepProviderAndPduIdentical() {
        val f=Fake();f.realPreparation=true
        val original=realJpeg();val before=original.copyOf()
        val result=sender(f).send("+33612345678","real JPEG",1,listOf(SentinelMmsSender.Attachment("image/jpeg",original)))
        assertTrue("$result",result.accepted);assertEquals(1,f.sendCalls);assertEquals(1,f.persistenceCalls)
        assertArrayEquals(before,original)
        val bytes=f.providerParts.single().payload
        assertTrue(bytes.size<original.size)
        assertEquals(com.sentinel.quantum.mmsjpeg.StrictJpegValidator.VALID,com.sentinel.quantum.mmsjpeg.StrictJpegValidator.validate(bytes))
        val encoded=SentinelMmsSendPduComposer.compose(f.providerDestination,f.providerTransaction,f.providerText,f.providerParts.map{SentinelMmsSendPduComposer.Part(it.mimeType,it.payload)},f.limit) as SentinelMmsSendPduComposer.Result.Composed
        assertArrayEquals(encoded.pdu,f.stagedPdu);assertTrue(encoded.pdu.size<=f.limit)
    }
    @Test fun realTruncatedEntropyNeverStagesPersistsOrSubmits() {
        val input=realJpeg();val corrupt=input.copyOf(input.size/2)+byteArrayOf(0xff.toByte(),0xd9.toByte())
        val before=corrupt.copyOf();val f=Fake();f.realPreparation=true
        val result=sender(f).send("+33612345678","corrupt JPEG",1,listOf(SentinelMmsSender.Attachment("image/jpeg",corrupt)))
        assertFalse(result.accepted);assertEquals("MMS_JPEG_INVALID_JPEG",result.reason)
        assertNoEffects(f);assertArrayEquals(before,corrupt)
    }
    @Test fun absentRoleRejectsBeforePreparation() {val f=Fake();f.role=false;assertEquals("SMS_ROLE_NOT_HELD",sender(f).send("+33612345678","test").reason);assertEquals(0,f.preparationCalls);assertNoEffects(f)}
    @Test fun missingAndIneffectivePermissionsRefuseWithoutEffects() {
        for(p in listOf(Manifest.permission.SEND_SMS,Manifest.permission.READ_PHONE_STATE)) {
            val f=Fake();f.missingPermission=p;assertFalse(sender(f).send("+33612345678","test").accepted);assertNoEffects(f)
            val g=Fake();g.ineffectivePermission=p;assertFalse(sender(g).send("+33612345678","test").accepted);assertNoEffects(g)
        }
    }
    @Test fun invalidSubscriptionRefusesBeforePrepare() {val f=Fake();assertFalse(sender(f).send("+33612345678","x",99).accepted);assertNoEffects(f);assertEquals(0,f.preparationCalls)}
    @Test fun missingCarrierConfigRefusesBeforePrepare() {val f=Fake();f.limit=0;assertEquals("MMS_CARRIER_CONFIG_UNAVAILABLE",sender(f).send("+33612345678","x").reason);assertNoEffects(f)}
    @Test fun compressedTransportAndProviderUseExactlySameDataAndTransaction() {
        val f=Fake();val original=image(600000);val before=original.payload.copyOf()
        val result=sender(f).send("+33612345678","exact text",1,listOf(original))
        assertTrue("$result",result.accepted);assertEquals(1,f.preparationCalls);assertEquals(1,f.stageCalls);assertEquals(1,f.persistenceCalls);assertEquals(1,f.sendCalls);assertEquals(1,f.submitted)
        assertTrue(f.providerParts.single().payload.size<original.payload.size);assertArrayEquals(before,original.payload)
        val expected=SentinelMmsSendPduComposer.compose(f.providerDestination,f.providerTransaction,f.providerText,f.providerParts.map{SentinelMmsSendPduComposer.Part(it.mimeType,it.payload)},f.limit) as SentinelMmsSendPduComposer.Result.Composed
        assertArrayEquals(expected.pdu,f.stagedPdu);assertEquals(f.preparedTransaction,f.providerTransaction);assertEquals("+33612345678",f.providerDestination);assertEquals("exact text",f.providerText)
    }
    @Test fun textOnlyUsesPreparationWithoutDoubleSend() {val f=Fake();assertTrue(sender(f).send("+33612345678","x").accepted);assertEquals(1,f.preparationCalls);assertEquals(1,f.sendCalls)}
    @Test fun rejectedPreparationNeverStagesOrPersists() {val f=Fake();f.preparationFailure="MMS_JPEG_INVALID_JPEG";assertEquals("MMS_JPEG_INVALID_JPEG",sender(f).send("+33612345678","x",1,listOf(image(100))).reason);assertNoEffects(f)}
    @Test fun roleRevokedDuringPreparationRejectsBeforeStage() {val f=Fake();f.beforePrepared={f.role=false};assertEquals("SMS_ROLE_NOT_HELD",sender(f).send("+33612345678","x",1,listOf(image(100))).reason);assertNoEffects(f)}
    @Test fun permissionRevokedDuringPreparationRejectsBeforeStage() {val f=Fake();f.beforePrepared={f.missingPermission=Manifest.permission.SEND_SMS};assertFalse(sender(f).send("+33612345678","x",1,listOf(image(100))).accepted);assertNoEffects(f)}
    @Test fun carrierLimitShrinkingDuringPreparationRejectsBeforeStage() {val f=Fake();f.beforePrepared={f.limit=10};assertEquals("MMS_CARRIER_SIZE_EXCEEDED",sender(f).send("+33612345678","x",1,listOf(image(100))).reason);assertNoEffects(f)}
    @Test fun stageFailureDoesNotWriteProvider() {val f=Fake();f.rejectStage=true;assertFalse(sender(f).send("+33612345678","x").accepted);assertEquals(0,f.persistenceCalls);assertEquals(0,f.sendCalls)}
    @Test fun providerRefusalDeletesStageAndNeverSends() {val f=Fake();f.rejectProvider=true;assertFalse(sender(f).send("+33612345678","x").accepted);assertEquals(1,f.deleteCalls);assertEquals(0,f.sendCalls)}
    @Test fun transportExceptionKeepsRecoveryStateAndNeverRetries() {val f=Fake();f.throwTransport=true;assertEquals("MMS_SUBMISSION_OUTCOME_UNKNOWN",sender(f).send("+33612345678","x").reason);assertEquals(1,f.sendCalls);assertEquals(1,f.unknown);assertEquals(0,f.deleteCalls);assertEquals(0,f.abandoned)}
    @Test fun preparationExceptionFailsClosedBeforeEffects() {val f=Fake();f.throwPreparation=true;val result=runCatching{sender(f).send("+33612345678","x")};assertTrue(result.isSuccess);assertFalse(result.getOrThrow().accepted);assertNoEffects(f)}
    @Test fun stageExceptionFailsClosedBeforeProvider() {val f=Fake();f.throwStage=true;val result=runCatching{sender(f).send("+33612345678","x")};assertTrue(result.isSuccess);assertFalse(result.getOrThrow().accepted);assertEquals(0,f.persistenceCalls);assertEquals(0,f.sendCalls)}

    @Test fun providerExceptionDoesNotSubmitAndKeepsFailureExplicit() {val f=Fake();f.throwProvider=true;val result=runCatching{sender(f).send("+33612345678","x")};assertTrue(result.isSuccess);assertFalse(result.getOrThrow().accepted);assertEquals(0,f.sendCalls);assertEquals(1,f.deleteCalls)}
    @Test fun roleLostAfterPersistenceNeverCallsTransportAndCompensates() {val f=Fake();f.afterPersist={f.role=false};assertFalse(sender(f).send("+33612345678","x").accepted);assertEquals(0,f.sendCalls);assertEquals(1,f.abandoned);assertEquals(1,f.deleteCalls)}
    @Test fun removedSubscriptionDuringCompressionNeverStages() {val f=Fake();f.beforePrepared={f.active=emptySet()};assertFalse(sender(f).send("+33612345678","x").accepted);assertNoEffects(f)}
    @Test fun carrierLookupExceptionRefusesWithoutEffects() {val f=Fake();f.throwCarrier=true;assertEquals("MMS_CARRIER_CONFIG_UNAVAILABLE",sender(f).send("+33612345678","x").reason);assertNoEffects(f)}
    @Test fun defaultSubscriptionExceptionFailsClosed() {val f=Fake();f.throwDefault=true;val result=runCatching{sender(f).send("+33612345678","x")};assertTrue(result.isSuccess);assertFalse(result.getOrThrow().accepted);assertNoEffects(f)}
    @Test fun preparationMemoryFailureNeverStages() {val f=Fake();f.throwPrepareMemory=true;val result=runCatching{sender(f).send("+33612345678","x")};assertTrue(result.isSuccess);assertFalse(result.getOrThrow().accepted);assertNoEffects(f)}

    @Test fun explicitSecondSimControlsBudgetPersistenceAndTransportTogether() {
        val f=Fake();f.active=setOf(1,2);f.limits[1]=307200;f.limits[2]=20000
        assertTrue(sender(f).send("+33612345678","x",2,listOf(image(600000))).accepted)
        assertEquals(listOf(2),f.carrierRequests);assertEquals(20000,f.prepareBudget);assertEquals(2,f.providerSubscription);assertEquals(listOf(2),f.transportSubscriptions);assertTrue(requireNotNull(f.stagedPdu).size<=20000)
    }
}

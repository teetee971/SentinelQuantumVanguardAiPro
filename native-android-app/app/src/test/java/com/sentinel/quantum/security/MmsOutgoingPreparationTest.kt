package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class MmsOutgoingPreparationTest {
    private val destination = "+33612345678"
    private val transaction = "unique-transaction"
    private class Codec {
        val budgets = mutableListOf<Int>()
        val inputs = mutableListOf<ByteArray>()
        fun compress(bytes:ByteArray,budget:Int):MmsOutgoingJpegCompressor.Result {
            budgets += budget;inputs += bytes
            return MmsOutgoingJpegCompressor.Result.Compressed(if(bytes.size<=budget)bytes else ByteArray(budget){42})
        }
    }
    private fun jpeg(size:Int)=SentinelMmsSendPduComposer.Part("image/jpeg",ByteArray(size){17})
    private fun png(size:Int)=SentinelMmsSendPduComposer.Part("image/png",ByteArray(size){23})
    private fun ready(result:MmsOutgoingPreparation.Result):MmsOutgoingPreparation.Result.Ready {
        assertTrue("$result",result is MmsOutgoingPreparation.Result.Ready)
        return result as MmsOutgoingPreparation.Result.Ready
    }
    @Test fun textOnlyNeverCallsImageCodec() {
        val codec=Codec();val result=ready(MmsOutgoingPreparation.prepare(destination,transaction,"bonjour",emptyList(),1000,codec::compress))
        assertTrue(result.attachments.isEmpty());assertTrue(codec.budgets.isEmpty());assertTrue(result.pdu.toString(Charsets.ISO_8859_1).contains("bonjour"))
    }
    @Test fun conformingJpegPreservesBytesWithoutEncoding() {
        val original=jpeg(100);val codec=Codec();val result=ready(MmsOutgoingPreparation.prepare(destination,transaction,"x",listOf(original),1000,codec::compress))
        assertArrayEquals(original.payload,result.attachments.single().payload)
        assertTrue(codec.budgets.all { it>=100 })
    }
    @Test fun largeJpegUsesAvailableCapacityOnce() {
        val codec=Codec();val result=ready(MmsOutgoingPreparation.prepare(destination,transaction,"x",listOf(jpeg(600000)),20000,codec::compress))
        assertEquals(1,codec.budgets.size);assertTrue(codec.budgets.single()>19000);assertTrue(result.pdu.size<=20000)
    }
    @Test fun smallJpegRetainsItsBudgetWhileLargeOnesShareRemainder() {
        val small=jpeg(100);val codec=Codec();val result=ready(MmsOutgoingPreparation.prepare(destination,transaction,"x",listOf(jpeg(600000),small,jpeg(600000)),100000,codec::compress))
        assertArrayEquals(small.payload,result.attachments[1].payload);assertTrue(result.attachments[0].payload.size>49000);assertTrue(result.attachments[2].payload.size>49000)
        assertEquals(3,codec.budgets.size);assertTrue(result.pdu.size<=100000)
    }
    @Test fun mixedPngConsumesFixedBudgetBeforeJpegCompression() {
        val fixed=png(45000);val codec=Codec();val result=ready(MmsOutgoingPreparation.prepare(destination,transaction,"x",listOf(jpeg(600000),fixed),50000,codec::compress))
        assertArrayEquals(fixed.payload,result.attachments[1].payload);assertEquals(1,codec.budgets.size);assertTrue(codec.budgets.single() in 4800..5000);assertTrue(result.pdu.size<=50000)
    }
    @Test fun impossibleFixedPayloadRejectsWithoutTryingJpeg() {
        val codec=Codec();assertTrue(MmsOutgoingPreparation.prepare(destination,transaction,"x",listOf(png(60000),jpeg(600000)),50000,codec::compress) is MmsOutgoingPreparation.Result.Rejected);assertTrue(codec.budgets.isEmpty())
    }
    @Test fun nonJpegOversizeIsNeverTranscoded() {
        val codec=Codec();assertTrue(MmsOutgoingPreparation.prepare(destination,transaction,"",listOf(png(60000)),50000,codec::compress) is MmsOutgoingPreparation.Result.Rejected);assertTrue(codec.budgets.isEmpty())
    }
    @Test fun invalidCarrierBudgetsFailBeforeCodec() {
        val codec=Codec();for(budget in listOf(0,-1)) assertEquals("MMS_CARRIER_CONFIG_UNAVAILABLE",(MmsOutgoingPreparation.prepare(destination,transaction,"x",listOf(jpeg(100)),budget,codec::compress) as MmsOutgoingPreparation.Result.Rejected).reason)
        assertTrue(codec.budgets.isEmpty())
    }
    @Test fun finalPduIsExactlyTheCompositionOfPreparedParts() {
        val codec=Codec();val result=ready(MmsOutgoingPreparation.prepare(destination,transaction,"exact text",listOf(jpeg(600000),png(100)),20000,codec::compress))
        val encoded=SentinelMmsSendPduComposer.compose(destination,transaction,"exact text",result.attachments,20000) as SentinelMmsSendPduComposer.Result.Composed
        assertArrayEquals(encoded.pdu,result.pdu);assertTrue(result.pdu.toString(Charsets.ISO_8859_1).contains(destination));assertTrue(result.pdu.toString(Charsets.ISO_8859_1).contains("exact text"))
    }
    @Test fun originalArraysAreIsolatedFromCodecMutation() {
        val original=jpeg(600000);val copy=original.payload.copyOf()
        ready(MmsOutgoingPreparation.prepare(destination,transaction,"x",listOf(original),20000){bytes,budget->bytes.fill(99);MmsOutgoingJpegCompressor.Result.Compressed(ByteArray(minOf(budget,1000)){42})})
        assertArrayEquals(copy,original.payload)
    }
    @Test fun codecRefusalReasonIsNotMaskedAsCarrierSize() {
        val result=MmsOutgoingPreparation.prepare(destination,transaction,"x",listOf(jpeg(600000)),20000){_,_->MmsOutgoingJpegCompressor.Result.Rejected("INVALID_JPEG")}
        assertEquals("MMS_JPEG_INVALID_JPEG",(result as MmsOutgoingPreparation.Result.Rejected).reason)
    }
    @Test fun codecCannotReturnMoreThanItsBudget() {
        val result=MmsOutgoingPreparation.prepare(destination,transaction,"x",listOf(jpeg(600000)),20000){_,budget->MmsOutgoingJpegCompressor.Result.Compressed(ByteArray(budget+1))}
        assertEquals("MMS_JPEG_BUDGET_EXCEEDED",(result as? MmsOutgoingPreparation.Result.Rejected)?.reason)
    }
    @Test fun codecExceptionsRejectWithoutEscaping() {
        val result=runCatching { MmsOutgoingPreparation.prepare(destination,transaction,"x",listOf(jpeg(600000)),20000){_,_->throw IllegalStateException("decoder")}}
        assertTrue(result.isSuccess);assertTrue(result.getOrNull() is MmsOutgoingPreparation.Result.Rejected)
    }
    @Test fun hugeCarrierBudgetDoesNotOverflowOrTransformSmallInputs() {
        val codec=Codec();val result=ready(MmsOutgoingPreparation.prepare(destination,transaction,"x",listOf(jpeg(100)),Int.MAX_VALUE,codec::compress));assertEquals(100,result.attachments.single().payload.size)
    }

    @Test fun gifAndWebpBytesAreNeverSentToJpegCodecOrChanged() {
        val parts=listOf(SentinelMmsSendPduComposer.Part("image/gif","GIF89a-payload".toByteArray()),SentinelMmsSendPduComposer.Part("image/webp","RIFF0000WEBPpayload".toByteArray()))
        val codec=Codec();val result=ready(MmsOutgoingPreparation.prepare(destination,transaction,"x",parts,1000,codec::compress))
        assertTrue(codec.budgets.isEmpty());for(index in parts.indices)assertArrayEquals(parts[index].payload,result.attachments[index].payload)
    }
}

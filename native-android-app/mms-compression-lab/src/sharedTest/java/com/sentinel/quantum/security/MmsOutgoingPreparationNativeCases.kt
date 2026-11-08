package com.sentinel.quantum.security

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

abstract class MmsOutgoingPreparationNativeCases {
    private val destination="+33612345678"
    private fun image(w:Int=1600,h:Int=1200,png:Boolean=false):ByteArray {
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        try {
            val pixels=IntArray(w*h){i->val x=i%w;val y=i/w;Color.rgb((x*37+y*17)and 255,(x*11+y*43)and 255,(x*29+y*7)and 255)}
            bitmap.setPixels(pixels,0,w,0,0,w,h)
            return ByteArrayOutputStream().use {check(bitmap.compress(if(png)Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,95,it));it.toByteArray()}
        } finally {bitmap.recycle()}
    }
    private fun prepare(parts:List<SentinelMmsSendPduComposer.Part>,budget:Int=307200):MmsOutgoingPreparation.Result.Ready {
        val result=MmsOutgoingPreparation.prepare(destination,"native-case","Texte préservé",parts,budget)
        assertTrue("$result",result is MmsOutgoingPreparation.Result.Ready)
        result as MmsOutgoingPreparation.Result.Ready
        assertTrue(result.pdu.size<=budget)
        val encoded=SentinelMmsSendPduComposer.compose(destination,"native-case","Texte préservé",result.attachments,budget) as SentinelMmsSendPduComposer.Result.Composed
        assertArrayEquals(encoded.pdu,result.pdu)
        return result
    }
    @Test fun actualLargeJpegFitsCarrierAndOriginalIsUntouched() {
        val original=image();val before=original.copyOf();assertTrue(original.size>307200)
        val result=prepare(listOf(SentinelMmsSendPduComposer.Part("image/jpeg",original)))
        assertTrue(result.attachments.single().payload.size<original.size);assertArrayEquals(before,original)
    }
    @Test fun multipleRealJpegsAndPngKeepOrderAndFixedBytes() {
        val first=image();val second=image(1200,1600);val png=image(48,48,true)
        val result=prepare(listOf(SentinelMmsSendPduComposer.Part("image/jpeg",first),SentinelMmsSendPduComposer.Part("image/png",png),SentinelMmsSendPduComposer.Part("image/jpeg",second)))
        assertEquals(listOf("image/jpeg","image/png","image/jpeg"),result.attachments.map{it.mimeType})
        assertArrayEquals(png,result.attachments[1].payload)
    }
    @Test fun smallRealJpegIsNotReencoded() {
        val small=image(64,48);val result=prepare(listOf(SentinelMmsSendPduComposer.Part("image/jpeg",small)))
        assertArrayEquals(small,result.attachments.single().payload)
    }
    @Test fun corruptFittingJpegIsRefusedBeforeAnyTransport() {
        val result=MmsOutgoingPreparation.prepare(destination,"bad-case","text",listOf(SentinelMmsSendPduComposer.Part("image/jpeg",byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte()))),307200)
        assertTrue(result is MmsOutgoingPreparation.Result.Rejected)
    }
}

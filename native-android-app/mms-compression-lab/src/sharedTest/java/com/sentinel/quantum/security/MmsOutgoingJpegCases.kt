package com.sentinel.quantum.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

abstract class MmsOutgoingJpegCases {
    @Test fun malformedSmallJpegMustNotPassThrough() {
        assertTrue(MmsOutgoingJpegCompressor.compress(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()), 1000) is MmsOutgoingJpegCompressor.Result.Rejected)
    }
    @Test fun conformingJpegRemainsByteIdentical() {
        val input = jpeg(240, 160)
        assertArrayEquals(input, compressed(input, input.size))
    }
    @Test fun truncatedJpegIsRejectedEvenWithLargeBudget() {
        val input = jpeg(240, 160)
        assertTrue(MmsOutgoingJpegCompressor.compress(input.copyOf(input.size - 10), input.size) is MmsOutgoingJpegCompressor.Result.Rejected)
    }
    @Test fun truncatedEntropyWithAnAppendedEndMarkerIsRejected() {
        val input=jpeg(800,600,true)
        val truncated=input.copyOf(input.size/2)+byteArrayOf(0xff.toByte(),0xd9.toByte())
        assertTrue("Incomplete entropy stream accepted",MmsOutgoingJpegCompressor.compress(truncated,input.size) is MmsOutgoingJpegCompressor.Result.Rejected)
    }
    @Test fun artificialEndMarkersAtDifferentTruncationPointsAreRejected() {
        val input = jpeg(800, 600, true)
        assertArrayEquals(input, compressed(input, input.size))
        for (percent in listOf(10, 25, 50, 75, 90, 99)) {
            val truncated = input.copyOf(input.size * percent / 100) + byteArrayOf(0xff.toByte(), 0xd9.toByte())
            assertTrue("Incomplete Android-generated JPEG accepted at $percent%",
                MmsOutgoingJpegCompressor.compress(truncated, input.size) is MmsOutgoingJpegCompressor.Result.Rejected)
        }
    }
    @Test fun hugeBudgetNeverAllocatesAHugeOutputBuffer() {
        val input=jpeg(80,60);assertArrayEquals(input,compressed(input,Int.MAX_VALUE))
    }
    @Test fun oversizedInputRejectedBeforeDecoding() {
        assertTrue(MmsOutgoingJpegCompressor.compress(ByteArray(5*1024*1024+1),307200) is MmsOutgoingJpegCompressor.Result.Rejected)
    }
    @Test fun budgetsZeroNegativeAndTooSmallAreRejected() {
        val input = jpeg(100, 80)
        for (budget in listOf(0, -1, 1)) assertTrue(MmsOutgoingJpegCompressor.compress(input, budget) is MmsOutgoingJpegCompressor.Result.Rejected)
    }
    @Test fun portraitAndLandscapeFitWithoutChangingAspectRatio() {
        for ((w,h) in listOf(1600 to 1200, 1200 to 1600)) {
            val input = jpeg(w,h, noise = true)
            val output = compressed(input, 40_000)
            assertTrue(output.size <= 40_000)
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(output,0,output.size))
            try { assertEquals(w.toDouble()/h, bitmap.width.toDouble()/bitmap.height, 0.02) }
            finally { bitmap.recycle() }
        }
    }
    @Test fun transformedImageDoesNotMutateOriginalAndKeepsJpeg() {
        val input=jpeg(800,600, true);val original=input.copyOf();val out=compressed(input,30_000)
        assertArrayEquals(original,input);assertEquals(0xff,out[0].toInt() and 255);assertEquals(0xd8,out[1].toInt() and 255)
        assertEquals(0xff,out[out.size-2].toInt() and 255);assertEquals(0xd9,out.last().toInt() and 255)
    }
    @Test fun decodedSourcePixelBombIsRejectedBeforeBitmapAllocation() {
        val input=jpeg(100,80)
        val patched=input.copyOf()
        var pos=2
        while(pos<patched.size-8) {
            val marker=patched[pos+1].toInt() and 255
            if(marker==0xc0 || marker==0xc2) { patched[pos+5]=0x7f;patched[pos+6]=0xff.toByte();patched[pos+7]=0x7f;patched[pos+8]=0xff.toByte();break }
            val len=((patched[pos+2].toInt() and 255) shl 8) or (patched[pos+3].toInt() and 255);pos+=2+len
        }
        assertTrue(MmsOutgoingJpegCompressor.compress(patched,patched.size) is MmsOutgoingJpegCompressor.Result.Rejected)
    }
    @Test fun invalidExifOrientationFailsClosed() {
        val input=withExif(jpeg(240,160),9)
        assertTrue(MmsOutgoingJpegCompressor.compress(input,input.size) is MmsOutgoingJpegCompressor.Result.Rejected)
    }
    @Test fun allExifRotationsAndMirrorsAreBakedIntoPixels() {
        val colors=intArrayOf(Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW)
        val expected=listOf(intArrayOf(0,1,2,3),intArrayOf(1,0,3,2),intArrayOf(3,2,1,0),intArrayOf(2,3,0,1),intArrayOf(0,2,1,3),intArrayOf(2,0,3,1),intArrayOf(3,1,2,0),intArrayOf(1,3,0,2))
        for (orientation in 1..8) {
            val input=withExif(jpeg(240,160),orientation)
            val out=compressed(input,input.size-1)
            val bitmap=requireNotNull(BitmapFactory.decodeByteArray(out,0,out.size))
            try {
                assertEquals(if(orientation>=5)160 else 240,bitmap.width)
                assertEquals(if(orientation>=5)240 else 160,bitmap.height)
                val points=listOf(20 to 20,bitmap.width-21 to 20,20 to bitmap.height-21,bitmap.width-21 to bitmap.height-21)
                for ((i,point) in points.withIndex()) {
                    val pixel=bitmap.getPixel(point.first,point.second);val wanted=colors[expected[orientation-1][i]]
                    assertTrue("orientation=$orientation corner=$i",kotlin.math.abs(Color.red(pixel)-Color.red(wanted))<45 && kotlin.math.abs(Color.green(pixel)-Color.green(wanted))<45 && kotlin.math.abs(Color.blue(pixel)-Color.blue(wanted))<45)
                }
            } finally { bitmap.recycle() }
        }
    }
    @Test fun interruptionRejectsWithoutClearingFlag() {
        val input=jpeg(100,80);Thread.currentThread().interrupt()
        try { assertEquals("INTERRUPTED",(MmsOutgoingJpegCompressor.compress(input,500) as? MmsOutgoingJpegCompressor.Result.Rejected)?.reason);assertTrue(Thread.currentThread().isInterrupted) }
        finally { Thread.interrupted() }
    }
    @Test fun legitimateProgressiveAndRestartStreamsRemainByteIdentical() {
        // libjpeg-turbo 3.2.1 cjpeg -quality 90, with -progressive / -restart 1B.
        // Deterministic 96x64 RGB fixture; exercises real multi-scan and restart entropy.
        val fixtures = listOf(
            "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")
        for (encoded in fixtures) {
            val input = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
            assertArrayEquals(input, compressed(input, input.size))
        }
    }
    @Test fun nativeValidationIsIndependentAcrossConcurrentCalls() {
        val input = jpeg(240, 160, true)
        val bad = input.copyOf(input.size / 2) + byteArrayOf(0xff.toByte(), 0xd9.toByte())
        val executor = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val results = (0 until 20).map { index -> executor.submit<Boolean> {
                val valid = index % 2 == 0
                val result = com.sentinel.quantum.mmsjpeg.StrictJpegValidator.validate(if (valid) input else bad)
                result == if (valid) com.sentinel.quantum.mmsjpeg.StrictJpegValidator.VALID
                    else com.sentinel.quantum.mmsjpeg.StrictJpegValidator.INVALID
            } }
            results.forEach { assertTrue(it.get(10, java.util.concurrent.TimeUnit.SECONDS)) }
        } finally { executor.shutdownNow() }
    }
    private fun compressed(input:ByteArray,budget:Int):ByteArray {
        val result=MmsOutgoingJpegCompressor.compress(input,budget)
        assertTrue("$result",result is MmsOutgoingJpegCompressor.Result.Compressed)
        return (result as MmsOutgoingJpegCompressor.Result.Compressed).bytes
    }
    private fun jpeg(w:Int,h:Int,noise:Boolean=false):ByteArray {
        val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        try {
            val pixels=IntArray(w*h) { index -> val x=index%w;val y=index/w
                if(noise)Color.rgb((x*37+y*17)and 255,(x*11+y*43)and 255,(x*29+y*7)and 255)
                else when {y<h/2 && x<w/2->Color.RED;y<h/2->Color.GREEN;x<w/2->Color.BLUE;else->Color.YELLOW}
            };b.setPixels(pixels,0,w,0,0,w,h)
            return ByteArrayOutputStream().use {check(b.compress(Bitmap.CompressFormat.JPEG,95,it));it.toByteArray()}
        } finally {b.recycle()}
    }
    private fun withExif(input:ByteArray,orientation:Int):ByteArray {
        val data=ByteBuffer.allocate(32 + 4096).order(ByteOrder.LITTLE_ENDIAN)
        data.put("Exif\u0000\u0000".toByteArray());data.put('I'.code.toByte());data.put('I'.code.toByte());data.putShort(42);data.putInt(8);data.putShort(1);data.putShort(0x112);data.putShort(3);data.putInt(1);data.putShort(orientation.toShort());data.putShort(0);data.putInt(0)
        return byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte(),0xe1.toByte(),0x10,0x22)+data.array()+input.copyOfRange(2,input.size)
    }
}

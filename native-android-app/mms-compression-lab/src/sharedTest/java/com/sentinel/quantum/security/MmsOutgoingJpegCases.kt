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

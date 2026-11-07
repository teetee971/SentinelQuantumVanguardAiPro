package com.sentinel.quantum.security
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[27],manifest=Config.NONE)
class MmsOutgoingJpegLegacyTest {
    private fun input():ByteArray {
        val b=Bitmap.createBitmap(64,48,Bitmap.Config.ARGB_8888)
        try {b.eraseColor(android.graphics.Color.BLUE);return ByteArrayOutputStream().use{check(b.compress(Bitmap.CompressFormat.JPEG,90,it));it.toByteArray()}}
        finally{b.recycle()}
    }
    @Test fun conformingOriginalKeepsLegacyBehavior() {
        val bytes=input();val result=MmsOutgoingJpegCompressor.compress(bytes,bytes.size)
        assertTrue("$result",result is MmsOutgoingJpegCompressor.Result.Compressed)
        assertArrayEquals(bytes,(result as MmsOutgoingJpegCompressor.Result.Compressed).bytes)
    }
    @Test fun newTranscodingFailsClosedWithoutStrictDecoder() {
        assertEquals("STRICT_DECODER_UNAVAILABLE",(MmsOutgoingJpegCompressor.compress(input(),1) as MmsOutgoingJpegCompressor.Result.Rejected).reason)
    }
}

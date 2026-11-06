package com.sentinel.quantum.security

import android.database.Cursor
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MmsPartCursorReaderTest {
    @Test
    fun singleProviderRowReturnsMetadataBeforeCursorPassesEnd() {
        assertEquals(
            MmsImagePartContract.PartMetadata(7L, 42L, "image/png"),
            readUniqueMmsPartMetadata(cursor(listOf(listOf(7L, 42L, "image/png"))))
        )
    }

    @Test
    fun emptyProviderResultHasNoMetadata() {
        assertNull(readUniqueMmsPartMetadata(cursor(emptyList())))
    }

    @Test
    fun duplicateProviderRowsAreRejectedEvenWhenIdentical() {
        val row = listOf(7L, 42L, "image/png")
        assertNull(readUniqueMmsPartMetadata(cursor(listOf(row, row))))
    }

    @Test
    fun missingMimeIsPreservedForContractRejection() {
        assertEquals(
            MmsImagePartContract.PartMetadata(7L, 42L, null),
            readUniqueMmsPartMetadata(cursor(listOf(listOf(7L, 42L, null))))
        )
    }

    // Provider boundary double: preserves Android Cursor's after-last position.
    // Android's host jar has no functional MatrixCursor implementation.
    private fun cursor(rows: List<List<Any?>>): Cursor {
        var position = -1
        return Proxy.newProxyInstance(Cursor::class.java.classLoader, arrayOf(Cursor::class.java)) { _, method, args ->
            when (method.name) {
                "getColumnIndexOrThrow" -> listOf("_id", "mid", "ct").indexOf(args!![0]).also {
                    require(it >= 0) { "Unknown column" }
                }
                "moveToFirst" -> { position = 0; rows.isNotEmpty() }
                "moveToNext" -> { position++; position < rows.size }
                "getLong", "getString" -> {
                    check(position in rows.indices) { "Cursor read outside a row: $position" }
                    rows[position][args!![0] as Int]
                }
                else -> error("Unexpected cursor operation: ${method.name}")
            }
        } as Cursor
    }
}

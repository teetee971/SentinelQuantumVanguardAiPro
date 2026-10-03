package com.sentinel.quantum.security

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Bounded allowlist of IANA MIB charsets used by Android's MMS stack. Missing charset follows the
 * Android MMS default of UTF-8. The result is Unicode text; provider projection normalizes it to
 * canonical UTF-8 rather than pretending the decoded String still carries its source byte encoding.
 */
internal object MmsTextCharset {
    data class Decoded(val text: String)

    fun decode(bytes: ByteArray, mibEnum: Int?): Decoded? {
        if (bytes.isEmpty()) return null
        val normalized = mibEnum ?: UTF_8
        val charset = runCatching {
            when (normalized) {
                US_ASCII -> Charsets.US_ASCII
                ISO_8859_1 -> Charsets.ISO_8859_1
                ISO_8859_2 -> Charset.forName("ISO-8859-2")
                ISO_8859_3 -> Charset.forName("ISO-8859-3")
                ISO_8859_4 -> Charset.forName("ISO-8859-4")
                ISO_8859_5 -> Charset.forName("ISO-8859-5")
                ISO_8859_6 -> Charset.forName("ISO-8859-6")
                ISO_8859_7 -> Charset.forName("ISO-8859-7")
                ISO_8859_8 -> Charset.forName("ISO-8859-8")
                ISO_8859_9 -> Charset.forName("ISO-8859-9")
                SHIFT_JIS -> Charset.forName("Shift_JIS")
                UTF_8 -> Charsets.UTF_8
                BIG5 -> Charset.forName("Big5")
                UCS2 -> Charset.forName("UTF-16BE")
                UTF_16 -> Charset.forName("UTF-16")
                else -> return null
            }
        }.getOrNull() ?: return null
        val text = runCatching {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull() ?: return null
        if (text.indexOf('\u0000') >= 0) return null
        return Decoded(text)
    }

    fun mibEnumForName(value: String): Int? = when (
        value.trim().lowercase().replace('_', '-')
    ) {
        "us-ascii", "ascii" -> US_ASCII
        "iso-8859-1", "latin1", "latin-1" -> ISO_8859_1
        "iso-8859-2" -> ISO_8859_2
        "iso-8859-3" -> ISO_8859_3
        "iso-8859-4" -> ISO_8859_4
        "iso-8859-5" -> ISO_8859_5
        "iso-8859-6" -> ISO_8859_6
        "iso-8859-7" -> ISO_8859_7
        "iso-8859-8" -> ISO_8859_8
        "iso-8859-9" -> ISO_8859_9
        "shift-jis", "shift-jis", "sjis" -> SHIFT_JIS
        "utf-8", "utf8" -> UTF_8
        "big5" -> BIG5
        "iso-10646-ucs-2", "ucs-2", "ucs2", "utf-16be" -> UCS2
        "utf-16", "utf16" -> UTF_16
        else -> null
    }

    const val US_ASCII = 0x03
    const val ISO_8859_1 = 0x04
    const val ISO_8859_2 = 0x05
    const val ISO_8859_3 = 0x06
    const val ISO_8859_4 = 0x07
    const val ISO_8859_5 = 0x08
    const val ISO_8859_6 = 0x09
    const val ISO_8859_7 = 0x0a
    const val ISO_8859_8 = 0x0b
    const val ISO_8859_9 = 0x0c
    const val SHIFT_JIS = 0x11
    const val UTF_8 = 0x6a
    const val UCS2 = 0x03e8
    const val UTF_16 = 0x03f7
    const val BIG5 = 0x07ea
}

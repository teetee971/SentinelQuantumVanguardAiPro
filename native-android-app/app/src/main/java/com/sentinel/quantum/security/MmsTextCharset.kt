package com.sentinel.quantum.security

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Small allowlist of MMS text charsets that can be decoded deterministically and persisted with the
 * original IANA MIB enum. Missing charset follows Android's MMS default of UTF-8.
 */
internal object MmsTextCharset {
    data class Decoded(
        val text: String,
        val providerMibEnum: Int
    )

    fun decode(bytes: ByteArray, mibEnum: Int?): Decoded? {
        if (bytes.isEmpty()) return null
        val normalized = mibEnum ?: UTF_8
        val charset = when (normalized) {
            US_ASCII -> Charsets.US_ASCII
            ISO_8859_1 -> Charsets.ISO_8859_1
            UTF_8 -> Charsets.UTF_8
            UCS2 -> Charset.forName("UTF-16BE")
            UTF_16 -> Charset.forName("UTF-16")
            else -> return null
        }
        val text = runCatching {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull() ?: return null
        if (text.indexOf('\u0000') >= 0) return null
        return Decoded(text, normalized)
    }

    const val US_ASCII = 0x03
    const val ISO_8859_1 = 0x04
    const val UTF_8 = 0x6a
    const val UCS2 = 0x03e8
    const val UTF_16 = 0x03f7
}

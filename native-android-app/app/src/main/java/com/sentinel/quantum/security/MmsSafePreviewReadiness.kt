package com.sentinel.quantum.security

/**
 * Deterministic software self-test for the exact decoder + safety boundary used at runtime.
 * This is intentionally separate from physical carrier/device validation.
 */
object MmsSafePreviewReadiness {
    val softwareValidated: Boolean by lazy {
        val safe = MmsDecodePipeline.decodeAndValidate(
            multipartFixture(
                partContentType = 0x83, // text/plain
                payload = "Sentinel MMS preview".toByteArray(Charsets.UTF_8)
            ),
            SentinelMmsPduDecoder
        )
        val spoofedImage = MmsDecodePipeline.decodeAndValidate(
            multipartFixture(
                partContentType = 0x9e, // image/jpeg
                payload = "not-a-jpeg".toByteArray(Charsets.US_ASCII)
            ),
            SentinelMmsPduDecoder
        )
        safe is MmsDecodePipeline.Result.Accepted &&
            safe.parts.size == 1 &&
            safe.parts.single().mimeType == "text/plain" &&
            spoofedImage is MmsDecodePipeline.Result.Rejected &&
            spoofedImage.reason == "CONTENT_SIGNATURE_MISMATCH"
    }

    internal fun multipartFixture(partContentType: Int, payload: ByteArray): ByteArray {
        require(partContentType in 0x80..0xff)
        require(payload.size in 1..0x7f)
        return byteArrayOf(
            0x84.toByte(), // X-Mms-Content-Type
            0xa3.toByte(), // application/vnd.wap.multipart.mixed
            0x01,          // one part
            0x01,          // one byte of part headers
            payload.size.toByte(),
            partContentType.toByte()
        ) + payload
    }
}

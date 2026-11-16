package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsRelatedPresentationContractTest {
    @Test
    fun aospStyleRelatedTypeAndStartBindTheDeclaredSmilRoot() {
        val pdu = relatedRetrieveConf(startContentId = "<smil>", smilFirst = true)
        val envelope = acceptedEnvelope(pdu)

        val inspected = MmsRelatedPresentationInspector.inspect(pdu, envelope)
        assertTrue(inspected is MmsRelatedPresentationInspector.Result.Ready)
        val metadata = (inspected as MmsRelatedPresentationInspector.Result.Ready).metadata
        assertEquals("<smil>", metadata.startContentId)
        assertEquals("application/smil", metadata.rootContentType)

        val safeParts = safeParts(pdu, envelope)
        assertNull(MmsRelatedPresentationValidator.validate(safeParts, metadata))

        val pipeline = IncomingMmsProjectionPipeline.prepare(
            pdu = pdu,
            digestHex = "a".repeat(64),
            subscriptionId = 0
        )
        assertTrue(pipeline is IncomingMmsProjectionPipeline.Result.Ready)
    }

    @Test
    fun explicitStartMismatchIsQuarantinedInsteadOfPromotingAnotherSmil() {
        val pdu = relatedRetrieveConf(startContentId = "<missing>", smilFirst = true)
        val pipeline = IncomingMmsProjectionPipeline.prepare(
            pdu = pdu,
            digestHex = "b".repeat(64),
            subscriptionId = 0
        )
        assertTrue(pipeline is IncomingMmsProjectionPipeline.Result.Quarantined)
        assertEquals(
            "PRESENTATION:RELATED_START_MISMATCH",
            (pipeline as IncomingMmsProjectionPipeline.Result.Quarantined).reason
        )
    }

    @Test
    fun absentStartUsesFirstBodyPartAndRejectsLaterSmilRoot() {
        val valid = relatedRetrieveConf(startContentId = null, smilFirst = true)
        assertTrue(
            IncomingMmsProjectionPipeline.prepare(valid, "c".repeat(64), 0) is
                IncomingMmsProjectionPipeline.Result.Ready
        )

        val invalid = relatedRetrieveConf(startContentId = null, smilFirst = false)
        val result = IncomingMmsProjectionPipeline.prepare(invalid, "d".repeat(64), 0)
        assertTrue(result is IncomingMmsProjectionPipeline.Result.Quarantined)
        assertEquals(
            "PRESENTATION:RELATED_IMPLICIT_ROOT_NOT_SMIL",
            (result as IncomingMmsProjectionPipeline.Result.Quarantined).reason
        )
    }

    private fun acceptedEnvelope(pdu: ByteArray): MmsRetrieveEnvelopeParser.Envelope {
        val parsed = MmsRetrieveEnvelopeParser.parse(pdu)
        assertTrue(parsed is MmsRetrieveEnvelopeParser.Result.Accepted)
        return (parsed as MmsRetrieveEnvelopeParser.Result.Accepted).envelope
    }

    private fun safeParts(
        pdu: ByteArray,
        envelope: MmsRetrieveEnvelopeParser.Envelope
    ): List<MmsDecodeBoundary.SafePart> {
        val decoded = SentinelMmsPduDecoder.decodeRetrieveBody(pdu, envelope)
        assertTrue(decoded is MmsPduDecoder.DecodeResult.Decoded)
        val boundary = MmsDecodeBoundary.validate(
            (decoded as MmsPduDecoder.DecodeResult.Decoded).parts
        )
        assertTrue(boundary is MmsDecodeBoundary.Result.Accepted)
        return (boundary as MmsDecodeBoundary.Result.Accepted).parts
    }

    private fun relatedRetrieveConf(startContentId: String?, smilFirst: Boolean): ByteArray {
        val smilPayload = "<smil><body><img src=\"cid:img1\"/></body></smil>".toByteArray()
        val imagePayload = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0x01)
        val smilHeader =
            "application/smil".toByteArray(Charsets.US_ASCII) +
                byteArrayOf(0x00, 0xc0.toByte()) +
                "<smil>".toByteArray(Charsets.US_ASCII) +
                byteArrayOf(0x00, 0x8e.toByte()) +
                "presentation.smil".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x00)
        val imageHeader = byteArrayOf(0x9e.toByte(), 0xc0.toByte()) +
            "<img1>".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0x00, 0x8e.toByte()) +
            "photo.jpg".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x00)

        val smilPart = encodedPart(smilHeader, smilPayload)
        val imagePart = encodedPart(imageHeader, imagePayload)
        val body = byteArrayOf(0x02) + if (smilFirst) {
            smilPart + imagePart
        } else {
            imagePart + smilPart
        }

        val contentTypeParameters = buildList<Byte> {
            add(0xb3.toByte()) // application/vnd.wap.multipart.related
            if (startContentId != null) {
                add(0x8a.toByte()) // deprecated start token emitted by Android PduComposer
                addAll(startContentId.toByteArray(Charsets.US_ASCII).toList())
                add(0x00)
            }
            add(0x89.toByte()) // multipart-related type parameter
            addAll("application/smil".toByteArray(Charsets.US_ASCII).toList())
            add(0x00)
        }.toByteArray()
        require(contentTypeParameters.size <= 30)

        val sender = "+590690123456/TYPE=PLMN".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        return byteArrayOf(
            0x8c.toByte(), 0x84.toByte(),
            0x8d.toByte(), 0x92.toByte(),
            0x85.toByte(), 0x04, 0x65, 0x53, 0xf1.toByte(), 0x00,
            0x89.toByte(), (1 + sender.size).toByte(), 0x80.toByte()
        ) + sender + byteArrayOf(
            0x8b.toByte(),
            'm'.code.toByte(), 's'.code.toByte(), 'g'.code.toByte(), '-'.code.toByte(),
            'r'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(), 0x00,
            0x84.toByte(), contentTypeParameters.size.toByte()
        ) + contentTypeParameters + body
    }

    private fun encodedPart(header: ByteArray, payload: ByteArray): ByteArray {
        require(header.size < 128 && payload.size < 128)
        return byteArrayOf(header.size.toByte(), payload.size.toByte()) + header + payload
    }
}

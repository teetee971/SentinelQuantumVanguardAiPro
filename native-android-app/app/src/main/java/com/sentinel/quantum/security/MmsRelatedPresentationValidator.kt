package com.sentinel.quantum.security

/** Binds top-level multipart/related type/start semantics to the decoded body, fail-closed. */
internal object MmsRelatedPresentationValidator {
    fun validate(
        parts: List<MmsDecodeBoundary.SafePart>,
        metadata: MmsRelatedPresentationInspector.Metadata
    ): String? {
        if (metadata.rootContentType != SMIL_MIME) return "RELATED_ROOT_TYPE_UNSUPPORTED"
        val smilIndexes = parts.indices.filter { parts[it].mimeType == SMIL_MIME }
        if (smilIndexes.size != 1) return "RELATED_SMIL_REQUIRED"

        val smilIndex = smilIndexes.single()
        if (metadata.startContentId != null) {
            if (parts[smilIndex].contentId != metadata.startContentId) {
                return "RELATED_START_MISMATCH"
            }
        } else if (smilIndex != 0) {
            // RFC 2387 / Android parser semantics: no start parameter means first body part is root.
            return "RELATED_IMPLICIT_ROOT_NOT_SMIL"
        }

        if (parts.any {
                it.mimeType != SMIL_MIME && it.contentId == null && it.contentLocation == null
            }
        ) {
            return "RELATED_PART_REFERENCE_REQUIRED"
        }
        return null
    }

    private const val SMIL_MIME = "application/smil"
}

package com.sentinel.quantum.security
import org.junit.Assert.*
import org.junit.Test
class MmsComposeDraftSnapshotTest {
    private val selected=listOf("photo-one")
    private val submitted=MmsComposeDraftSnapshot("test-recipient","old draft",selected,1)
    @Test fun unchangedSubmittedDraftCanBeCleared() {assertTrue(submitted.isCurrent("test-recipient","old draft",selected,1))}
    @Test fun editsDuringCompressionMustNotDisappear() {assertFalse(submitted.isCurrent("test-recipient","new draft",selected,1))}
    @Test fun replacementSelectionMustNotBeClearedEvenWithEqualContents() {assertFalse(submitted.isCurrent("test-recipient","old draft",selected.toMutableList(),1))}
    @Test fun differentRecipientOrSimKeepsNewDraft() {assertFalse(submitted.isCurrent("other-recipient","old draft",selected,1));assertFalse(submitted.isCurrent("test-recipient","old draft",selected,2))}
}

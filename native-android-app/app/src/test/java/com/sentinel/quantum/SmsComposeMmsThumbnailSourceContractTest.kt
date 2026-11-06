package com.sentinel.quantum

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsComposeMmsThumbnailSourceContractTest {
    private val source: String by lazy {
        File("src/main/java/com/sentinel/quantum/SmsComposeActivity.kt").readText()
    }

    @Test
    fun receivedThumbnailsLoadOffMainThreadAndCancelWithConversation() {
        assertTrue(source.contains("MmsImagePartRepository(applicationContext)"))
        assertTrue(source.contains("LaunchedEffect(selectedThreadId, threadMessages)"))
        assertTrue(source.contains("withContext(Dispatchers.IO)"))
        assertTrue(source.contains("ensureActive()"))
        assertTrue(source.contains("MMS_THUMBNAIL_MESSAGE_LIMIT"))
    }

    @Test
    fun thumbnailUiExposesLoadingAndUnavailableStates() {
        assertTrue(source.contains("MmsThumbnailState.Loading"))
        assertTrue(source.contains("MmsThumbnailState.Unavailable"))
        assertTrue(source.contains("Miniature MMS indisponible"))
    }
}

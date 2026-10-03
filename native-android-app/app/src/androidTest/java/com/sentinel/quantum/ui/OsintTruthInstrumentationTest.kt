package com.sentinel.quantum.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sentinel.quantum.data.OsintFeedItem
import com.sentinel.quantum.ui.screens.OsintFeedCard
import org.junit.Rule
import org.junit.Test
import java.util.Date

class OsintTruthInstrumentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unknownPublicationTimeIsRenderedAsUnknownInsteadOfEpoch() {
        val item = OsintFeedItem(
            title = "Alerte sans date",
            description = "",
            link = "https://example.org/advisory",
            source = "Source test",
            pubDate = Date(0L)
        )

        composeRule.setContent {
            MaterialTheme {
                OsintFeedCard(item = item)
            }
        }

        composeRule.onNodeWithText("Date inconnue").assertIsDisplayed()
        composeRule.onNodeWithText("Alerte sans date").assertIsDisplayed()
    }
}

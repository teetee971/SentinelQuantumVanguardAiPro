package com.sentinel.quantum.ui.design

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sentinel.quantum.security.ContactPhotoIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test

/** Exercises the real state producer used by D1ContactAvatar; only the provider read is deferred. */
class ContactPhotoStateInstrumentationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun revisionChangeHidesOldPixelsUntilTheNewReadCompletes() {
        var request by mutableStateOf(ContactPhotoIdentity(12, "content://com.android.contacts/photo/12", 100L))
        val updated = CompletableDeferred<String?>()
        rule.setContent {
            val current = request
            Text(rememberContactPhoto(current) {
                if (current.revision == 100L) "old pixels" else updated.await()
            } ?: "placeholder")
        }
        rule.onNodeWithText("old pixels").assertIsDisplayed()
        rule.runOnIdle { request = request.copy(revision = 101L) }
        rule.onNodeWithText("placeholder").assertIsDisplayed()
        updated.complete("updated pixels")
        rule.waitForIdle()
        rule.onNodeWithText("updated pixels").assertIsDisplayed()
    }

    @Test fun resumeGenerationReloadsWhenTheProviderHasNoRevision() {
        var request by mutableStateOf(ContactPhotoIdentity(12, "content://com.android.contacts/photo/12", null, 1))
        val updated = CompletableDeferred<String?>()
        rule.setContent {
            val current = request
            Text(rememberContactPhoto(current) {
                if (current.refreshGeneration == 1) "before editing" else updated.await()
            } ?: "placeholder")
        }
        rule.onNodeWithText("before editing").assertIsDisplayed()
        rule.runOnIdle { request = request.copy(refreshGeneration = 2) }
        rule.onNodeWithText("placeholder").assertIsDisplayed()
        updated.complete("after editing")
        rule.waitForIdle()
        rule.onNodeWithText("after editing").assertIsDisplayed()
    }

    @Test fun lateReadForPreviousContactCannotReplaceTheCurrentContact() {
        var request by mutableStateOf(ContactPhotoIdentity(12, "content://com.android.contacts/photo/12"))
        val previous = CompletableDeferred<String?>()
        val currentRead = CompletableDeferred<String?>()
        rule.setContent {
            val current = request
            Text(rememberContactPhoto(current) {
                if (current.contactId == 12L) withContext(NonCancellable) { previous.await() }
                else currentRead.await()
            } ?: "placeholder")
        }
        try {
            rule.onNodeWithText("placeholder").assertIsDisplayed()
            rule.runOnIdle { request = request.copy(contactId = 13, uri = "content://com.android.contacts/photo/13") }
            currentRead.complete("current contact pixels")
            rule.waitForIdle()
            rule.onNodeWithText("current contact pixels").assertIsDisplayed()
            previous.complete("late previous contact pixels")
            rule.waitForIdle()
            rule.onNodeWithText("current contact pixels").assertIsDisplayed()
        } finally {
            previous.complete(null)
            currentRead.complete(null)
        }
    }
}

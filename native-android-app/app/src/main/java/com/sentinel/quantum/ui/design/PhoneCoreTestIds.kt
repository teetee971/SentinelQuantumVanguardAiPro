package com.sentinel.quantum.ui.design

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/** Stable automation identity; accessibility labels remain separately translated. */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.phoneCoreTestId(id: String): Modifier =
    semantics { testTagsAsResourceId = true }.testTag(id)

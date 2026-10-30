package com.sentinel.quantum

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.quantum.security.MmsAttachmentDraft
import com.sentinel.quantum.security.MmsAttachmentLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Activity-scoped retention only: application context, no broad or persistent URI grant. */
internal class MmsAttachmentDraftViewModel(application: Application) : AndroidViewModel(application) {
    private val loader = MmsAttachmentLoader(application)
    val draft = MmsAttachmentDraft(viewModelScope) { sources ->
        withContext(Dispatchers.IO) { loader.load(sources.map(Uri::parse)) }
    }

    override fun onCleared() {
        draft.clear()
        super.onCleared()
    }
}

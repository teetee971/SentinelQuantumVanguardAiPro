package com.sentinel.quantum.security

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Local Android fields only. No inferred identity, remote account or trust claim. */
enum class ContactProfileKind {
    NAME, PHONE, EMAIL, ADDRESS, ORGANISATION, EVENT, NOTE, WEBSITE, NICKNAME, RELATION, IM, SIP
}
data class ContactProfileField(val kind: ContactProfileKind, val label: String, val value: String)
data class ContactProfileRequest(val contactId: Long, val revision: Long?, val generation: Int, val activation: Int = 0)
enum class ContactProfileStatus { READY, PERMISSION_REQUIRED, UNAVAILABLE }
data class ContactProfileSnapshot(
    val request: ContactProfileRequest,
    val status: ContactProfileStatus,
    val fields: List<ContactProfileField> = emptyList()
) {
    fun forRequest(current: ContactProfileRequest): ContactProfileSnapshot? = takeIf { request == current }
}

object ContactProfileDetails {
    /** No directory fallback: removed numbers must cease being actionable after a reload. */
    fun phoneNumbers(snapshot: ContactProfileSnapshot?): List<String> =
        if (snapshot?.status == ContactProfileStatus.READY)
            ContactPresentationPolicy.displayNumbers(snapshot.fields.filter { it.kind == ContactProfileKind.PHONE }.map { it.value })
        else emptyList()

    fun normalize(rows: List<ContactProfileField>): List<ContactProfileField> = rows.asSequence()
        .take(256)
        .map { it.copy(label = it.label.trim().take(80), value = it.value.trim().take(if (it.kind == ContactProfileKind.NOTE) 16384 else 4096)) }
        .filter { it.value.isNotBlank() }
        .distinctBy { Triple(it.kind, it.label, it.value) }
        .toList()
}

/** Recheck permission after provider I/O; cancellation must not publish an old contact's fields. */
suspend fun loadContactProfile(
    request: ContactProfileRequest,
    canRead: () -> Boolean,
    read: suspend () -> List<ContactProfileField>?
): ContactProfileSnapshot {
    if (!canRead()) return ContactProfileSnapshot(request, ContactProfileStatus.PERMISSION_REQUIRED)
    val rows = try { read() } catch (_: SecurityException) {
        currentCoroutineContext().ensureActive()
        return ContactProfileSnapshot(request, ContactProfileStatus.PERMISSION_REQUIRED)
    }
    currentCoroutineContext().ensureActive()
    if (!canRead()) return ContactProfileSnapshot(request, ContactProfileStatus.PERMISSION_REQUIRED)
    return if (rows == null) ContactProfileSnapshot(request, ContactProfileStatus.UNAVAILABLE)
    else ContactProfileSnapshot(request, ContactProfileStatus.READY, ContactProfileDetails.normalize(rows))
}

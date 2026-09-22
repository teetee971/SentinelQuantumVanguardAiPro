package com.sentinel.quantum.security

/**
 * Local-only chronology for already-classified network-security events.
 *
 * This component does not scan, capture packets, inspect payloads, open sockets or
 * resolve identities. It stores only device-bound fingerprints plus bounded metadata.
 */
enum class NetworkTimelineEventKind { FIRST_SEEN, RETURNED, DISAPPEARED, IDENTITY_CHANGE, TOPOLOGY_CHANGE, COVERT_DEVICE_HINT, FLOW_ANOMALY, BEHAVIOR_ANOMALY, RISK_SCORE_CHANGED }
enum class NetworkTimelineSource { ANDROID_WIFI, ANDROID_BLE, LOCAL_AGENT, FLOW_ANALYZER, BEHAVIOR_BASELINE, TOPOLOGY_ENGINE, RISK_DOSSIER, USER_CONFIRMED }
enum class NetworkTimelineSeverity { INFO, WARNING, HIGH }

data class NetworkTimelineEvent(val subjectFingerprint:String,val kind:NetworkTimelineEventKind,val source:NetworkTimelineSource,val severity:NetworkTimelineSeverity,val observedAtMs:Long,val summary:String)
data class NetworkTimelineSnapshot(val acceptedEvents:List<NetworkTimelineEvent>,val rejectedEvents:Int,val prunedEvents:Int,val firstObservedAtMs:Long?,val lastObservedAtMs:Long?,val highSeverityCount:Int,val warningCount:Int)

object NetworkChangeTimeline {
    const val MAX_EVENTS=10_000
    const val DEFAULT_RETENTION_MS=30L*24L*60L*60L*1000L
    const val DUPLICATE_WINDOW_MS=60_000L
    private const val FP_LEN=64
    private const val MAX_SUMMARY=160

    fun build(events:List<NetworkTimelineEvent>,nowMs:Long,retentionMs:Long=DEFAULT_RETENTION_MS):NetworkTimelineSnapshot {
        require(nowMs>=0L) { "nowMs must be non-negative" }; require(retentionMs>0L) { "retentionMs must be positive" }
        val bounded=events.takeLast(MAX_EVENTS); var rejected=events.size-bounded.size; var pruned=0
        val floor=(nowMs-retentionMs).coerceAtLeast(0L); val normalized=mutableListOf<NetworkTimelineEvent>()
        bounded.sortedBy{it.observedAtMs}.forEach { raw ->
            val event=normalize(raw,nowMs) ?: run { rejected++; return@forEach }
            if(event.observedAtMs<floor){ pruned++; return@forEach }
            val previous=normalized.lastOrNull{it.subjectFingerprint==event.subjectFingerprint&&it.kind==event.kind&&it.source==event.source&&it.severity==event.severity&&it.summary.equals(event.summary,ignoreCase=true)}
            if(previous!=null && event.observedAtMs-previous.observedAtMs<DUPLICATE_WINDOW_MS) rejected++ else normalized+=event
        }
        val accepted=normalized.sortedWith(compareByDescending<NetworkTimelineEvent>{it.observedAtMs}.thenBy{it.subjectFingerprint}.thenBy{it.kind.name})
        return NetworkTimelineSnapshot(accepted,rejected,pruned,accepted.minOfOrNull{it.observedAtMs},accepted.maxOfOrNull{it.observedAtMs},accepted.count{it.severity==NetworkTimelineSeverity.HIGH},accepted.count{it.severity==NetworkTimelineSeverity.WARNING})
    }
    private fun normalize(event:NetworkTimelineEvent,nowMs:Long):NetworkTimelineEvent? {
        val fp=event.subjectFingerprint.trim().lowercase(); if(fp.length!=FP_LEN || !fp.all{it in '0'..'9'||it in 'a'..'f'} || event.observedAtMs !in 0L..nowMs) return null
        val summary=event.summary.trim().replace(Regex("\\s+")," ").take(MAX_SUMMARY); if(summary.isEmpty()) return null
        return event.copy(subjectFingerprint=fp,summary=summary)
    }
}

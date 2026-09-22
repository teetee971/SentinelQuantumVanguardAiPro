package com.sentinel.quantum.security

/**
 * Local deterministic comparison of two already-produced network snapshots.
 *
 * The diff engine performs no discovery or network access. It accepts only device-bound
 * fingerprints and bounded metadata, allowing higher layers to explain what changed
 * between two points in time.
 */
enum class NetworkSnapshotProtocol { TCP, UDP, OTHER }
enum class NetworkSnapshotRelation { ATTACHED_TO_GATEWAY, BRIDGED_BY, MANAGED_BY, PEERS_WITH }
data class NetworkSnapshotService(val subjectFingerprint:String,val protocol:NetworkSnapshotProtocol,val port:Int)
data class NetworkSnapshotTopologyEdge(val fromFingerprint:String,val toFingerprint:String,val relation:NetworkSnapshotRelation)
data class NetworkSnapshotState(val observedAtMs:Long,val deviceFingerprints:Set<String>,val services:Set<NetworkSnapshotService>,val topologyEdges:Set<NetworkSnapshotTopologyEdge>,val riskScores:Map<String,Int>)
data class NetworkRiskDelta(val subjectFingerprint:String,val previousScore:Int,val currentScore:Int){ val delta:Int get()=currentScore-previousScore }
data class NetworkSnapshotDiff(val previousObservedAtMs:Long,val currentObservedAtMs:Long,val addedDevices:Set<String>,val removedDevices:Set<String>,val openedServices:Set<NetworkSnapshotService>,val closedServices:Set<NetworkSnapshotService>,val addedTopologyEdges:Set<NetworkSnapshotTopologyEdge>,val removedTopologyEdges:Set<NetworkSnapshotTopologyEdge>,val riskIncreases:List<NetworkRiskDelta>,val riskDecreases:List<NetworkRiskDelta>,val materialRiskIncreases:List<NetworkRiskDelta>,val rejectedItems:Int)

object NetworkSnapshotDiffEngine {
    const val MAX_DEVICES=4_096; const val MAX_SERVICES=20_000; const val MAX_TOPOLOGY_EDGES=10_000; const val DEFAULT_MATERIAL_RISK_DELTA=20; private const val FP_LEN=64
    fun compare(previous:NetworkSnapshotState,current:NetworkSnapshotState,materialRiskDelta:Int=DEFAULT_MATERIAL_RISK_DELTA):NetworkSnapshotDiff? {
        if(previous.observedAtMs<0L||current.observedAtMs<previous.observedAtMs||materialRiskDelta !in 1..100)return null
        val p=normalize(previous); val c=normalize(current)
        val deltas=(p.risks.keys+c.risks.keys).toSortedSet().mapNotNull { fp -> val before=p.risks[fp]; val after=c.risks[fp]; if(before==null||after==null||before==after)null else NetworkRiskDelta(fp,before,after) }
        val inc=deltas.filter{it.delta>0}.sortedWith(compareByDescending<NetworkRiskDelta>{it.delta}.thenBy{it.subjectFingerprint}); val dec=deltas.filter{it.delta<0}.sortedWith(compareBy<NetworkRiskDelta>{it.delta}.thenBy{it.subjectFingerprint})
        return NetworkSnapshotDiff(previous.observedAtMs,current.observedAtMs,c.devices-p.devices,p.devices-c.devices,c.services-p.services,p.services-c.services,c.edges-p.edges,p.edges-c.edges,inc,dec,inc.filter{it.delta>=materialRiskDelta},p.rejected+c.rejected)
    }
    private data class Normalized(val devices:Set<String>,val services:Set<NetworkSnapshotService>,val edges:Set<NetworkSnapshotTopologyEdge>,val risks:Map<String,Int>,val rejected:Int)
    private fun normalize(s:NetworkSnapshotState):Normalized {
        var rejected=0
        val devices=linkedSetOf<String>(); s.deviceFingerprints.sorted().take(MAX_DEVICES).forEach{raw-> val fp=fp(raw); if(fp==null)rejected++ else devices+=fp}; rejected+=(s.deviceFingerprints.size-MAX_DEVICES).coerceAtLeast(0)
        val services=linkedSetOf<NetworkSnapshotService>(); s.services.sortedWith(compareBy<NetworkSnapshotService>{it.subjectFingerprint}.thenBy{it.protocol.name}.thenBy{it.port}).take(MAX_SERVICES).forEach{raw->val fp=fp(raw.subjectFingerprint);if(fp==null||raw.port !in 1..65535)rejected++ else services+=raw.copy(subjectFingerprint=fp)}; rejected+=(s.services.size-MAX_SERVICES).coerceAtLeast(0)
        val edges=linkedSetOf<NetworkSnapshotTopologyEdge>(); s.topologyEdges.sortedWith(compareBy<NetworkSnapshotTopologyEdge>{it.fromFingerprint}.thenBy{it.toFingerprint}.thenBy{it.relation.name}).take(MAX_TOPOLOGY_EDGES).forEach{raw->val a=fp(raw.fromFingerprint);val b=fp(raw.toFingerprint);if(a==null||b==null||a==b){rejected++;return@forEach};var from=a;var to=b;if(raw.relation==NetworkSnapshotRelation.PEERS_WITH&&from>to){val x=from;from=to;to=x};edges+=raw.copy(fromFingerprint=from,toFingerprint=to)}; rejected+=(s.topologyEdges.size-MAX_TOPOLOGY_EDGES).coerceAtLeast(0)
        val risks=linkedMapOf<String,Int>();s.riskScores.toSortedMap().forEach{(raw,score)->val fp=fp(raw);if(fp==null||score !in 0..100)rejected++ else risks[fp]=score}
        return Normalized(devices,services,edges,risks,rejected)
    }
    private fun fp(v:String):String?{val n=v.trim().lowercase();return n.takeIf{it.length==FP_LEN&&it.all{c->c in '0'..'9'||c in 'a'..'f'}}}
}

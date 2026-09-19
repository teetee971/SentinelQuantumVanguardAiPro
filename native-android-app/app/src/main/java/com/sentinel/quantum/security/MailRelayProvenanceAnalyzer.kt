package com.sentinel.quantum.security

import java.net.InetAddress

/** Local-only SMTP relay provenance. Observable relay IPs identify infrastructure, not a person. */
object MailRelayProvenanceAnalyzer {
    enum class Confidence { INDICATIVE, UNKNOWN }
    data class Hop(val index:Int,val fromHost:String?,val byHost:String?,val observablePublicIps:List<String>,val confidence:Confidence)
    data class Report(val hops:List<Hop>,val truncated:Boolean)
    private val ipv4=Regex("""(?<![0-9.])(?:\d{1,3}\.){3}\d{1,3}(?![0-9.])""")
    private val ipv6=Regex("""(?i)(?<![0-9a-f:])(?:[0-9a-f]{0,4}:){2,7}[0-9a-f]{0,4}(?![0-9a-f:])""")
    private val from=Regex("""(?i)(?:^|\s)from\s+([^\s(;]+)""")
    private val by=Regex("""(?i)(?:^|\s)by\s+([^\s(;]+)""")

    fun analyze(received:List<String>):Report {
        val bounded=received.take(EmailHeaderAnalyzer.MAX_RECEIVED_HOPS)
        return Report(bounded.mapIndexed { i,raw ->
            val ips=(ipv4.findAll(raw).map{it.value}+ipv6.findAll(raw).map{it.value})
                .mapNotNull(::publicIp).distinct().take(4).toList()
            Hop(i,from.find(raw)?.groupValues?.get(1),by.find(raw)?.groupValues?.get(1),ips,
                if(ips.isEmpty()) Confidence.UNKNOWN else Confidence.INDICATIVE)
        },received.size>EmailHeaderAnalyzer.MAX_RECEIVED_HOPS)
    }

    private fun publicIp(value:String):String?=try {
        val a=InetAddress.getByName(value)
        if(a.isAnyLocalAddress||a.isLoopbackAddress||a.isLinkLocalAddress||a.isSiteLocalAddress||a.isMulticastAddress||reserved(a.address)) null else a.hostAddress
    } catch(_:Exception){null}

    private fun reserved(b:ByteArray):Boolean {
        if(b.size==4){val a=b[0].toInt()and 255;val x=b[1].toInt()and 255;val c=b[2].toInt()and 255
            return a==0||a>=224||(a==100&&x in 64..127)||(a==192&&x==0&&c==2)||(a==198&&x==51&&c==100)||(a==203&&x==0&&c==113)}
        if(b.size==16){val a=b[0].toInt()and 255;val x=b[1].toInt()and 255
            return a==0xfc||a==0xfd||(a==0xfe&&(x and 0xc0)==0x80)||(a==0x20&&x==0x01&&(b[2].toInt()and 255)==0x0d&&(b[3].toInt()and 255)==0xb8)}
        return true
    }
}

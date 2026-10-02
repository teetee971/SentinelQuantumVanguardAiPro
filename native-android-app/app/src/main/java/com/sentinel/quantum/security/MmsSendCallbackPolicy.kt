package com.sentinel.quantum.security

/** Pure validation boundary for identity-bound outgoing MMS telephony callbacks. */
object MmsSendCallbackPolicy {
    data class Input(
        val action: String?,
        val scheme: String?,
        val host: String?,
        val pathSegments: List<String>,
        val uriToken: String?,
        val extraToken: String?,
        val fileName: String?,
        val subscriptionId: Int
    )

    fun accepts(input: Input): Boolean {
        if (input.action != MmsSendCoordinator.ACTION_SEND_COMPLETE) return false
        if (input.scheme != "sentinel-mms-send" || input.host != "callback") return false
        if (input.pathSegments.size != 1) return false
        val token = input.extraToken ?: return false
        if (input.uriToken != token || input.pathSegments.single() != token) return false
        if (!UUID_TOKEN.matches(token)) return false
        if (input.fileName != token + ".pdu") return false
        return input.subscriptionId >= 0
    }

    private val UUID_TOKEN = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
    )
}

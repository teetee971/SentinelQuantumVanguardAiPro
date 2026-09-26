package com.sentinel.quantum.security

/**
 * Local-only OTP recognition for SMS safety UX.
 *
 * OTP values are intentionally never exposed as a network payload. Detection is bounded and
 * conservative: a short numeric code must appear next to explicit one-time-code language.
 */
object SmsOtpPrivacy {
    private const val MAX_TEXT_CHARS = 2_000
    private val code = Regex("""(?<!\d)(\d{4,8})(?!\d)""")
    private val context = Regex(
        """(?i)\b(code|otp|one[- ]?time|usage unique|vérification|verification|authentification|confirmation)\b"""
    )

    data class Result(val containsOtp: Boolean, val codeLength: Int?)

    fun inspect(message: String): Result {
        val text = message.take(MAX_TEXT_CHARS)
        if (!context.containsMatchIn(text)) return Result(false, null)
        val numeric = numericCode.find(text)
        val alphanumeric = alphanumericCode.find(text)
        val length = listOfNotNull(
            numeric?.groupValues?.getOrNull(1)?.length,
            alphanumeric?.value?.length
        ).minOrNull() ?: return Result(false, null)
        return Result(true, length)
    }

    /** OTP-bearing message bodies and extracted codes must remain local. */
    fun permitsRemoteTransmission(result: Result): Boolean = !result.containsOtp
}

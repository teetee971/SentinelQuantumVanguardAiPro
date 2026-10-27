package com.sentinel.quantum

/** Pure outcome policy for the user-driven Android role request flow. */
internal object PhoneCoreRoleRequestPolicy {
    enum class AndroidDecision {
        ACCEPTED_ROLE_HELD,
        ACCEPTED_ROLE_NOT_HELD,
        REFUSED_OR_CANCELLED,
        ANDROID_RESULT_NOT_OK,
    }

    enum class Status {
        ROLE_UNAVAILABLE,
        INTENT_UNAVAILABLE,
        INTENT_READY,
        LAUNCH_FAILED,
        AWAITING_ANDROID,
        CANCELLED,
        COMPLETED_NOT_HELD,
        COMPLETED_HELD,
    }

    fun afterClick(roleAvailable: Boolean, intentCreated: Boolean): Status = when {
        !roleAvailable -> Status.ROLE_UNAVAILABLE
        !intentCreated -> Status.INTENT_UNAVAILABLE
        else -> Status.INTENT_READY
    }

    fun afterLaunch(success: Boolean): Status =
        if (success) Status.AWAITING_ANDROID else Status.LAUNCH_FAILED

    fun androidDecision(resultCode: Int, roleHeld: Boolean): AndroidDecision = when {
        roleHeld -> AndroidDecision.ACCEPTED_ROLE_HELD
        resultCode == -1 -> AndroidDecision.ACCEPTED_ROLE_NOT_HELD
        resultCode == 0 -> AndroidDecision.REFUSED_OR_CANCELLED
        else -> AndroidDecision.ANDROID_RESULT_NOT_OK
    }

    fun afterResult(resultCode: Int, roleHeld: Boolean): Status = when {
        roleHeld -> Status.COMPLETED_HELD
        resultCode == 0 -> Status.CANCELLED
        else -> Status.COMPLETED_NOT_HELD
    }

    fun message(status: Status): String = when (status) {
        Status.ROLE_UNAVAILABLE -> "Le rôle Téléphone est indisponible sur cet appareil."
        Status.INTENT_UNAVAILABLE -> "Android n’a pas fourni de demande pour le rôle Téléphone. Vérifiez la configuration des rôles système."
        Status.INTENT_READY, Status.AWAITING_ANDROID -> "La demande Android pour le rôle Téléphone a été lancée."
        Status.LAUNCH_FAILED -> "Android n’a pas pu ouvrir la demande du rôle Téléphone."
        Status.CANCELLED -> "La demande du rôle Téléphone a été annulée. Sentinel reste non attribué."
        Status.COMPLETED_NOT_HELD -> "La demande est terminée, mais Sentinel ne détient pas le rôle Téléphone."
        Status.COMPLETED_HELD -> "Rôle Téléphone accordé à Sentinel."
    }
}

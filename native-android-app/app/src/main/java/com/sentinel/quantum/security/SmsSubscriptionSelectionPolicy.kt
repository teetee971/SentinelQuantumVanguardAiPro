package com.sentinel.quantum.security

/**
 * Fail-closed selection policy for multi-SIM SMS sending.
 * UI may present active subscription IDs, but a send must resolve to exactly one active line.
 *
 * The Android default subscription is ignored by default so interactive compose surfaces still
 * require an explicit user choice when several lines are active. Non-interactive platform flows
 * such as ACTION_RESPOND_VIA_MESSAGE may opt in to the already-active Android default.
 */
object SmsSubscriptionSelectionPolicy {
    data class Result(val accepted:Boolean,val subscriptionId:Int?,val reason:String)
    fun select(
        activeSubscriptionIds:Set<Int>,
        requestedSubscriptionId:Int?,
        defaultSubscriptionId:Int?,
        allowActiveDefaultWhenMultiple:Boolean = false
    ):Result {
        val active=activeSubscriptionIds.filter { it >= 0 }.toSet()
        if(active.isEmpty()) return Result(false,null,"NO_ACTIVE_SMS_SUBSCRIPTION")
        if(requestedSubscriptionId!=null) return if(requestedSubscriptionId in active)
            Result(true,requestedSubscriptionId,"USER_SELECTED") else Result(false,null,"REQUESTED_SUBSCRIPTION_NOT_ACTIVE")
        if(active.size==1) return Result(true,active.single(),"ONLY_ACTIVE_SUBSCRIPTION")
        if(allowActiveDefaultWhenMultiple && defaultSubscriptionId != null && defaultSubscriptionId in active) {
            return Result(true,defaultSubscriptionId,"ANDROID_DEFAULT_SUBSCRIPTION")
        }
        return Result(false,null,"USER_SELECTION_REQUIRED")
    }
}

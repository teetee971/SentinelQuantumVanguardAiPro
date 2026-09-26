package com.sentinel.quantum.security

/**
 * Fail-closed selection policy for multi-SIM SMS sending.
 * UI may present active subscription IDs, but a send must resolve to exactly one active line.
 *
 * Android's default SMS subscription is intentionally not used when several lines are active:
 * Sentinel requires the same explicit user choice that the compose UI displays.
 */
object SmsSubscriptionSelectionPolicy {
    data class Result(val accepted:Boolean,val subscriptionId:Int?,val reason:String)
    fun select(activeSubscriptionIds:Set<Int>, requestedSubscriptionId:Int?, defaultSubscriptionId:Int?):Result {
        val active=activeSubscriptionIds.filter { it >= 0 }.toSet()
        if(active.isEmpty()) return Result(false,null,"NO_ACTIVE_SMS_SUBSCRIPTION")
        if(requestedSubscriptionId!=null) return if(requestedSubscriptionId in active)
            Result(true,requestedSubscriptionId,"USER_SELECTED") else Result(false,null,"REQUESTED_SUBSCRIPTION_NOT_ACTIVE")
        if(active.size==1) return Result(true,active.single(),"ONLY_ACTIVE_SUBSCRIPTION")
        return Result(false,null,"USER_SELECTION_REQUIRED")
    }
}

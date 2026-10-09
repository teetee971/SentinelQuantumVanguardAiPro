package com.sentinel.quantum.talkiewalkie

enum class PttTriggerEvent {
    PRESS,
    RELEASE,
    DISCONNECTED
}

interface PttTriggerTarget {
    suspend fun onPress()

    suspend fun onRelease()

    suspend fun onTriggerLost()
}

class PttTrigger(
    private val target: PttTriggerTarget
) {
    suspend fun dispatch(event: PttTriggerEvent) {
        when (event) {
            PttTriggerEvent.PRESS -> target.onPress()
            PttTriggerEvent.RELEASE -> target.onRelease()
            PttTriggerEvent.DISCONNECTED -> target.onTriggerLost()
        }
    }
}

class TalkieWalkieSessionTriggerTarget(
    private val controller: TalkieWalkieSessionController
) : PttTriggerTarget {
    override suspend fun onPress() {
        controller.pressToTalk()
    }

    override suspend fun onRelease() {
        controller.releaseToTalk()
    }

    override suspend fun onTriggerLost() {
        controller.onTriggerLost()
    }
}

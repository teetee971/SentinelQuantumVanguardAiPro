package com.sentinel.quantum.talkiewalkie

enum class TalkieWalkieState {
    DISCONNECTED,
    CONNECTING,
    LISTENING,
    REQUESTING_FLOOR,
    TRANSMITTING,
    RECEIVING,
    RECONNECTING,
    FAILED
}

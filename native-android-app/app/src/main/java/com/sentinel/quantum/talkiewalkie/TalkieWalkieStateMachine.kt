package com.sentinel.quantum.talkiewalkie

enum class TalkieWalkieState {
    DISCONNECTED,
    CONNECTING,
    LISTENING,
    REQUESTING_FLOOR,
    TRANSMITTING,
    RECONNECTING,
    FAILED
}

sealed interface TalkieWalkieEvent {
    data object ConnectRequested : TalkieWalkieEvent
    data object Connected : TalkieWalkieEvent
    data object PressToTalk : TalkieWalkieEvent
    data object FloorGranted : TalkieWalkieEvent
    data object ReleaseToTalk : TalkieWalkieEvent
    data object TransportLost : TalkieWalkieEvent
    data object Reconnected : TalkieWalkieEvent
    data object FatalFailure : TalkieWalkieEvent
}

class TalkieWalkieStateMachine {
    fun transition(
        current: TalkieWalkieState,
        event: TalkieWalkieEvent
    ): TalkieWalkieState = when (current to event) {
        TalkieWalkieState.DISCONNECTED to TalkieWalkieEvent.ConnectRequested ->
            TalkieWalkieState.CONNECTING

        TalkieWalkieState.CONNECTING to TalkieWalkieEvent.Connected ->
            TalkieWalkieState.LISTENING

        TalkieWalkieState.LISTENING to TalkieWalkieEvent.PressToTalk ->
            TalkieWalkieState.REQUESTING_FLOOR

        TalkieWalkieState.REQUESTING_FLOOR to TalkieWalkieEvent.FloorGranted ->
            TalkieWalkieState.TRANSMITTING

        TalkieWalkieState.TRANSMITTING to TalkieWalkieEvent.ReleaseToTalk ->
            TalkieWalkieState.LISTENING

        TalkieWalkieState.TRANSMITTING to TalkieWalkieEvent.TransportLost ->
            TalkieWalkieState.RECONNECTING

        TalkieWalkieState.RECONNECTING to TalkieWalkieEvent.Reconnected ->
            TalkieWalkieState.LISTENING

        TalkieWalkieState.TRANSMITTING to TalkieWalkieEvent.FatalFailure ->
            TalkieWalkieState.FAILED

        else -> throw IllegalStateException(
            "Illegal talkie-walkie transition: $current + ${event::class.simpleName}"
        )
    }
}

package com.sentinel.quantum.talkiewalkie

data class TalkieWalkieUiState(
    val pttState: TalkieWalkieState,
    val networkQuality: NetworkQuality,
    val channelName: String,
    val participantCount: Int,
    val activeSpeakerName: String?,
    val audioRouteLabel: String
)

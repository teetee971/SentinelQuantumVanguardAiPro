package com.sentinel.quantum.talkiewalkie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TalkieWalkieViewModel(
    private val controller: TalkieWalkieSessionController,
    initialUiState: TalkieWalkieUiState
) : ViewModel(), TalkieWalkieScreenActions {
    private val mutableUiState = MutableStateFlow(initialUiState)
    val uiState: StateFlow<TalkieWalkieUiState> = mutableUiState.asStateFlow()

    override fun onPressToTalk() {
        viewModelScope.launch {
            controller.pressToTalk()
            refreshAuthoritativeState()
        }
    }

    override fun onReleaseToTalk() {
        viewModelScope.launch {
            controller.releaseToTalk()
            refreshAuthoritativeState()
        }
    }

    override fun onAudioRouteClick() = Unit

    fun updateNetworkQuality(networkQuality: NetworkQuality) {
        mutableUiState.value = mutableUiState.value.copy(networkQuality = networkQuality)
    }

    fun updateParticipants(
        participantCount: Int,
        activeSpeakerName: String?
    ) {
        mutableUiState.value = mutableUiState.value.copy(
            participantCount = participantCount.coerceAtLeast(0),
            activeSpeakerName = activeSpeakerName
        )
    }

    fun updateAudioRouteLabel(label: String) {
        mutableUiState.value = mutableUiState.value.copy(audioRouteLabel = label)
    }

    private fun refreshAuthoritativeState() {
        mutableUiState.value = mutableUiState.value.copy(
            pttState = controller.snapshot().state
        )
    }
}

package com.sentinel.quantum.talkiewalkie

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.quantum.ui.design.SentinelD1

interface TalkieWalkieScreenActions {
    fun onPressToTalk()
    fun onReleaseToTalk()
    fun onAudioRouteClick()
}

@Composable
fun TalkieWalkieScreen(
    uiState: TalkieWalkieUiState,
    actions: TalkieWalkieScreenActions,
    modifier: Modifier = Modifier
) {
    val status = pttStatus(uiState.pttState)
    val statusColor = pttStatusColor(uiState.pttState)
    val pttEnabled = uiState.pttState == TalkieWalkieState.LISTENING ||
        uiState.pttState == TalkieWalkieState.TRANSMITTING

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SentinelD1.Background)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = SentinelD1.Panel,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = uiState.channelName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.participantCount.coerceAtLeast(0)} participant(s)",
                            color = SentinelD1.Unknown,
                            fontSize = 13.sp
                        )
                    }
                    NetworkQualityBadge(uiState.networkQuality)
                }

                Text(
                    text = uiState.activeSpeakerName?.let { "En réception : $it" } ?: "Canal prêt",
                    color = if (uiState.activeSpeakerName != null) SentinelD1.Cyan else SentinelD1.Unknown,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(30.dp))

        Text(
            text = status,
            color = statusColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(22.dp))

        Box(
            modifier = Modifier
                .size(232.dp)
                .border(1.dp, statusColor.copy(alpha = 0.32f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(202.dp)
                    .border(2.dp, statusColor.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .size(170.dp)
                        .semantics {
                            contentDescription = "Bouton pousser-pour-parler"
                        }
                        .pointerInput(actions, pttEnabled) {
                            if (pttEnabled) {
                                detectTapGestures(
                                    onPress = {
                                        actions.onPressToTalk()
                                        tryAwaitRelease()
                                        actions.onReleaseToTalk()
                                    }
                                )
                            }
                        },
                    shape = CircleShape,
                    color = statusColor.copy(alpha = if (pttEnabled) 0.18f else 0.08f),
                    contentColor = Color.White
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = when (uiState.pttState) {
                                TalkieWalkieState.TRANSMITTING -> "PTT\nACTIF"
                                TalkieWalkieState.LISTENING -> "PTT\nMAINTENIR"
                                else -> "PTT\nATTENTE"
                            },
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Route audio : ${uiState.audioRouteLabel}" },
            shape = RoundedCornerShape(16.dp),
            color = SentinelD1.Card,
            contentColor = Color.White,
            onClick = actions.onAudioRouteClick
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Route audio", color = SentinelD1.Unknown)
                Text(uiState.audioRouteLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NetworkQualityBadge(quality: NetworkQuality) {
    val (label, color) = when (quality) {
        NetworkQuality.EXCELLENT -> "Réseau excellent" to SentinelD1.Success
        NetworkQuality.GOOD -> "Réseau bon" to SentinelD1.Cyan
        NetworkQuality.DEGRADED -> "Réseau dégradé" to SentinelD1.Warning
        NetworkQuality.UNUSABLE -> "Réseau inutilisable" to SentinelD1.Danger
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        contentColor = color
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun pttStatus(state: TalkieWalkieState): String = when (state) {
    TalkieWalkieState.DISCONNECTED -> "Déconnecté"
    TalkieWalkieState.CONNECTING -> "Connexion…"
    TalkieWalkieState.LISTENING -> "Maintenir pour parler"
    TalkieWalkieState.REQUESTING_FLOOR -> "Demande du canal…"
    TalkieWalkieState.TRANSMITTING -> "Vous parlez"
    TalkieWalkieState.RECEIVING -> "Réception en cours"
    TalkieWalkieState.RECONNECTING -> "Reconnexion…"
    TalkieWalkieState.FAILED -> "Session indisponible"
}

private fun pttStatusColor(state: TalkieWalkieState): Color = when (state) {
    TalkieWalkieState.TRANSMITTING -> SentinelD1.Success
    TalkieWalkieState.RECEIVING -> SentinelD1.Cyan
    TalkieWalkieState.REQUESTING_FLOOR,
    TalkieWalkieState.CONNECTING,
    TalkieWalkieState.RECONNECTING -> SentinelD1.Warning
    TalkieWalkieState.FAILED -> SentinelD1.Danger
    TalkieWalkieState.DISCONNECTED -> SentinelD1.Unknown
    TalkieWalkieState.LISTENING -> SentinelD1.Cyan
}

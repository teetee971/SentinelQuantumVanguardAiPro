package com.sentinel.quantum

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sentinel.quantum.security.SentinelInCallService
import com.sentinel.quantum.ui.design.PhoneCoreBrand
import com.sentinel.quantum.ui.design.SentinelTopBar
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import com.sentinel.quantum.voice.VoiceAddonPolicy
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
class VoiceStudioActivity : ComponentActivity() {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private lateinit var previewFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewFile = File(cacheDir, "voice-studio-preview.m4a")
        setContent {
            SentinelQuantumTheme {
                VoiceStudioScreen()
            }
        }
    }

    @Composable
    private fun VoiceStudioScreen() {
        var selectedEffect by remember { mutableStateOf(VoiceAddonPolicy.Effect.NATURAL) }
        var recording by remember { mutableStateOf(false) }
        var playing by remember { mutableStateOf(false) }
        var previewReady by remember { mutableStateOf(previewFile.exists()) }
        var status by remember {
            mutableStateOf("Testez votre voix localement avant son utilisation dans un appel Sentinel compatible.")
        }
        val commercialState = remember { VoiceAddonPolicy.currentState() }

        val microphoneLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startRecording()?.let { failure -> status = failure } ?: run {
                    recording = true
                    previewReady = false
                    status = "Enregistrement local en cours…"
                }
            } else {
                status = "Microphone refusé. Aucun enregistrement n’a été effectué."
            }
        }

        Scaffold(
            topBar = {
                SentinelTopBar(
                    title = "Studio voix",
                    subtitle = "Aperçu local · moteur temps réel intégré",
                    onBack = { finish() }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PhoneCoreBrand(
                    context = "ADD-ON · STUDIO VOIX",
                    status = commercialState.customerLabel,
                    modifier = Modifier.fillMaxWidth()
                )

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Choisissez un rendu", fontWeight = FontWeight.Bold)
                                Text(
                                    "Le fichier d’essai reste dans le cache de Sentinel et est supprimé à la fermeture.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        VoiceAddonPolicy.Effect.entries.forEach { effect ->
                            FilterChip(
                                selected = selectedEffect == effect,
                                onClick = {
                                    if (playing) {
                                        stopPlayback()
                                        playing = false
                                    }
                                    selectedEffect = effect
                                    status = if (previewReady) {
                                        "Rendu « ${effect.label} » sélectionné. Écoutez l’aperçu."
                                    } else {
                                        "Rendu « ${effect.label} » sélectionné. Enregistrez un essai."
                                    }
                                },
                                label = { Text(effect.label) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = {
                                if (recording) {
                                    stopRecording()
                                    recording = false
                                    previewReady = previewFile.exists() && previewFile.length() > 0L
                                    status = if (previewReady) "Aperçu prêt. Choisissez un rendu puis écoutez-le."
                                    else "Aucun aperçu exploitable n’a été créé."
                                } else {
                                    if (playing) {
                                        stopPlayback()
                                        playing = false
                                    }
                                    when {
                                        carrierCallActive() ->
                                            status = "Aperçu bloqué pendant un appel mobile actif."
                                        ContextCompat.checkSelfPermission(
                                            this@VoiceStudioActivity,
                                            Manifest.permission.RECORD_AUDIO
                                        ) != PackageManager.PERMISSION_GRANTED ->
                                            microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        else -> {
                                            startRecording()?.let { failure -> status = failure } ?: run {
                                                recording = true
                                                previewReady = false
                                                status = "Enregistrement local en cours…"
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (recording) "Arrêter l’essai" else "Enregistrer un essai")
                        }

                        OutlinedButton(
                            onClick = {
                                if (playing) {
                                    stopPlayback()
                                    playing = false
                                    status = "Lecture arrêtée."
                                } else if (carrierCallActive()) {
                                    status = "Lecture bloquée pendant un appel mobile actif."
                                } else {
                                    playPreview(
                                        selectedEffect,
                                        onCompleted = {
                                            playing = false
                                            status = "Lecture « ${selectedEffect.label} » terminée."
                                        }
                                    )?.let { failure ->
                                        playing = false
                                        status = failure
                                    } ?: run {
                                        playing = true
                                        status = "Lecture « ${selectedEffect.label} » en cours."
                                    }
                                }
                            },
                            enabled = previewReady && !recording,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (playing) "Arrêter la lecture" else "Écouter l’aperçu")
                        }

                        Text(status, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Transformation en appel · intégration obligatoire", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Le moteur temps réel, le post-traitement LiveKit/WebRTC et le transport média côté client sont intégrés. Aucun parcours utilisateur ne lance encore une session d’appel Sentinel réelle : ce chemin reste volontairement non opérationnel.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Il reste à raccorder une session d’appel authentifiée : émission serveur de jetons éphémères, serveur LiveKit/signaling, contrôle d’accès et passerelle VoIP/PSTN pour joindre les numéros classiques. L’audio d’un appel SIM natif reste hors du chemin média public d’une application Android tierce.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {},
                            enabled = commercialState.paidCheckoutAllowed,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Session d’appel/PSTN non raccordée")
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun carrierCallActive(): Boolean {
        if (SentinelInCallService.hasActiveCall()) return true

        val audioMode = getSystemService(AudioManager::class.java)?.mode
        if (audioMode == AudioManager.MODE_IN_CALL ||
            audioMode == AudioManager.MODE_IN_COMMUNICATION
        ) {
            return true
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return runCatching {
            getSystemService(TelecomManager::class.java)?.isInCall == true
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    private fun startRecording(): String? {
        stopPlayback()
        runCatching { previewFile.delete() }
        val nextRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }
        return runCatching {
            nextRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            nextRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            nextRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            nextRecorder.setAudioEncodingBitRate(96_000)
            nextRecorder.setAudioSamplingRate(44_100)
            nextRecorder.setOutputFile(previewFile.absolutePath)
            nextRecorder.prepare()
            nextRecorder.start()
            recorder = nextRecorder
            null
        }.getOrElse {
            runCatching { nextRecorder.release() }
            "Impossible de démarrer l’essai vocal sur cet appareil."
        }
    }

    private fun stopRecording() {
        val current = recorder ?: return
        recorder = null
        runCatching { current.stop() }
        runCatching { current.release() }
    }

    private fun playPreview(
        effect: VoiceAddonPolicy.Effect,
        onCompleted: () -> Unit
    ): String? {
        stopPlayback()
        if (!previewFile.exists() || previewFile.length() == 0L) return "Enregistrez d’abord un essai."
        return runCatching {
            MediaPlayer().also { next ->
                player = next
                next.setDataSource(previewFile.absolutePath)
                next.setOnCompletionListener {
                    stopPlayback()
                    onCompleted()
                }
                next.prepare()
                next.playbackParams = PlaybackParams()
                    .setSpeed(1.0f)
                    .setPitch(effect.pitch)
                next.start()
            }
            null
        }.getOrElse {
            stopPlayback()
            "Ce rendu vocal n’est pas pris en charge par cet appareil."
        }
    }

    private fun stopPlayback() {
        val current = player ?: return
        player = null
        runCatching { current.stop() }
        runCatching { current.release() }
    }

    override fun onDestroy() {
        stopRecording()
        stopPlayback()
        runCatching { previewFile.delete() }
        super.onDestroy()
    }
}

package com.sentinel.quantum

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
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
import com.sentinel.quantum.security.VoiceModulatorPolicy
import com.sentinel.quantum.ui.design.PhoneCoreBrand
import com.sentinel.quantum.ui.design.SentinelTopBar
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Local Voice Studio preview.
 *
 * The preview records a short microphone sample into RAM, stops capture, then plays the
 * sample locally with Android playback pitch processing. Nothing is written to disk or sent
 * over the network. It deliberately refuses to run while Android reports an active call.
 *
 * This is not a carrier-call uplink modifier. Live modulation is reserved for a future
 * Sentinel-managed VoIP transport and remains locked until that transport and its DSP path
 * are physically validated.
 */
class VoiceStudioActivity : ComponentActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private val stopRequested = AtomicBoolean(false)
    @Volatile private var activeRecorder: AudioRecord? = null
    @Volatile private var activeTrack: AudioTrack? = null

    private var microphoneGranted by mutableStateOf(false)
    private var captureState by mutableStateOf(CaptureState.IDLE)
    private var statusText by mutableStateOf("Enregistrez 3 secondes pour tester votre voix localement.")
    private var sample by mutableStateOf<ShortArray?>(null)
    private var selectedPreset by mutableStateOf(VoicePreset.NATURAL)

    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        microphoneGranted = granted
        statusText = if (granted) {
            "Microphone autorisé. L’aperçu reste local et n’est pas utilisé pendant un appel opérateur."
        } else {
            "Microphone refusé. Aucun enregistrement n’a été effectué."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        microphoneGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            SentinelQuantumTheme {
                VoiceStudioScreen(
                    microphoneGranted = microphoneGranted,
                    captureState = captureState,
                    statusText = statusText,
                    sampleReady = sample != null,
                    selectedPreset = selectedPreset,
                    onPresetSelected = { selectedPreset = it },
                    onRequestMicrophone = {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onCapture = ::capturePreview,
                    onPlay = ::playPreview,
                    onStop = ::stopAudio,
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onStop() {
        stopAudio()
        super.onStop()
    }

    override fun onDestroy() {
        stopAudio()
        io.shutdownNow()
        super.onDestroy()
    }

    private fun capturePreview() {
        if (!microphoneGranted) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (isPhoneCallActive()) {
            statusText = "Aperçu local désactivé pendant un appel téléphonique actif."
            return
        }
        if (captureState != CaptureState.IDLE) return

        stopRequested.set(false)
        captureState = CaptureState.RECORDING
        sample = null
        statusText = "Enregistrement local… 3 secondes."

        io.execute {
            val result = runCatching { recordThreeSeconds() }
            runOnUiThread {
                captureState = CaptureState.IDLE
                if (stopRequested.get()) {
                    sample = null
                    statusText = "Aperçu arrêté et effacé de la mémoire."
                } else {
                    result.onSuccess { captured ->
                        sample = captured
                        statusText = if (captured.isNotEmpty()) {
                            "Aperçu prêt. Choisissez un effet puis écoutez-le."
                        } else {
                            "Aucun son exploitable n’a été capturé."
                        }
                    }.onFailure {
                        sample = null
                        statusText = "Impossible d’enregistrer l’aperçu sur cet appareil."
                    }
                }
            }
        }
    }

    private fun playPreview() {
        val captured = sample ?: run {
            statusText = "Enregistrez d’abord un aperçu."
            return
        }
        if (isPhoneCallActive()) {
            statusText = "Lecture désactivée pendant un appel téléphonique actif."
            return
        }
        if (captureState != CaptureState.IDLE) return

        stopRequested.set(false)
        captureState = CaptureState.PLAYING
        statusText = "Lecture locale · " + selectedPreset.label

        io.execute {
            val result = runCatching { playSample(captured, selectedPreset) }
            runOnUiThread {
                captureState = CaptureState.IDLE
                if (stopRequested.get()) {
                    sample = null
                    statusText = "Aperçu arrêté et effacé de la mémoire."
                } else if (result.isSuccess) {
                    statusText = "Lecture terminée. Aucun audio n’a été écrit sur le stockage."
                } else {
                    statusText = "Cet effet audio n’est pas disponible sur cet appareil."
                }
            }
        }
    }

    private fun stopAudio() {
        stopRequested.set(true)
        runCatching { activeRecorder?.stop() }
        runCatching { activeTrack?.stop() }
        sample = null
        if (captureState != CaptureState.IDLE) {
            captureState = CaptureState.IDLE
            statusText = "Aperçu arrêté et effacé de la mémoire."
        }
    }

    private fun recordThreeSeconds(): ShortArray {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBuffer > 0) { "AUDIO_RECORD_UNAVAILABLE" }
        val bufferBytes = max(minBuffer, SAMPLE_RATE / 2)
        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .build()
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "AUDIO_RECORD_INIT_FAILED" }

        activeRecorder = recorder
        val target = ShortArray(SAMPLE_RATE * PREVIEW_SECONDS)
        var offset = 0
        try {
            recorder.startRecording()
            while (offset < target.size && !stopRequested.get()) {
                val read = recorder.read(
                    target,
                    offset,
                    minOf(target.size - offset, bufferBytes / 2),
                    AudioRecord.READ_BLOCKING
                )
                if (read <= 0) break
                offset += read
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            activeRecorder = null
        }
        return if (offset == target.size) target else target.copyOf(offset.coerceAtLeast(0))
    }

    private fun playSample(samples: ShortArray, preset: VoicePreset) {
        if (samples.isEmpty()) return
        val bytes = samples.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        check(track.state == AudioTrack.STATE_INITIALIZED) { "AUDIO_TRACK_INIT_FAILED" }

        activeTrack = track
        try {
            check(track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING) > 0) {
                "AUDIO_TRACK_WRITE_FAILED"
            }
            track.playbackParams = PlaybackParams()
                .allowDefaults()
                .setSpeed(1.0f)
                .setPitch(preset.pitch)
            track.play()
            val waitMs = PREVIEW_SECONDS * 1000L + 700L
            var elapsed = 0L
            while (elapsed < waitMs && !stopRequested.get()) {
                Thread.sleep(50)
                elapsed += 50
            }
        } finally {
            runCatching { track.stop() }
            runCatching { track.flush() }
            track.release()
            activeTrack = null
        }
    }

    private fun isPhoneCallActive(): Boolean {
        val audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio?.mode == AudioManager.MODE_IN_CALL ||
            audio?.mode == AudioManager.MODE_IN_COMMUNICATION
        ) {
            return true
        }
        val telecom = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        return runCatching { telecom?.isInCall == true }.getOrDefault(false)
    }

    enum class VoicePreset(val label: String, val pitch: Float, val description: String) {
        NATURAL("Naturelle", 1.0f, "Lecture neutre pour comparer."),
        DEEP("Plus grave", 0.78f, "Abaisse la hauteur pendant l’aperçu local."),
        HIGH("Plus aiguë", 1.28f, "Augmente la hauteur pendant l’aperçu local.")
    }

    enum class CaptureState { IDLE, RECORDING, PLAYING }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val PREVIEW_SECONDS = 3
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceStudioScreen(
    microphoneGranted: Boolean,
    captureState: VoiceStudioActivity.CaptureState,
    statusText: String,
    sampleReady: Boolean,
    selectedPreset: VoiceStudioActivity.VoicePreset,
    onPresetSelected: (VoiceStudioActivity.VoicePreset) -> Unit,
    onRequestMicrophone: () -> Unit,
    onCapture: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit
) {
    val commercialAvailability = VoiceModulatorPolicy.availability(
        VoiceModulatorPolicy.Input(
            transport = VoiceModulatorPolicy.Transport.SENTINEL_MANAGED_VOIP,
            entitlement = VoiceModulatorPolicy.Entitlement.NOT_COMMERCIALIZED,
            engineValidated = false,
            microphonePermissionGranted = microphoneGranted
        )
    )

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "Studio vocal",
                subtitle = "Add-on voix · préversion",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PhoneCoreBrand(
                context = "VOICE STUDIO",
                status = "Aperçu local",
                modifier = Modifier.fillMaxWidth()
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Modulateur de voix",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        "Testez les effets localement. L’échantillon reste uniquement en mémoire et n’est ni sauvegardé ni envoyé.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("ADD-ON PAYANT · NON COMMERCIALISÉ") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Limite Android importante", fontWeight = FontWeight.Bold)
                    Text(
                        "Sentinel ne modifie pas le flux micro d’un appel SIM/opérateur. Android ne fournit pas cette capacité publique à une application tierce. Le futur add-on live sera limité aux appels VoIP gérés de bout en bout par Sentinel, après validation technique et physique.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Changer la hauteur d’une voix ne garantit pas l’anonymat ni l’impossibilité d’identifier une personne.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text("Choisir un effet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            VoiceStudioActivity.VoicePreset.entries.forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = { onPresetSelected(preset) },
                    label = { Text(preset.label) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    preset.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!microphoneGranted) {
                Button(onClick = onRequestMicrophone, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Autoriser le micro pour l’aperçu")
                }
            } else {
                Button(
                    onClick = onCapture,
                    enabled = captureState == VoiceStudioActivity.CaptureState.IDLE,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Enregistrer 3 secondes")
                }
            }

            Button(
                onClick = onPlay,
                enabled = sampleReady && captureState == VoiceStudioActivity.CaptureState.IDLE,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Écouter l’effet")
            }

            if (captureState != VoiceStudioActivity.CaptureState.IDLE) {
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Arrêter")
                }
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Text(statusText, style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()
            Text("Disponibilité commerciale", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                when (commercialAvailability) {
                    VoiceModulatorPolicy.Availability.ENGINE_NOT_VALIDATED ->
                        "Le moteur live VoIP n’est pas encore validé : aucun achat n’est proposé."
                    VoiceModulatorPolicy.Availability.PURCHASE_REQUIRED ->
                        "Le moteur est prêt mais nécessite l’add-on."
                    VoiceModulatorPolicy.Availability.READY ->
                        "Add-on actif."
                    VoiceModulatorPolicy.Availability.PREVIEW_AVAILABLE ->
                        "Aperçu local disponible."
                    VoiceModulatorPolicy.Availability.PERMISSION_REQUIRED ->
                        "Autorisation microphone requise."
                    VoiceModulatorPolicy.Availability.UNSUPPORTED_BY_ANDROID ->
                        "Non pris en charge sur les appels opérateur."
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

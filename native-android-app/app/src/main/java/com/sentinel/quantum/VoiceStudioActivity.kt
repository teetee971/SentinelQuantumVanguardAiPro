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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sentinel.quantum.BuildConfig
import com.sentinel.quantum.auth.Auth0SessionController
import com.sentinel.quantum.auth.AuthSessionRevocation
import android.os.Handler
import android.os.Looper
import com.sentinel.quantum.security.SentinelDeviceIdentity
import com.sentinel.quantum.security.SentinelInCallService
import com.sentinel.quantum.ui.design.PhoneCoreBrand
import com.sentinel.quantum.ui.design.SentinelTopBar
import com.sentinel.quantum.ui.theme.SentinelQuantumTheme
import com.sentinel.quantum.voice.LiveKitVoiceAudioProcessor
import com.sentinel.quantum.voice.SentinelLiveKitCallTransport
import com.sentinel.quantum.voice.VoiceAddonPolicy
import com.sentinel.quantum.voice.VoiceSessionClient
import com.sentinel.quantum.voice.VoiceSessionCoordinator
import com.sentinel.quantum.voice.VoiceSessionRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

@OptIn(ExperimentalMaterial3Api::class)
class VoiceStudioActivity : ComponentActivity() {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private lateinit var previewFile: File
    private lateinit var authController: Auth0SessionController
    private lateinit var voiceSessionCoordinator: VoiceSessionCoordinator
    private var activeVoiceSession: VoiceSessionCoordinator.ActiveSession? = null
    private var pttGestureJob: Job? = null
    private var voiceConnectJob: Job? = null
    private var unsubscribeAuthRevocation: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewFile = File(cacheDir, "voice-studio-preview.m4a")
        authController = Auth0SessionController(this)
        voiceSessionCoordinator = VoiceSessionCoordinator(
            api = VoiceSessionClient(),
            transport = SentinelLiveKitCallTransport(
                this,
                LiveKitVoiceAudioProcessor()
            )
        )
        unsubscribeAuthRevocation = AuthSessionRevocation.subscribe {
            mainHandler.post {
                if (isDestroyed) return@post
                val pendingConnection = voiceConnectJob
                cancelPendingVoiceConnection()
                activeVoiceSession = null

                (application as SentinelApplication).launchVoiceTeardown {
                    pendingConnection?.join()
                    voiceSessionCoordinator.disconnect()
                }
            }
        }

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
        var showTechnicalDetails by remember { mutableStateOf(false) }
        var sessionConnected by remember { mutableStateOf(false) }
        var sessionBusy by remember { mutableStateOf(false) }
        var transmitting by remember { mutableStateOf(false) }
        var sessionStatus by remember {
            mutableStateOf("Choisissez Grave ou Aiguë pour démarrer une session PTT de test.")
        }
        var status by remember {
            mutableStateOf("Testez votre voix localement avant son utilisation dans un appel Sentinel compatible.")
        }
        val commercialState = remember { VoiceAddonPolicy.currentState() }
        val pttGestureScope = rememberCoroutineScope()
        val pttEnabled = sessionConnected && !sessionBusy

        LaunchedEffect(pttEnabled) {
            if (!pttEnabled) {
                pttGestureJob?.cancel()
                pttGestureJob = null
                transmitting = false
            }
        }

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

        fun connectVoiceSession() {
            if (sessionBusy || sessionConnected) return
            if (selectedEffect == VoiceAddonPolicy.Effect.NATURAL) {
                sessionStatus = "Sélectionnez un rendu transformé avant de démarrer la session."
                return
            }
            if (carrierCallActive()) {
                sessionStatus = "Session vocale bloquée pendant un appel mobile actif."
                return
            }
            val deviceId = SentinelDeviceIdentity.current(this@VoiceStudioActivity)
            if (deviceId == null) {
                sessionStatus = "Identifiant Android indisponible : session refusée."
                return
            }

            sessionBusy = true
            sessionStatus = "Récupération du jeton Auth0…"
            authController.accessToken(
                onSuccess = { authToken ->
                    voiceConnectJob = lifecycleScope.launch {
                        sessionStatus = "Création de la session sécurisée…"
                        val created = voiceSessionCoordinator.connect(
                            accessToken = authToken,
                            request = VoiceSessionRequest(
                                roomId = demoRoomId(deviceId),
                                deviceId = deviceId,
                                appVersion = BuildConfig.VERSION_NAME
                            ),
                            effect = selectedEffect
                        )
                        created.fold(
                            onSuccess = { active ->
                                activeVoiceSession = active
                                sessionConnected = true
                                sessionBusy = false
                                sessionStatus = "Session PTT connectée dans la salle de test isolée."
                            },
                            onFailure = { failure ->
                                sessionBusy = false
                                sessionStatus = "Session vocale refusée : ${failure.message.orEmpty()}"
                            }
                        )
                    }
                },
                onFailure = { message ->
                    sessionBusy = false
                    sessionStatus = "Jeton Auth0 indisponible : ${message.orEmpty()}"
                }
            )
        }

        val sessionMicrophoneLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                connectVoiceSession()
            } else {
                sessionStatus = "Microphone refusé. La session PTT n’a pas démarré."
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
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Session PTT Sentinel · test", fontWeight = FontWeight.Bold)
                        Text(
                            "Cette session utilise Auth0, un jeton éphémère et LiveKit. Elle ne compose pas de numéro mobile et utilise une salle de diagnostic isolée pour cet appareil.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = {
                                if (sessionConnected) {
                                    sessionBusy = true
                                    disconnectVoiceSession {
                                        sessionConnected = false
                                        transmitting = false
                                        sessionBusy = false
                                        sessionStatus = "Session PTT fermée."
                                    }
                                } else if (!sessionBusy) {
                                    if (ContextCompat.checkSelfPermission(
                                        this@VoiceStudioActivity,
                                        Manifest.permission.RECORD_AUDIO
                                    ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        sessionMicrophoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        connectVoiceSession()
                                    }
                                }
                            },
                            enabled = !sessionBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (sessionConnected) "Fermer la session" else "Démarrer la session PTT")
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    role = Role.Button
                                    contentDescription = when {
                                        transmitting -> "Relâchez pour couper le microphone"
                                        else -> "Maintenez pour parler en PTT"
                                    }
                                    if (!pttEnabled) disabled()
                                }
                                .pointerInput(pttEnabled, activeVoiceSession) {
                                    if (!pttEnabled) return@pointerInput
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        val session = activeVoiceSession ?: return@awaitEachGesture
                                        pttGestureJob?.cancel()
                                        pttGestureJob = pttGestureScope.launch {
                                            val pressResult = session.pushToTalk.press()
                                            if (pressResult.isFailure) {
                                                transmitting = false
                                                sessionStatus = "Contrôle PTT impossible : ${pressResult.exceptionOrNull()?.message.orEmpty()}"
                                                return@launch
                                            }

                                            transmitting = true
                                            sessionStatus = "Microphone ouvert : maintenez pour parler."
                                            try {
                                                awaitCancellation()
                                            } finally {
                                                val releaseResult = releasePushToTalk()
                                                transmitting = false
                                                sessionStatus = if (releaseResult.isSuccess) {
                                                    "Microphone coupé : session PTT toujours connectée."
                                                } else {
                                                    "Fermeture du microphone impossible : ${releaseResult.exceptionOrNull()?.message.orEmpty()}"
                                                }
                                            }
                                        }
                                        waitForUpOrCancellation()
                                        pttGestureJob?.cancel()
                                        pttGestureJob = null
                                    }
                                },
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(
                                1.dp,
                                if (pttEnabled) {
                                    MaterialTheme.colorScheme.outline
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                }
                            ),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when {
                                        transmitting -> "Relâchez pour couper"
                                        else -> "Maintenez pour parler"
                                    },
                                    color = if (pttEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    }
                                )
                            }
                        }

                        Text(
                            sessionStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Appels avec voix transformée",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "En préparation",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            "Vous pouvez enregistrer et écouter vos rendus localement, ou lancer la session PTT de test ci-dessus. L’appel vers un numéro mobile n’est pas activé.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "La session de test ne remplace pas encore la certification d’un appel réel : elle valide uniquement Auth0, l’accès éphémère LiveKit et le bouton PTT.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { showTechnicalDetails = !showTechnicalDetails },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                if (showTechnicalDetails)
                                    "Masquer les détails techniques"
                                else
                                    "Afficher les détails techniques"
                            )
                        }
                        if (showTechnicalDetails) {
                            HorizontalDivider()
                            Text(
                                "Transformation en appel · intégration obligatoire",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Le moteur temps réel, le post-traitement LiveKit/WebRTC et le transport média côté client sont intégrés. Aucun parcours utilisateur ne lance encore une session d’appel Sentinel réelle : la session PTT ci-dessus reste un diagnostic contrôlé et sans numéro mobile.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Il reste à raccorder une session d’appel authentifiée : émission serveur de jetons éphémères, serveur LiveKit/signaling, contrôle d’accès et passerelle VoIP/PSTN pour joindre les numéros classiques. L’audio d’un appel SIM natif reste hors du chemin média public d’une application Android tierce.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Session d’appel/PSTN non raccordée",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Session d’appel/PSTN non raccordée. Aucun bouton d’appel n’est affiché pour joindre un numéro mobile. La passerelle VoIP/PSTN, la validation physique et la certification d’appel restent nécessaires avant toute activation commerciale.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

    private fun demoRoomId(deviceId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("sentinel-voice-demo:v1:$deviceId".toByteArray(Charsets.UTF_8))
        val suffix = digest.take(16).joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
        return "demo-$suffix"
    }

    private suspend fun releasePushToTalk(): Result<Unit> {
        val session = activeVoiceSession ?: return Result.success(Unit)
        return withContext(NonCancellable) {
            session.pushToTalk.release()
        }
    }

    private fun cancelPendingVoiceConnection() {
        voiceConnectJob?.cancel()
        voiceConnectJob = null
        pttGestureJob?.cancel()
        pttGestureJob = null
    }

    private fun disconnectVoiceSession(onFinished: () -> Unit) {
        val active = activeVoiceSession
        if (active == null) {
            onFinished()
            return
        }
        lifecycleScope.launch {
            voiceSessionCoordinator.disconnect()
            activeVoiceSession = null
            onFinished()
        }
    }

    override fun onStop() {
        pttGestureJob?.cancel()
        pttGestureJob = null
        if (activeVoiceSession != null) {
            (application as SentinelApplication).launchVoiceTeardown {
                releasePushToTalk()
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        unsubscribeAuthRevocation?.invoke()
        unsubscribeAuthRevocation = null
        val pendingConnection = voiceConnectJob
        cancelPendingVoiceConnection()
        stopRecording()
        stopPlayback()
        runCatching { previewFile.delete() }
        activeVoiceSession = null

        (application as SentinelApplication).launchVoiceTeardown {
            pendingConnection?.join()
            voiceSessionCoordinator.disconnect()
        }
        super.onDestroy()
    }
}
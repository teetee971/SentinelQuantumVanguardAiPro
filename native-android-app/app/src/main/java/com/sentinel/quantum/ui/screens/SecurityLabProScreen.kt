package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.layout.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.widget.ImageView
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalContext
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.clip
import androidx.navigation.NavController
import com.sentinel.quantum.security.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import android.net.Uri

/** Local-only security inspection. A self-inspection is always visible even with package visibility limits. */
@Composable
fun SecurityLabProScreen(navController: NavController) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<AppIntegrityReport?>(null) }
    var selfInspection by remember { mutableStateOf(ApkInspectionActivityState()) }
    var selection by remember { mutableStateOf(ApkSelectionState()) }
    val firstApk = selection.reference
    val secondApk = selection.candidate
    val comparison = selection.comparison
    var error by remember { mutableStateOf<String?>(null) }
    var selectionBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val apkManager = remember(context) { SentinelApkManager(context.applicationContext) }
    val installedScanner = remember(context) { InstalledAppScanner(context.applicationContext) }
    var visibleApps by remember { mutableStateOf<List<VisibleInstalledApp>>(emptyList()) }
    var scanBusy by remember { mutableStateOf(false) }
    var appSearch by remember { mutableStateOf("") }
    var appsFilter by remember { mutableStateOf(0) }
    var labTab by remember { mutableStateOf(0) }
    var volumetricEnabled by remember { mutableStateOf(false) }
    var installPending by remember { mutableStateOf<Pair<Uri, ApkEvidence>?>(null) }
    var uninstallPending by remember { mutableStateOf<String?>(null) }
    var uninstallInput by remember { mutableStateOf("") }
    var managerError by remember { mutableStateOf<String?>(null) }
    var auditRevision by remember { mutableIntStateOf(0) }
    val installPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            selectionBusy = true
            managerError = null
            try {
                val evidence = SelectedApkInspector(context.applicationContext).inspect(uri)
                if (evidence.signatureVerified != true) {
                    managerError = "Signature APK non vérifiée : installation refusée."
                } else if (evidence.packageName == "com.sentinel.quantum" || evidence.packageName == "com.sentinel.quantum.labpreview") {
                    managerError = "APK Sentinel protégé : utiliser le parcours de mise à jour dédié, hors du laboratoire."
                } else {
                    installPending = uri to evidence
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                managerError = "Analyse préalable impossible : ${e.message ?: "erreur"}"
            } finally {
                selectionBusy = false
            }
        }
    }
    installPending?.let { pending ->
        AlertDialog(
            onDismissRequest = { installPending = null },
            title = { Text("Confirmer la demande d'installation") },
            text = { Text("APK : ${pending.second.packageName ?: "inconnu"}. Android demandera une confirmation supplémentaire. Aucun résultat d'installation n'est encore connu.") },
            confirmButton = { TextButton(onClick = {
                try {
                    apkManager.installUserSelected(pending.first, pending.second)
                    auditRevision++
                } catch (e: Exception) { managerError = e.message ?: "Installateur indisponible" }
                installPending = null
            }) { Text("Ouvrir l'installateur Android") } },
            dismissButton = { TextButton(onClick = { installPending = null }) { Text("Annuler") } }
        )
    }
    uninstallPending?.let { packageName ->
        AlertDialog(
            onDismissRequest = { uninstallPending = null },
            title = { Text("Demander la désinstallation ?") },
            text = { Text("Application : $packageName. Android exigera ta confirmation. Les données de l'application peuvent être perdues.") },
            confirmButton = { TextButton(onClick = {
                try {
                    apkManager.requestUninstall(packageName)
                    auditRevision++
                } catch (e: Exception) { managerError = e.message ?: "Désinstallation indisponible" }
                uninstallPending = null
            }) { Text("Ouvrir la confirmation Android") } },
            dismissButton = { TextButton(onClick = { uninstallPending = null }) { Text("Annuler") } }
        )
    }

    val firstPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            selectionBusy = true
            error = null
            selection = selection.startReferenceReplacement()
            try { selection = selection.referenceLoaded(SelectedApkInspector(context.applicationContext).inspect(uri)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = "Premier APK non lisible : ${e.message ?: "erreur"}" }
            finally { selectionBusy = false }
        }
    }
    val secondPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            selectionBusy = true
            error = null
            selection = selection.startCandidateReplacement()
            try { selection = selection.candidateLoaded(SelectedApkInspector(context.applicationContext).inspect(uri)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = "Second APK non lisible : ${e.message ?: "erreur"}" }
            finally { selectionBusy = false }
        }
    }
    LaunchedEffect(context) {
        selfInspection = selfInspection.started()
        try {
            report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                InstalledAppIntegrityInspector(context.applicationContext).inspect(context.packageName)
            }
        } catch (e: CancellationException) {
            selfInspection = selfInspection.cancelled()
            throw e
        } catch (e: Exception) {
            selfInspection = selfInspection.failed("Inspection locale indisponible : ${e.message ?: "erreur"}")
        } finally {
            if (selfInspection.loading) selfInspection = selfInspection.completed()
        }
    }
    Scaffold(topBar = { Text("Security Lab Pro", style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(20.dp)) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Analyse locale des applications. Toute installation ou désinstallation nécessite votre confirmation Android.",
                    style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { navController.popBackStack() }) { Text("Retour") }
                if (selfInspection.loading || selectionBusy) CircularProgressIndicator()
                selfInspection.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1628)),
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.dp, Color(0xFF3D83AB))
                ) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 190.dp).clip(RoundedCornerShape(26.dp))) {
                        if (volumetricEnabled) D1VolumetricSurface(Modifier.matchParentSize())
                        else D1HolographicField(Modifier.matchParentSize())
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("SENTINEL / D.1", color = Color(0xFF5DDEFF), style = MaterialTheme.typography.labelLarge)
                            Text("APK INTELLIGENCE", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "Analyse locale • Inventaire contrôlé • Gestion avec consentement",
                                color = Color(0xFFC0D6E9),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(.76f)
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Volume 3D (GPU) • expérimental", style = MaterialTheme.typography.labelMedium)
                    Switch(checked = volumetricEnabled, onCheckedChange = { volumetricEnabled = it })
                }
                SingleChoiceSegmentedButtonRow {
                    listOf("Scanner", "APK", "Journal").forEachIndexed { index, label ->
                        SegmentedButton(selected = labTab == index, onClick = { labTab = index },
                            shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(label) }
                    }
                }
                if (labTab == 0) {
                Button(enabled = !scanBusy, onClick = {
                    scope.launch {
                        scanBusy = true
                        try {
                            visibleApps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { installedScanner.scan() }
                        } catch (e: Exception) { managerError = "Inventaire indisponible : ${e.message ?: "erreur"}" }
                        finally { scanBusy = false }
                    }
                }) { Text("Scanner les applications installées") }
                if (scanBusy) CircularProgressIndicator()
                if (visibleApps.isNotEmpty()) {
                    Text("${visibleApps.size} application(s) visibles par Android — inventaire potentiellement partiel.")
                    Text("Applications détectées", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow {
                        listOf("Utilisateur", "Système", "Toutes").forEachIndexed { index, title ->
                            SegmentedButton(selected = appsFilter == index, onClick = { appsFilter = index },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)) { Text(title) }
                        }
                    }
                    OutlinedTextField(value = appSearch, onValueChange = { appSearch = it },
                        label = { Text("Rechercher une application") }, singleLine = true)
                    visibleApps.filter { (appsFilter == 2 || (appsFilter == 0 && !it.system) || (appsFilter == 1 && it.system)) &&
                        (it.label.contains(appSearch, true) || it.packageName.contains(appSearch, true)) }
                        .take(25).forEach { app ->
                            Card(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF273D59)),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF151C2B))) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AndroidView(
                                    modifier = Modifier.size(44.dp),
                                    factory = { viewContext -> ImageView(viewContext).apply {
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                        contentDescription = "Icône de l'application"
                                    } },
                                    update = { image ->
                                        val icon = try {
                                            context.packageManager.getApplicationIcon(app.packageName)
                                        } catch (_: Exception) {
                                            context.packageManager.defaultActivityIcon
                                        }
                                        image.setImageDrawable(icon)
                                        image.contentDescription = "Icône de ${app.label}"
                                    }
                                )
                                Column {
                                    Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                try { installedScanner.openDetails(app.packageName) }
                                catch (e: Exception) { managerError = e.message ?: "Fiche Android indisponible" }
                            }) { Text("Fiche Android") }
                            if (!app.system && app.packageName != "com.sentinel.quantum" && app.packageName != "com.sentinel.quantum.labpreview") {
                                TextButton(onClick = { uninstallPending = app.packageName }) { Text("Désinstaller…") }
                            }
                            }
                                }
                            }
                        }
                }
                Text("Inventaire limité aux applications visibles par Android.", style = MaterialTheme.typography.bodySmall)
                }
                if (labTab == 1) {
                Text("FICHIERS APK", color = Color(0xFF53CFFF), style = MaterialTheme.typography.labelLarge)
                Text("Analyse, installation contrôlée et comparaison", style = MaterialTheme.typography.titleMedium)

                Button(enabled = !selectionBusy, onClick = { installPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")) }) {
                    Text("Analyser puis demander l'installation")
                }
                HorizontalDivider()
                Text("Désinstallation avec confirmation Android", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = uninstallInput, onValueChange = { uninstallInput = it.trim() },
                    label = { Text("Identifiant Android à désinstaller") }, singleLine = true)
                Button(enabled = uninstallInput.isNotBlank() && uninstallInput != "com.sentinel.quantum" &&
                    uninstallInput != "com.sentinel.quantum.labpreview",
                    onClick = { uninstallPending = uninstallInput }) { Text("Demander la désinstallation") }
                managerError?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { managerError = null }) { Text("Effacer le message") }
                }
                }
                if (labTab == 2) {
                Text("HISTORIQUE LOCAL", color = Color(0xFF53CFFF), style = MaterialTheme.typography.labelLarge)
                Text("Demandes envoyées à Android ; aucun succès n'est présumé.", style = MaterialTheme.typography.bodySmall)
                @Suppress("UNUSED_VARIABLE") val refreshAudit = auditRevision
                val recentEntries = apkManager.entries()
                if (recentEntries.isEmpty()) {
                    Card(shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFF355C86)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111C30))) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Aucune demande enregistrée", style = MaterialTheme.typography.titleMedium)
                            Text("Les demandes d'installation et de désinstallation apparaîtront ici après ouverture de leur confirmation Android. Une demande ne prouve pas sa réussite.",
                                style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA9B9D4))
                        }
                    }
                } else {
                    recentEntries.take(15).forEach { entry ->
                        Card(shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF151C2B))) {
                            Text(entry, modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                }
                if (labTab == 1) {
                HorizontalDivider()
                Text("Comparer deux APK sélectionnés sur ce téléphone (sans installation).")
                Button(enabled = !selectionBusy, onClick = { firstPicker.launch(arrayOf("*/*")) }) {
                    Text("Choisir APK de référence")
                }
                Button(enabled = !selectionBusy, onClick = { secondPicker.launch(arrayOf("*/*")) }) {
                    Text("Choisir APK à comparer")
                }
                firstApk?.let { Text("Référence : ${it.packageName ?: "Non identifié"} / build ${it.versionCode ?: "?"}") }
                secondApk?.let { Text("Comparaison : ${it.packageName ?: "Non identifié"} / build ${it.versionCode ?: "?"}") }
                Button(
                    enabled = firstApk != null && secondApk != null && !selectionBusy,
                    onClick = { selection = selection.withComparison(ApkComparisonEngine.compare(
                        firstApk!!, secondApk!!, System.currentTimeMillis()
                    )) }
                ) { Text("Comparer les deux APK") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            if (labTab == 1) comparison?.let { result ->
                item {
                    val ok = result.findings.count { it.status == AppIntegrityStatus.OK }
                    val warn = result.findings.count { it.status == AppIntegrityStatus.WARN }
                    val bad = result.findings.count { it.status == AppIntegrityStatus.BAD }
                    val unknown = result.findings.count { it.status == AppIntegrityStatus.UNKNOWN }
                    Text("Synthèse : $ok vérifié(s) · $warn à revoir · $bad invalide(s) · $unknown indéterminé(s)", style = MaterialTheme.typography.titleSmall)
                }
                item {
                    Text(when {
                        result.hasReviewRequired -> "Revue nécessaire : au moins une différence ou anomalie demande vérification."
                        result.hasUnknownEvidence -> "Analyse incomplète : certaines preuves sont indéterminées."
                        else -> "Aucune divergence détectée sur les preuves vérifiées ; sécurité globale non garantie."
                    })
                }
                item {
                    if (firstApk?.packageName != null && secondApk?.packageName != null && firstApk.packageName != secondApk.packageName) {
                        Text("Ces fichiers correspondent à deux identifiants Android différents. Il ne s’agit pas d’une comparaison de mise à jour de la même application.", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Résultats — divergence ne prouve pas un piratage",
                        style = MaterialTheme.typography.titleMedium)
                }
                items(result.findings, key = { "apk_" + it.id }) { finding ->
                    ApkComparisonFindingCard(finding)
                }
            }
            val current = report
            if (current != null && labTab == 0) {
                item {
                    Text("Application : ${current.packageName ?: "Inconnue"}")
                    Text("Build : ${current.versionCode ?: "Non vérifiable"}")
                    Text("État : ${current.overallStatus.name} — preuve limitée à l'inspection locale")
                }
                items(current.findings, key = { it.id }) { finding ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("${finding.status.name} • ${finding.title}",
                                style = MaterialTheme.typography.titleMedium)
                            Text(finding.explanation)
                            finding.evidence?.let { Text(it, style = MaterialTheme.typography.bodySmall, overflow = TextOverflow.Ellipsis, maxLines = 3) }
                            Text("Source : ${finding.source.name}",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (labTab == 0) item {
                Text("Les applications tierces peuvent être masquées par Android. Aucun verdict de sûreté n'est déduit de leur absence.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Isolated finding component for emulator UI validation with known test evidence. */
@Composable
internal fun ApkComparisonFindingCard(finding: AppIntegrityFinding) {
    var expanded by remember(finding.id) { mutableStateOf(false) }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            val label = when (finding.status) {
                AppIntegrityStatus.OK -> "Vérifié"
                AppIntegrityStatus.WARN -> "À examiner"
                AppIntegrityStatus.BAD -> "Invalide"
                AppIntegrityStatus.UNKNOWN -> "Indéterminé"
            }
            Text("$label — ${finding.title}", style = MaterialTheme.typography.titleMedium)
            Text(finding.explanation, maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Réduire" else "Afficher les détails")
            }
        }
    }
}


/**
 * D.1 decorative layer: deterministic, non-interactive and inexpensive.
 * Particles/rings are drawn rather than downloaded; no access to app or user data.
 * Static until motion preferences and hardware performance are validated on-device.
 */
@Composable
private fun D1HolographicField(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val motionAllowed = remember(context) {
        val power = context.getSystemService(PowerManager::class.java)
        val systemScale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        systemScale > 0f && power?.isPowerSaveMode != true
    }
    val transition = rememberInfiniteTransition(label = "d1AmbientParticles")
    val animatedPhase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "d1ParticleDrift"
    )
    val phase = if (motionAllowed) animatedPhase else 0f
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w * .84f, h * .48f)
        drawRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFF081427), Color(0xFF112B48), Color(0xFF081427)),
                start = Offset.Zero, end = Offset(w, h)
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x5545CFFF), Color(0x1125AEE8), Color.Transparent),
                center = center, radius = h * .72f
            ),
            radius = h * .72f, center = center
        )
        for (ring in 0..3) {
            drawCircle(
                color = Color(0xFF4AD5FF).copy(alpha = .36f - ring * .055f),
                radius = h * (.15f + ring * .075f),
                center = center,
                style = Stroke(width = if (ring == 0) 2.5f else 1.3f)
            )
        }
        for (line in 0..5) {
            val path = Path().apply {
                moveTo(w * (.50f + line * .075f), h)
                lineTo(center.x, center.y)
            }
            drawPath(path, Color(0xFF56CCF2).copy(alpha = .10f), style = Stroke(width = 1f))
        }
        // Stable particle coordinates prevent flickering and avoid frame-by-frame allocations.
        for (i in 0 until 26) {
            val x = w * (.50f + ((i * 37) % 49) / 100f)
            val y = h * (.06f + ((((i * 29) % 88) / 100f + phase * (.025f + (i % 3) * .012f)) % .88f))
            drawCircle(
                color = Color(0xFF96E8FF).copy(alpha = .24f + (i % 4) * .12f),
                radius = if (i % 5 == 0) 2.2f else 1.2f,
                center = Offset(x, y)
            )
        }
    }
}


@Composable
private fun D1VolumetricSurface(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val animate = remember(context) {
        val power = context.getSystemService(PowerManager::class.java)
        power?.isPowerSaveMode != true &&
            runCatching {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
            }.getOrDefault(true)
    }
    val scene = remember(context, animate) { D1VolumetricView(context, animate) }
    DisposableEffect(owner, scene) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> scene.suspendRendering()
                Lifecycle.Event.ON_RESUME -> scene.resumeRendering()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            scene.suspendRendering()
        }
    }
    AndroidView(factory = { scene }, modifier = modifier)
}

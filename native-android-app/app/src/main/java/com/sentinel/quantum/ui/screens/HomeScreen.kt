package com.sentinel.quantum.ui.screens

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.sentinel.quantum.PhoneCoreActivationActivity
import com.sentinel.quantum.PhoneCoreRuntimeFacts
import com.sentinel.quantum.PhoneCoreSetupWizardStore
import com.sentinel.quantum.R
import com.sentinel.quantum.SentinelDialerActivity
import com.sentinel.quantum.SmsComposeActivity
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.security.GeneratedProductCapabilities
import com.sentinel.quantum.ui.design.MotionLevel
import com.sentinel.quantum.ui.design.SentinelActionTile
import com.sentinel.quantum.ui.design.SentinelCoreModule
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelGlassCard
import com.sentinel.quantum.ui.design.SentinelHolographicScene
import com.sentinel.quantum.ui.design.SentinelHolographicSceneModel
import com.sentinel.quantum.ui.design.SentinelReferenceAction
import com.sentinel.quantum.ui.design.SentinelReferenceActionGrid
import com.sentinel.quantum.ui.design.SentinelReferenceMetric
import com.sentinel.quantum.ui.design.SentinelReferenceMetricStrip
import com.sentinel.quantum.ui.design.SentinelMotionPolicy
import com.sentinel.quantum.ui.design.SentinelSceneKind
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelStatus
import com.sentinel.quantum.ui.design.SentinelStatusChip
import com.sentinel.quantum.ui.design.SentinelTopBar
import com.sentinel.quantum.ui.design.visual
import com.sentinel.quantum.ui.presentation.HomeComponentFacts
import com.sentinel.quantum.ui.presentation.HomeCondition
import com.sentinel.quantum.ui.presentation.HomeModuleId
import com.sentinel.quantum.ui.presentation.HomeModulePresentation
import com.sentinel.quantum.ui.presentation.HomeRuntimeFacts
import com.sentinel.quantum.ui.presentation.HomeSecurityPresentation
import java.util.Locale

private enum class HomeDomain(val label: String, val description: String) {
    COMMUNICATIONS("Téléphone & messages", "Appels, contacts, SMS/MMS et identification"),
    PROTECTION("Protection", "Sécurité, permissions, blocage et diagnostic"),
    NETWORK("Réseau & appareils", "Wi-Fi, Bluetooth, VPN et objets connectés"),
    ANALYSIS("Identité & analyses", "E-mails, exposition numérique et contenus suspects"),
    WATCH("Veille", "Événements et signaux OSINT")
}

private data class HomeTool(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val keywords: Set<String>,
    val domain: HomeDomain,
    val featured: Boolean = false,
    val capabilityId: String? = null,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    var toolQuery by rememberSaveable { mutableStateOf("") }
    var showAllTools by rememberSaveable { mutableStateOf(false) }
    var postureEpoch by remember { mutableStateOf(0) }

    val activity = context as? ComponentActivity
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) postureEpoch++
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    val phoneCoreFacts = remember(postureEpoch) {
        PhoneCoreRuntimeFacts.read(context.applicationContext)
    }
    val phoneCoreChecks = listOf(
        phoneCoreFacts.corePermissionsReady,
        phoneCoreFacts.dialerRoleHeld,
        phoneCoreFacts.callScreeningRoleHeld,
        phoneCoreFacts.callLogPermissionGranted,
        phoneCoreFacts.smsRoleHeld,
        phoneCoreFacts.smsRuntimePermissionsReady,
        phoneCoreFacts.mmsPermissionsReady,
        phoneCoreFacts.notificationChannelsReady
    )
    val readyCount = phoneCoreChecks.count { it }
    val phoneCoreReady = PhoneCoreSetupWizardStore.softwarePrerequisitesReady(phoneCoreFacts)
    val nextPhoneCoreStep = PhoneCoreSetupWizardStore.nextStep(phoneCoreFacts)

    val motionLevel = remember(postureEpoch) { resolveHomeMotion(context) }
    val phoneCapability = GeneratedProductCapabilities.states["phone_core_android"]
    val vpnCapability = GeneratedProductCapabilities.states["sentinel_vpn_service"]
    val collectiveCapability = GeneratedProductCapabilities.states["collective_defense_android"]

    val homeState = remember(phoneCoreFacts, postureEpoch) {
        HomeSecurityPresentation.from(
            HomeRuntimeFacts(
                phoneCore = HomeComponentFacts(
                    implemented = phoneCapability?.implemented ?: true,
                    customerAvailable = phoneCapability?.availableAt() == true,
                    evidenceObserved = false,
                    condition = if (phoneCoreReady) HomeCondition.HEALTHY else HomeCondition.DEGRADED,
                    locked = !phoneCoreFacts.dialerRoleHeld || !phoneCoreFacts.callScreeningRoleHeld ||
                        !phoneCoreFacts.smsRoleHeld,
                    requiredRolesAvailable = phoneCoreFacts.dialerRoleAvailable &&
                        phoneCoreFacts.callScreeningRoleAvailable && phoneCoreFacts.smsRoleAvailable
                ),
                vpn = HomeComponentFacts(
                    implemented = vpnCapability?.implemented ?: false,
                    customerAvailable = vpnCapability?.availableAt() == true,
                    evidenceObserved = false
                ),
                network = HomeComponentFacts(
                    implemented = true,
                    customerAvailable = true,
                    evidenceObserved = false
                ),
                collectiveDefense = HomeComponentFacts(
                    implemented = collectiveCapability?.implemented ?: false,
                    customerAvailable = collectiveCapability?.availableAt() == true,
                    evidenceObserved = false
                )
            )
        )
    }
    val phoneCoreModule = homeState.modules.first { it.id == HomeModuleId.PHONE_CORE }
    val vpnModule = homeState.modules.first { it.id == HomeModuleId.VPN }
    val networkModule = homeState.modules.first { it.id == HomeModuleId.NETWORK }
    val communityModule = homeState.modules.first { it.id == HomeModuleId.COLLECTIVE_DEFENSE }
    val coreModules = listOf(
        SentinelCoreModule(
            id = "phone",
            label = "Téléphone",
            status = phoneCoreModule.status,
            icon = Icons.Default.Phone
        ),
        SentinelCoreModule(
            id = "messages",
            label = "SMS/MMS",
            status = if (phoneCoreFacts.smsRoleHeld) SentinelStatus.UNKNOWN else SentinelStatus.LOCKED,
            icon = Icons.Default.Sms
        ),
        SentinelCoreModule(
            id = "network",
            label = "Réseau",
            status = networkModule.status,
            icon = Icons.Default.Wifi
        ),
        SentinelCoreModule(
            id = "vpn",
            label = "VPN",
            status = vpnModule.status,
            icon = Icons.Default.VpnLock
        ),
        SentinelCoreModule(
            id = "exposure",
            label = "Empreinte",
            status = SentinelStatus.UNKNOWN,
            icon = Icons.Default.Fingerprint
        ),
        SentinelCoreModule(
            id = "threats",
            label = "Menaces",
            status = SentinelStatus.UNKNOWN,
            icon = Icons.Default.WarningAmber
        ),
        SentinelCoreModule(
            id = "community",
            label = "Communauté",
            status = communityModule.status,
            icon = Icons.Default.Groups
        ),
        SentinelCoreModule(
            id = "system",
            label = "Système",
            status = SentinelStatus.UNKNOWN,
            icon = Icons.Default.Security
        )
    )

    val tools = homeTools(context, navController)
    val normalizedQuery = toolQuery.trim().lowercase(Locale.FRENCH)
    val matchingTools = if (normalizedQuery.isBlank()) {
        if (showAllTools) tools else tools.filter { it.featured }
    } else {
        tools.filter { tool ->
            tool.title.lowercase(Locale.FRENCH).contains(normalizedQuery) ||
                tool.subtitle.lowercase(Locale.FRENCH).contains(normalizedQuery) ||
                tool.keywords.any { it.contains(normalizedQuery) || normalizedQuery.contains(it) }
        }
    }

    Scaffold(
        topBar = {
            SentinelTopBar(
                title = "Sentinel",
                subtitle = "QUANTUM VANGUARD AI PRO · état observé"
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SentinelActionTile(
                title = when {
                    phoneCoreModule.status == SentinelStatus.LIMITED -> "Examiner Phone Core"
                    phoneCoreReady -> "Vérifier Phone Core"
                    else -> "Continuer l’activation"
                },
                subtitle = when {
                    phoneCoreModule.status == SentinelStatus.LIMITED -> phoneCoreModule.detail
                    phoneCoreReady -> "$readyCount/8 étapes prêtes · validation physique distincte."
                    else -> "$readyCount/8 étapes d’activation Android prêtes · prochaine étape : ${PhoneCoreSetupWizardStore.stepLabel(nextPhoneCoreStep)}"
                },
                icon = Icons.Default.Shield,
                onClick = { context.startActivity(Intent(context, PhoneCoreActivationActivity::class.java)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_primary_action")
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_security_hero")
            ) {
                SentinelHolographicScene(
                    model = SentinelHolographicSceneModel(
                        kind = SentinelSceneKind.COMMAND_CENTER,
                        status = homeState.overallStatus,
                        title = "SENTINEL COMMAND CENTER",
                        evidenceLabel = homeState.headline,
                        evidenceSource = "états runtime Android",
                        nodes = coreModules.map {
                            com.sentinel.quantum.ui.design.SentinelHolographicSceneNode(
                                label = it.label,
                                status = it.status
                            )
                        }
                    ),
                    motionLevel = motionLevel,
                    modifier = Modifier.testTag("sentinel_quantum_core")
                )
            }

            SentinelReferenceMetricStrip(
                metrics = listOf(
                    SentinelReferenceMetric(
                        label = "Phone Core",
                        value = phoneCoreModule.status.visual().label,
                        detail = "$readyCount/8 prérequis observés",
                        status = phoneCoreModule.status
                    ),
                    SentinelReferenceMetric(
                        label = "VPN",
                        value = vpnModule.status.visual().label,
                        detail = "Tunnel non présumé",
                        status = vpnModule.status
                    ),
                    SentinelReferenceMetric(
                        label = "Réseau",
                        value = networkModule.status.visual().label,
                        detail = "État local observé",
                        status = networkModule.status
                    ),
                    SentinelReferenceMetric(
                        label = "Défense collective",
                        value = communityModule.status.visual().label,
                        detail = "Signalements volontaires",
                        status = communityModule.status
                    )
                ),
                modifier = Modifier.testTag("home_reference_metrics")
            )

            SentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Priorité", style = MaterialTheme.typography.labelLarge, color = SentinelD1.Cyan)
                Text(homeState.priorityMessage, fontWeight = FontWeight.Bold)
                Text(
                    "Les états inconnus, limités ou verrouillés restent visibles : Sentinel ne les transforme pas en protection confirmée.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SentinelSectionHeader(title = "Protection principale")
            homeState.modules.forEach { module ->
                HomeModuleCard(
                    module = module,
                    modifier = Modifier.testTag(moduleTag(module.id)),
                    onClick = { navigateHomeModule(module.id, navController, context) }
                )
            }

            SentinelGlassCard(modifier = Modifier.fillMaxWidth().testTag("home_activity_summary")) {
                Text("Activité système", style = MaterialTheme.typography.titleMedium)
                Text(homeState.activitySummary, style = MaterialTheme.typography.bodySmall)
            }

            OutlinedTextField(
                value = toolQuery,
                onValueChange = { toolQuery = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Chercher une fonction") },
                placeholder = { Text("Ex. contact, SMS, Wi-Fi, VPN, e-mail…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (toolQuery.isNotEmpty()) {
                        IconButton(onClick = { toolQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer la recherche")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            SentinelSectionHeader(
                title = if (normalizedQuery.isBlank()) "Accès directs" else "Résultats",
                trailing = if (normalizedQuery.isBlank()) null else "${matchingTools.size}"
            )

            if (matchingTools.isEmpty()) {
                SentinelGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Aucun résultat", fontWeight = FontWeight.Bold)
                    Text(
                        "Essayez appel, SMS, Wi-Fi, VPN, e-mail, blocage ou permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (normalizedQuery.isBlank() && !showAllTools) {
                SentinelReferenceActionGrid(
                    actions = matchingTools.take(4).map { tool ->
                        SentinelReferenceAction(
                            label = tool.title,
                            detail = capabilitySubtitle(tool),
                            icon = tool.icon,
                            onClick = tool.onClick,
                            status = SentinelStatus.UNKNOWN
                        )
                    },
                    modifier = Modifier.testTag("home_reference_actions")
                )
            } else if (normalizedQuery.isNotBlank()) {
                matchingTools.forEach { tool -> HomeToolTile(tool) }
            } else {
                HomeDomain.entries.forEach { domain ->
                    val domainTools = matchingTools.filter { it.domain == domain }
                    if (domainTools.isNotEmpty()) {
                        SentinelSectionHeader(title = domain.label)
                        Text(
                            domain.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        domainTools.forEach { tool -> HomeToolTile(tool) }
                    }
                }
            }

            if (normalizedQuery.isBlank()) {
                TextButton(
                    onClick = { showAllTools = !showAllTools },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (showAllTools) Icons.Default.ExpandLess else Icons.Default.GridView,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (showAllTools) "Revenir aux actions essentielles" else "Explorer toutes les protections")
                }
            }
        }
    }
}

@Composable
private fun HomeModuleCard(
    module: HomeModulePresentation,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    SentinelGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(module.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    module.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            SentinelStatusChip(module.status)
        }
    }
}

@Composable
private fun QuickToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 120.dp)
            .border(1.dp, SentinelD1.BorderStrong.copy(alpha = 0.72f), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = SentinelD1.Card.copy(alpha = 0.92f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = SentinelD1.Cyan)
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
        }
    }
}

@Composable
private fun HomeToolTile(tool: HomeTool) {
    SentinelActionTile(
        title = tool.title,
        subtitle = capabilitySubtitle(tool),
        icon = tool.icon,
        onClick = tool.onClick,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun moduleTag(id: HomeModuleId): String = when (id) {
    HomeModuleId.PHONE_CORE -> "home_module_phone_core"
    HomeModuleId.VPN -> "home_module_vpn"
    HomeModuleId.NETWORK -> "home_module_network"
    HomeModuleId.COLLECTIVE_DEFENSE -> "home_module_collective_defense"
}

private fun navigateHomeModule(id: HomeModuleId, navController: NavController, context: Context) {
    when (id) {
        HomeModuleId.PHONE_CORE -> context.startActivity(Intent(context, PhoneCoreActivationActivity::class.java))
        HomeModuleId.VPN -> navController.navigate(Screen.Vpn.route)
        HomeModuleId.NETWORK -> navController.navigate(Screen.NetworkSurveillance.route)
        HomeModuleId.COLLECTIVE_DEFENSE -> navController.navigate(Screen.CollectiveDefense.route)
    }
}

private fun navigateCoreModule(id: String, navController: NavController, context: Context) {
    when (id) {
        "phone" -> context.startActivity(Intent(context, SentinelDialerActivity::class.java))
        "messages" -> context.startActivity(Intent(context, SmsComposeActivity::class.java))
        "network" -> navController.navigate(Screen.NetworkSurveillance.route)
        "vpn" -> navController.navigate(Screen.Vpn.route)
        "exposure" -> navController.navigate(Screen.DigitalExposure.route)
        "threats" -> navController.navigate(Screen.SecurityAudit.route)
        "community" -> navController.navigate(Screen.CollectiveDefense.route)
        "system" -> navController.navigate(Screen.SystemDoctor.route)
    }
}

private fun resolveHomeMotion(context: Context): MotionLevel {
    val animatorScale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    val powerSave = runCatching {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    }.getOrDefault(false)
    return SentinelMotionPolicy.resolve(animatorScale, powerSave)
}

@Composable
private fun capabilitySubtitle(tool: HomeTool): String {
    val id = tool.capabilityId ?: return tool.subtitle
    val state = GeneratedProductCapabilities.states[id]
    val label = when {
        state?.availableAt() == true -> R.string.product_capability_available
        state?.implemented == false -> R.string.product_capability_not_implemented
        state?.customerAvailable == false -> R.string.product_capability_preproduction
        else -> R.string.product_capability_unavailable
    }
    return stringResource(label) + " · " + tool.subtitle
}

private fun homeTools(context: Context, navController: NavController): List<HomeTool> = listOf(
    HomeTool(
        "Téléphone",
        "Appeler, retrouver un contact ou consulter les appels récents",
        Icons.Default.Phone,
        setOf("appel", "appeler", "telephone", "téléphone", "contact", "contacts", "recent", "récents"),
        HomeDomain.COMMUNICATIONS,
        featured = true,
        capabilityId = "phone_core_android"
    ) { context.startActivity(Intent(context, SentinelDialerActivity::class.java)) },
    HomeTool(
        "Répertoire",
        "Retrouver vos contacts, appeler ou écrire en quelques secondes",
        Icons.Default.Contacts,
        setOf("contact", "contacts", "repertoire", "répertoire", "annuaire"),
        HomeDomain.COMMUNICATIONS,
        featured = true
    ) {
        context.startActivity(
            Intent(context, SentinelDialerActivity::class.java)
                .putExtra(SentinelDialerActivity.EXTRA_OPEN_CONTACTS, true)
        )
    },
    HomeTool(
        "Messages",
        "Lire les conversations ou écrire un SMS",
        Icons.Default.Sms,
        setOf("sms", "message", "messages", "conversation", "mms"),
        HomeDomain.COMMUNICATIONS,
        featured = true,
        capabilityId = "phone_core_android"
    ) { context.startActivity(Intent(context, SmsComposeActivity::class.java)) },
    HomeTool(
        "Vérifier un numéro",
        "Pays, attribution officielle et signaux de risque disponibles",
        Icons.Default.Search,
        setOf("numero", "numéro", "recherche", "chercher", "identifier", "identification"),
        HomeDomain.COMMUNICATIONS,
        featured = true
    ) { context.startActivity(Intent(context, SentinelDialerActivity::class.java)) },
    HomeTool(
        "Assistant Sentinel",
        "Comprendre un risque avec une réponse locale explicitement non probante",
        Icons.Default.AutoAwesome,
        setOf("assistant", "ia", "risque", "alerte", "conseil"),
        HomeDomain.ANALYSIS,
        featured = true
    ) { navController.navigate(Screen.Assistant.route) },
    HomeTool(
        "Bloquer un appel",
        "Gérer les règles locales de blocage et d’identification",
        Icons.Default.Block,
        setOf("bloquer", "blocage", "spam", "indesirable", "indésirable", "filtrage"),
        HomeDomain.PROTECTION,
        featured = true
    ) { navController.navigate(Screen.CallBlocking.route) },
    HomeTool(
        "Protection mobile",
        "Ce qui est protégé, à configurer ou encore non mesuré",
        Icons.Default.Shield,
        setOf("protection", "securite", "sécurité", "telephone", "téléphone"),
        HomeDomain.PROTECTION,
        featured = true
    ) { navController.navigate(Screen.PhoneSecurity.route) },
    HomeTool(
        "Communications",
        "Appels, SMS/MMS et canaux de communication",
        Icons.Default.Forum,
        setOf("communication", "appels", "sms", "mms"),
        HomeDomain.COMMUNICATIONS
    ) { navController.navigate(Screen.CommunicationsHub.route) },
    HomeTool(
        "Scanner un SMS ou un lien",
        "Analyser localement un message, une URL ou un contenu suspect",
        Icons.Default.MarkChatUnread,
        setOf("sms", "lien", "url", "phishing", "arnaque", "message"),
        HomeDomain.ANALYSIS
    ) { navController.navigate(Screen.SmsScanner.route) },
    HomeTool(
        "Réseau & appareils proches",
        "Wi-Fi, Bluetooth et visibilité locale",
        Icons.Default.Wifi,
        setOf("wifi", "wi-fi", "bluetooth", "reseau", "réseau", "scanner"),
        HomeDomain.NETWORK
    ) { navController.navigate(Screen.NetworkSurveillance.route) },
    HomeTool(
        "Diagnostic de l’appareil",
        "Stockage, correctifs et signaux de sécurité observables",
        Icons.Default.Security,
        setOf("diagnostic", "appareil", "stockage", "correctif", "systeme", "système"),
        HomeDomain.PROTECTION
    ) { navController.navigate(Screen.SystemDoctor.route) },
    HomeTool(
        "Permissions des applications",
        "Repérer les accès sensibles accordés aux applications",
        Icons.Default.Apps,
        setOf("permission", "permissions", "applications", "apps", "acces", "accès"),
        HomeDomain.PROTECTION
    ) { navController.navigate(Screen.AppPermissionAnalyzer.route) },
    HomeTool(
        "Exposition numérique",
        "Contrôler les signaux d’exposition disponibles",
        Icons.Default.Key,
        setOf("exposition", "numerique", "numérique", "fuite", "identite", "identité"),
        HomeDomain.ANALYSIS
    ) { navController.navigate(Screen.DigitalExposure.route) },
    HomeTool(
        "Défense collective",
        "Analyser, signaler et surveiller des indicateurs techniques",
        Icons.Default.Groups,
        setOf("defense", "défense", "collective", "phishing", "malware", "url", "email", "sha256"),
        HomeDomain.ANALYSIS,
        featured = true,
        capabilityId = "collective_defense_android"
    ) { navController.navigate(Screen.CollectiveDefense.route) },
    HomeTool(
        "VPN",
        "Protection réseau défensive et configuration du tunnel",
        Icons.Default.VpnLock,
        setOf("vpn", "wireguard", "tunnel", "reseau", "réseau"),
        HomeDomain.NETWORK,
        capabilityId = "sentinel_vpn_service"
    ) { navController.navigate(Screen.Vpn.route) },
    HomeTool(
        "Maison & objets connectés",
        "Montres, caméras, éclairage et appareils compatibles",
        Icons.Default.HomeWork,
        setOf("maison", "montre", "camera", "caméra", "iot", "domotique", "objet"),
        HomeDomain.NETWORK
    ) { navController.navigate(Screen.SmartHome.route) },
    HomeTool(
        "Analyser un e-mail",
        "Examiner localement les en-têtes, domaines et liens",
        Icons.Default.Email,
        setOf("email", "e-mail", "mail", "entete", "en-tête", "phishing"),
        HomeDomain.ANALYSIS
    ) { navController.navigate(Screen.EmailSecurity.route) },
    HomeTool(
        "Historique des appels filtrés",
        "Comprendre les décisions de filtrage prises par Sentinel",
        Icons.Default.History,
        setOf("historique", "appel", "filtre", "journal"),
        HomeDomain.COMMUNICATIONS
    ) { navController.navigate(Screen.CallFilterHistory.route) },
    HomeTool(
        "Audit sécurité",
        "Lancer les contrôles techniques disponibles",
        Icons.Default.Security,
        setOf("audit", "securite", "sécurité", "controle", "contrôle"),
        HomeDomain.PROTECTION
    ) { navController.navigate(Screen.SecurityAudit.route) },
    HomeTool(
        "Veille & événements",
        "Consulter les événements OSINT disponibles",
        Icons.Default.Public,
        setOf("osint", "veille", "evenement", "événement", "monde", "geopolitique", "géopolitique"),
        HomeDomain.WATCH
    ) { navController.navigate(Screen.OsintFeed.route) }
    ,
    HomeTool(
        "Carte des menaces",
        "Visualiser uniquement les événements géographiques réellement disponibles",
        Icons.Default.Public,
        setOf("carte", "menaces", "monde", "géographie", "geographie"),
        HomeDomain.WATCH
    ) { navController.navigate(Screen.ThreatMap.route) },
    HomeTool(
        "Quarantaine",
        "Consulter les mécanismes de confinement effectivement observés",
        Icons.Default.Security,
        setOf("quarantaine", "confinement", "mms", "isolement"),
        HomeDomain.PROTECTION
    ) { navController.navigate(Screen.Quarantine.route) },
    HomeTool(
        "Détail d’une menace",
        "Ouvrir un dossier de preuve lorsqu’un contrôle en fournit un",
        Icons.Default.WarningAmber,
        setOf("menace", "détail", "preuve", "incident"),
        HomeDomain.PROTECTION
    ) { navController.navigate(Screen.ThreatDetail.route) }
)

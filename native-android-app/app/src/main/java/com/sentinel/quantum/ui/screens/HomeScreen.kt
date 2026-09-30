package com.sentinel.quantum.ui.screens

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.sentinel.quantum.PhoneCoreActivationActivity
import com.sentinel.quantum.PhoneCoreRuntimeFacts
import com.sentinel.quantum.PhoneCoreSetupWizardStore
import com.sentinel.quantum.SentinelDialerActivity
import com.sentinel.quantum.SmsComposeActivity
import com.sentinel.quantum.VoiceStudioActivity
import com.sentinel.quantum.navigation.Screen
import com.sentinel.quantum.ui.design.SentinelD1
import com.sentinel.quantum.ui.design.SentinelHero
import com.sentinel.quantum.ui.design.SentinelSectionHeader
import com.sentinel.quantum.ui.design.SentinelTopBar
import java.util.Locale

private data class HomeTool(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val keywords: Set<String>,
    val featured: Boolean = false,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
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

    val tools = listOf(
        HomeTool(
            "Téléphone",
            "Appeler, retrouver un contact ou consulter les appels récents",
            Icons.Default.Phone,
            setOf("appel", "appeler", "telephone", "téléphone", "contact", "contacts", "recent", "récents"),
            featured = true
        ) { context.startActivity(Intent(context, SentinelDialerActivity::class.java)) },
        HomeTool(
            "Messages",
            "Lire les conversations ou écrire un SMS",
            Icons.Default.Sms,
            setOf("sms", "message", "messages", "conversation", "mms"),
            featured = true
        ) { context.startActivity(Intent(context, SmsComposeActivity::class.java)) },
        HomeTool(
            "Vérifier un numéro",
            "Pays, attribution officielle et signaux de risque disponibles",
            Icons.Default.Search,
            setOf("numero", "numéro", "recherche", "chercher", "identifier", "identification"),
            featured = true
        ) { navController.navigate(Screen.Search.route) },
        HomeTool(
            "Bloquer un appel",
            "Gérer les règles locales de blocage et d’identification",
            Icons.Default.Block,
            setOf("bloquer", "blocage", "spam", "indesirable", "indésirable", "filtrage"),
            featured = true
        ) { navController.navigate(Screen.CallBlocking.route) },
        HomeTool(
            "Studio vocal",
            "Tester des effets de voix localement · add-on live VoIP à venir",
            Icons.Default.GraphicEq,
            setOf("voix", "vocal", "modulateur", "changer voix", "effet", "audio"),
            featured = true
        ) { context.startActivity(Intent(context, VoiceStudioActivity::class.java)) },
        HomeTool(
            "Protection mobile",
            "Ce qui est protégé, à configurer ou encore non mesuré",
            Icons.Default.Shield,
            setOf("protection", "securite", "sécurité", "telephone", "téléphone")
        ) { navController.navigate(Screen.PhoneSecurity.route) },
        HomeTool(
            "Communications",
            "Appels, SMS/MMS et canaux de communication",
            Icons.Default.Forum,
            setOf("communication", "appels", "sms", "mms")
        ) { navController.navigate(Screen.CommunicationsHub.route) },
        HomeTool(
            "Scanner un SMS ou un lien",
            "Analyser localement un message, une URL ou un contenu suspect",
            Icons.Default.MarkChatUnread,
            setOf("sms", "lien", "url", "phishing", "arnaque", "message")
        ) { navController.navigate(Screen.SmsScanner.route) },
        HomeTool(
            "Réseau & appareils proches",
            "Wi-Fi, Bluetooth et visibilité locale",
            Icons.Default.Wifi,
            setOf("wifi", "wi-fi", "bluetooth", "reseau", "réseau", "scanner")
        ) { navController.navigate(Screen.NetworkSurveillance.route) },
        HomeTool(
            "Diagnostic de l’appareil",
            "Stockage, correctifs et signaux de sécurité observables",
            Icons.Default.Security,
            setOf("diagnostic", "appareil", "stockage", "correctif", "systeme", "système")
        ) { navController.navigate(Screen.SystemDoctor.route) },
        HomeTool(
            "Permissions des applications",
            "Repérer les accès sensibles accordés aux applications",
            Icons.Default.Apps,
            setOf("permission", "permissions", "applications", "apps", "acces", "accès")
        ) { navController.navigate(Screen.AppPermissionAnalyzer.route) },
        HomeTool(
            "Exposition numérique",
            "Contrôler les signaux d’exposition disponibles",
            Icons.Default.Key,
            setOf("exposition", "numerique", "numérique", "fuite", "identite", "identité")
        ) { navController.navigate(Screen.DigitalExposure.route) },
        HomeTool(
            "VPN",
            "Protection réseau défensive et configuration du tunnel",
            Icons.Default.VpnLock,
            setOf("vpn", "wireguard", "tunnel", "reseau", "réseau")
        ) { navController.navigate(Screen.Vpn.route) },
        HomeTool(
            "Maison & objets connectés",
            "Montres, caméras, éclairage et appareils compatibles",
            Icons.Default.HomeWork,
            setOf("maison", "montre", "camera", "caméra", "iot", "domotique", "objet")
        ) { navController.navigate(Screen.SmartHome.route) },
        HomeTool(
            "Analyser un e-mail",
            "Examiner localement les en-têtes, domaines et liens",
            Icons.Default.Email,
            setOf("email", "e-mail", "mail", "entete", "en-tête", "phishing")
        ) { navController.navigate(Screen.EmailSecurity.route) },
        HomeTool(
            "Historique des appels filtrés",
            "Comprendre les décisions de filtrage prises par Sentinel",
            Icons.Default.History,
            setOf("historique", "appel", "filtre", "journal")
        ) { navController.navigate(Screen.CallFilterHistory.route) },
        HomeTool(
            "Audit sécurité",
            "Lancer les contrôles techniques disponibles",
            Icons.Default.Security,
            setOf("audit", "securite", "sécurité", "controle", "contrôle")
        ) { navController.navigate(Screen.SecurityAudit.route) },
        HomeTool(
            "Veille & événements",
            "Consulter les événements OSINT disponibles",
            Icons.Default.Public,
            setOf("osint", "veille", "evenement", "événement", "monde", "geopolitique", "géopolitique")
        ) { navController.navigate(Screen.OsintFeed.route) }
    )

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
                title = "SENTINEL",
                subtitle = "Protection simple · état réel"
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
            SentinelHero(
                eyebrow = if (phoneCoreReady) "Prérequis Android prêts" else "Configuration en cours",
                title = "Que voulez-vous faire ?",
                body = "Un accès direct aux actions courantes et aux protections dont vous avez besoin.",
                badges = listOf(
                    (if (phoneCoreReady) "Prérequis prêts" else "$readyCount/8 contrôles prêts") to
                        (if (phoneCoreReady) SentinelD1.Success else SentinelD1.Cyan),
                    "Traitement local" to SentinelD1.Cyan
                )
            )

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
                subtitle = if (normalizedQuery.isBlank()) {
                    "Les actions courantes sans parcourir les menus."
                } else {
                    "${matchingTools.size} fonction(s) trouvée(s) pour « ${toolQuery.trim()} »."
                }
            )

            if (matchingTools.isEmpty()) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = SentinelD1.Card)
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Aucun résultat", fontWeight = FontWeight.Bold)
                        Text(
                            "Essayez appel, SMS, Wi-Fi, VPN, e-mail, blocage ou permissions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                matchingTools.forEach { tool ->
                    DashboardCard(tool.title, tool.subtitle, tool.icon, tool.onClick)
                }
            }

            if (normalizedQuery.isBlank()) {
                TextButton(
                    onClick = { showAllTools = !showAllTools },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (showAllTools) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (showAllTools) "Masquer les outils avancés" else "Afficher tous les outils")
                }

                SentinelSectionHeader(
                    title = if (phoneCoreReady) "Vérifier votre protection" else "Continuer l’activation",
                    subtitle = if (phoneCoreReady) {
                        "Les prérequis Android sont observés comme prêts ; les tests physiques sont distincts."
                    } else {
                        "$readyCount/8 contrôles Android prêts. Une étape guidée à la fois."
                    }
                )
                ElevatedCard(
                    onClick = {
                        context.startActivity(Intent(context, PhoneCoreActivationActivity::class.java))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SentinelD1.Border, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.elevatedCardColors(containerColor = SentinelD1.Card)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (phoneCoreReady) Icons.Default.Verified else Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (phoneCoreReady) SentinelD1.Success else SentinelD1.Cyan
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (phoneCoreReady) "Ouvrir les tests Phone Core" else "Terminer la configuration",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (phoneCoreReady) "Valider les fonctionnalités sur cet appareil."
                                    else "Reprendre directement à la prochaine étape manquante.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                        if (!phoneCoreReady) {
                            LinearProgressIndicator(
                                progress = { readyCount / 8f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SentinelD1.Border, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = SentinelD1.Card)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SentinelD1.Panel
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp).size(28.dp),
                    tint = SentinelD1.Cyan
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

package com.sentinel.quantum.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UnifiedIntelligenceScreen(
    blockedCount: Int = 4,
    whitelistedCount: Int = 1,
    localReportsCount: Int = 4,
    countriesCoveredCount: Int = 1,
    onSaveLocalReport: (number: String, reason: String, operator: String, note: String, wasBlocked: Boolean) -> Unit = { _, _, _, _, _ -> },
    onClearLocalData: () -> Unit = {},
    onSearchArcepPrefix: (String) -> Unit = {}
) {
    var rawNumberInput by remember { mutableStateOf("") }
    var selectedReason by remember { mutableStateOf("Hameçonnage") }
    var expandedReason by remember { mutableStateOf(false) }
    var operatorInput by remember { mutableStateOf("Inconnu") }
    var wasBlockedChecked by remember { mutableStateOf(false) }
    var noteInput by remember { mutableStateOf("") }

    var arcepPrefixInput by remember { mutableStateOf("042411") }

    val reasonsList = listOf("Hameçonnage", "Fraude financière", "Démarchage abusif", "Autre")
    val darkCardBg = Color(0xFF131B2E)
    val cyanAccent = Color(0xFF22D3EE)
    val textMuted = Color(0xFF94A3B8)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // En-tête / Identité visuelle web
        item {
            Text(
                text = "SENTINEL QUANTUM",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Parité web / mobile • Pare-feu explicite & Multi-listes",
                color = textMuted,
                fontSize = 12.sp
            )
        }

        // Section: Gestion multi-listes (Règle d'évaluation)
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = darkCardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Gestion multi-listes",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Comme un pare-feu, les listes sont évaluées dans un ordre explicite. Les choix personnels priment toujours sur les listes publiques.",
                        color = textMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val rules = listOf(
                        "1. Liste blanche personnelle — priorité absolue.",
                        "2. Liste de blocage personnelle — décision locale de l'utilisateur.",
                        "3. Liste communautaire signée — non connectée tant que modération, recours et seuils ne sont pas déployés.",
                        "4. Listes de référence (ARCEP, numéros spéciaux) — information uniquement, jamais preuve de fraude."
                    )
                    rules.forEach { rule ->
                        Text(
                            text = rule,
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Section: Explorer les attributions ARCEP par préfixe
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = darkCardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Explorer les attributions ARCEP par préfixe",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Entrez les 4 à 10 premiers chiffres d'un numéro français. Cette recherche réglementaire est séparée de vos listes personnelles.",
                        color = textMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Préfixe national", color = textMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = arcepPrefixInput,
                            onValueChange = { arcepPrefixInput = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = cyanAccent,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Button(
                            onClick = { onSearchArcepPrefix(arcepPrefixInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = cyanAccent),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text("Rechercher les tranches", color = Color(0xFF090D16), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Section: Créer un signalement local
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = darkCardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Créer un signalement local",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Numéro à cibler
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Numéro cible", color = textMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = rawNumberInput,
                                onValueChange = { rawNumberInput = it },
                                placeholder = { Text("06...", color = textMuted) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = cyanAccent,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }

                        // Opérateur déclaré
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Opérateur déclaré", color = textMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = operatorInput,
                                onValueChange = { operatorInput = it },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = cyanAccent,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Raison", color = textMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))

                    Box {
                        OutlinedButton(
                            onClick = { expandedReason = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text(selectedReason, modifier = Modifier.weight(1f))
                            Text("▼", fontSize = 10.sp, color = textMuted)
                        }
                        DropdownMenu(
                            expanded = expandedReason,
                            onDismissRequest = { expandedReason = false }
                        ) {
                            reasonsList.forEach { reason ->
                                DropdownMenuItem(
                                    text = { Text(reason) },
                                    onClick = {
                                        selectedReason = reason
                                        expandedReason = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = wasBlockedChecked,
                            onCheckedChange = { wasBlockedChecked = it },
                            colors = CheckboxDefaults.colors(checkedColor = cyanAccent)
                        )
                        Text(
                            text = "Ce numéro était déjà bloqué sur mon appareil",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Note factuelle (sans donnée sensible)", color = textMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = cyanAccent,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (rawNumberInput.isNotBlank()) {
                                onSaveLocalReport(rawNumberInput, selectedReason, operatorInput, noteInput, wasBlockedChecked)
                                rawNumberInput = ""
                                noteInput = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = cyanAccent)
                    ) {
                        Text(
                            "Enregistrer localement",
                            color = Color(0xFF090D16),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Section: Statistiques locales
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = darkCardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Statistiques locales",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatMiniBox("Signalements locaux", localReportsCount.toString(), Modifier.weight(1f))
                        StatMiniBox("Liste blanche", whitelistedCount.toString(), Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatMiniBox("Liste de blocage", blockedCount.toString(), Modifier.weight(1f))
                        StatMiniBox("Pays couverts", countriesCoveredCount.toString(), Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Ces chiffres concernent uniquement ce navigateur/appareil ; ils ne représentent pas la communauté Sentinel.",
                        color = textMuted,
                        fontSize = 10.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onClearLocalData,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Effacer mes données locales")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, color = Color(0xFF94A3B8), fontSize = 10.sp)
        }
    }
}

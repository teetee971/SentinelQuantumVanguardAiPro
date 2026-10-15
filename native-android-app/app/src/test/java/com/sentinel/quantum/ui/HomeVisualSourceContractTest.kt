package com.sentinel.quantum.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeVisualSourceContractTest {
    private val source = File(
        "src/main/java/com/sentinel/quantum/ui/screens/HomeScreen.kt"
    ).readText()

    @Test
    fun homeInstrumentationKeepsComposeClockAutoAdvanceEnabled() {
        val instrumentationSource = File(
            "src/androidTest/java/com/sentinel/quantum/ui/HomeVisualContractTest.kt"
        ).readText()
        assertFalse(
            "Home instrumentation must not disable Compose clock auto-advance",
            containsDisabledComposeClock(instrumentationSource)
        )
    }

    @Test
    fun clockGuardRejectsParenthesizedAndCommentSeparatedFalse() {
        listOf(
            "composeRule.mainClock.autoAdvance = false",
            "composeRule.mainClock.autoAdvance = (false)",
            "composeRule.mainClock.autoAdvance = ((false))",
            "composeRule.mainClock\n  . autoAdvance = (\n false \n)",
            "composeRule.mainClock /* gap */ . autoAdvance = ( /* gap */ false )",
            "composeRule.mainClock.autoAdvance = /* outer /* inner */ gap */ (false)"
        ).forEach { assignment ->
            assertTrue("Must reject: $assignment", containsDisabledComposeClock(assignment))
        }
    }

    @Test
    fun clockGuardIgnoresCommentsAndStringLiterals() {
        listOf(
            "// composeRule.mainClock.autoAdvance = false",
            "/* composeRule.mainClock.autoAdvance = (false) */",
            "/* outer /* composeRule.mainClock.autoAdvance = false */ end */",
            "val example = \"composeRule.mainClock.autoAdvance = false\"",
            "val example = \"\"\"composeRule.mainClock.autoAdvance = (false)\"\"\""
        ).forEach { nonExecutableText ->
            assertFalse("Must ignore: $nonExecutableText", containsDisabledComposeClock(nonExecutableText))
        }
    }

    private fun containsDisabledComposeClock(text: String): Boolean {
        var code = maskCommentsAndLiterals(text)
        val parenthesizedFalse = Regex("""\(\s*false\s*\)""")
        while (parenthesizedFalse.containsMatchIn(code)) {
            code = code.replace(parenthesizedFalse, "false")
        }
        return Regex("""\bmainClock\s*\.\s*autoAdvance\s*=\s*false\b""")
            .containsMatchIn(code)
    }

    private fun maskCommentsAndLiterals(text: String): String {
        val code = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("//", index) -> {
                    index = text.indexOf('\n', index).let { if (it < 0) text.length else it }
                    code.append(' ')
                }
                text.startsWith("/*", index) -> {
                    var depth = 1
                    index += 2
                    while (index < text.length && depth > 0) {
                        when {
                            text.startsWith("/*", index) -> { depth++; index += 2 }
                            text.startsWith("*/", index) -> { depth--; index += 2 }
                            else -> index++
                        }
                    }
                    code.append(' ')
                }
                text.startsWith("\"\"\"", index) -> {
                    val end = text.indexOf("\"\"\"", index + 3)
                    index = if (end < 0) text.length else end + 3
                    code.append(' ')
                }
                text[index] == '"' || text[index] == '\'' -> {
                    val quote = text[index++]
                    while (index < text.length) {
                        when {
                            text[index] == '\\' -> index = (index + 2).coerceAtMost(text.length)
                            text[index] == quote -> { index++; break }
                            else -> index++
                        }
                    }
                    code.append(' ')
                }
                else -> code.append(text[index++])
            }
        }
        return code.toString()
    }

    @Test
    fun primaryActivationActionPrecedesTheTallHeroAndModuleList() {
        val action = source.indexOf("SentinelActionTile(")
        val hero = source.indexOf("SentinelHeroVisual(")
        val modules = source.indexOf("homeState.modules.forEach")
        assertTrue("Activation must be the first content in the hero/priority region",
            action >= 0 && action < hero && hero < modules)
        assertEquals("Only one primary activation action", 1,
            Regex("testTag\\(\"home_primary_action\"\\)").findAll(source).count())
    }

    @Test
    fun primaryActionRetainsButtonSemanticsAndActivationCallback() {
        val action = source.substringAfter("SentinelActionTile(").substringBefore("\n            )")
        assertTrue(action.contains("testTag(\"home_primary_action\")"))
        assertTrue(action.contains("context.startActivity(Intent(context, PhoneCoreActivationActivity::class.java))"))
        val tile = File("src/main/java/com/sentinel/quantum/ui/design/SentinelGlassComponents.kt")
            .readText().substringAfter("fun SentinelActionTile(").substringBefore("\n@Composable")
        assertTrue(tile.contains(".clickable(role = Role.Button, onClick = onClick)"))
        assertFalse(action.contains("clearAndSetSemantics"))
    }

    @Test
    fun activitySummaryIsExplicitlyUnavailableWithoutAnEventSource() {
        val presentation = File("src/main/java/com/sentinel/quantum/ui/presentation/HomeSecurityPresentation.kt")
            .readText()
        assertTrue("Activity needs an explicit presentation field",
            presentation.contains("val activitySummary: String"))
        assertTrue(presentation.contains("activitySummary = \"Activité récente indisponible : aucun historique d’événements fourni à l’accueil.\""))
        assertTrue(source.contains("testTag(\"home_activity_summary\")"))
        assertTrue(source.contains("Text(homeState.activitySummary"))
    }

    @Test
    fun homeReadsRoleAvailabilityAndDoesNotOfferActivationAsTheRemedyForUnavailableRoles() {
        listOf("dialerRoleAvailable", "callScreeningRoleAvailable", "smsRoleAvailable").forEach {
            assertTrue("Home must consume $it", source.contains("phoneCoreFacts.$it"))
        }
        assertTrue(source.contains("requiredRolesAvailable ="))
        val action = source.substringAfter("SentinelActionTile(").substringBefore("\n            )")
        assertTrue(action.contains("SentinelStatus.LIMITED"))
        assertTrue(action.contains("Examiner Phone Core"))
        assertTrue(action.contains("phoneCoreModule.detail"))
    }

    @Test
    fun homeWiresPremiumCommandCenterAndStableSemantics() {
        listOf(
            "HomeSecurityPresentation",
            "SentinelHeroVisual",
            "home_security_hero",
            "home_module_phone_core",
            "home_module_vpn",
            "home_module_network",
            "home_module_collective_defense",
            "home_primary_action"
        ).forEach { marker ->
            assertTrue("HomeScreen missing $marker", source.contains(marker))
        }
    }

    @Test
    fun homeNoLongerPromotesSoftwarePrerequisitesAsGreenProtection() {
        assertTrue(
            "Legacy Prérequis prêts hero must be removed",
            !source.contains("\"Prérequis prêts\"")
        )
    }
}

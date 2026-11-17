package com.karelisio.allo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.karelisio.allo.AlloApp
import com.karelisio.allo.MainViewModel
import com.karelisio.allo.ui.components.LocalCaller
import com.karelisio.allo.ui.components.rememberCaller
import com.karelisio.allo.ui.dialer.DialerScreen
import com.karelisio.allo.ui.log.VoicemailScreen
import com.karelisio.allo.ui.onboarding.OnboardingScreen
import com.karelisio.allo.ui.settings.AboutScreen
import com.karelisio.allo.ui.settings.BlockingScreen
import com.karelisio.allo.ui.settings.PermissionsScreen
import com.karelisio.allo.ui.settings.QuickRepliesScreen
import com.karelisio.allo.ui.settings.ReliabilityScreen
import com.karelisio.allo.ui.settings.SettingsScreen
import com.karelisio.allo.ui.theme.AlloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Chaque écran se compose sans crash et affiche son titre (clair et sombre). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = AlloApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ScreensSmokeTest {
    @get:Rule val rule = createComposeRule()
    private val vm by lazy { MainViewModel(ApplicationProvider.getApplicationContext()) }

    private fun render(expected: String, dark: Boolean = false, content: @Composable (NavHostController) -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AlloTheme(dark = dark) {
                CompositionLocalProvider(LocalCaller provides rememberCaller()) { content(rememberNavController()) }
            }
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onAllNodesWithText(expected, substring = true).onFirst().assertExists()
    }

    @Test fun settings() = render("Réglages") { SettingsScreen(vm, it) }
    @Test fun settingsDark() = render("Réglages", dark = true) { SettingsScreen(vm, it) }
    @Test fun blocking() = render("Préfixes de démarchage") { BlockingScreen(vm, it) }
    @Test fun permissions() = render("Appli Téléphone par défaut") { PermissionsScreen(vm, it) }
    @Test fun reliability() = render("Test d'appel entrant") { ReliabilityScreen(vm, it) }
    @Test fun replies() = render("Réponses rapides") { QuickRepliesScreen(it) }
    @Test fun about() = render("Version") { AboutScreen(it) }
    @Test fun voicemail() = render("Messagerie vocale") { VoicemailScreen(vm, it) }
    @Test fun dialer() = render("Créer un contact") { DialerScreen(vm, it, "0612") }
    @Test fun stats() = render("Statistiques") { com.karelisio.allo.ui.settings.StatsScreen(vm, it) }
    @Test fun groups() = render("Sonnerie par groupe") { com.karelisio.allo.ui.settings.GroupRingtoneScreen(it) }
    @Test fun diagnostics() = render("Diagnostic") { com.karelisio.allo.ui.settings.DiagnosticsScreen(it) }
    @Test fun onboarding() = render("Bienvenue dans Allô") { OnboardingScreen(onFinished = {}, onPermissionsChanged = {}) }
    @Test fun onboardingDark() = render("Bienvenue dans Allô", dark = true) { OnboardingScreen(onFinished = {}, onPermissionsChanged = {}) }
}

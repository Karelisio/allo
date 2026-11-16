package com.karelisio.allo.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.karelisio.allo.AlloApp
import com.karelisio.allo.MainViewModel
import com.karelisio.allo.ui.theme.AlloTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.rememberNavController
import com.karelisio.allo.core.CallEntry
import com.karelisio.allo.core.CallGroup
import com.karelisio.allo.core.CallKind
import com.karelisio.allo.ui.components.LocalCaller
import com.karelisio.allo.ui.components.rememberCaller
import com.karelisio.allo.ui.log.CallRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** L'accueil (après onboarding) se compose sans crash, onglets compris. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = AlloApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class HomeSmokeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun homeRendersAndTabsSwitch() {
        val vm = MainViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent {
            AlloTheme { AlloRoot(vm, onboardingDone = true, onOnboardingFinished = {}, launch = null, onLaunchConsumed = {}, isDefaultDialer = false) }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Favoris").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Contacts").performClick()
        rule.waitForIdle()
    }

    /** Lignes du journal avec de vraies données (régression du crash 0.1.0). */
    @Test fun callRowsRender() {
        val now = System.currentTimeMillis()
        val groups = listOf(
            CallGroup(listOf(CallEntry(1, "+33612345678", "Camille Martin", CallKind.MISSED, now, 0, simSlot = 0, isNew = true),
                CallEntry(2, "+33612345678", "Camille Martin", CallKind.INCOMING, now - 60_000, 125))),
            CallGroup(listOf(CallEntry(3, "", null, CallKind.BLOCKED, now - 3_600_000, 0))),
            CallGroup(listOf(CallEntry(4, "0162123456", null, CallKind.OUTGOING, now - 7_200_000, 42, simSlot = 1))),
        )
        rule.setContent {
            AlloTheme {
                CompositionLocalProvider(LocalCaller provides rememberCaller()) {
                    val nav = rememberNavController()
                    Column { groups.forEach { CallRow(it, nav) } }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Camille Martin").assertExists()
        rule.onNodeWithText("Numéro masqué").assertExists()
    }
}

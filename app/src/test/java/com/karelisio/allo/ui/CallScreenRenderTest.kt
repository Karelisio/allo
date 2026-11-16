package com.karelisio.allo.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.karelisio.allo.telecom.AudioOutput
import com.karelisio.allo.telecom.AudioRoute
import com.karelisio.allo.telecom.AudioUi
import com.karelisio.allo.telecom.CallInfo
import com.karelisio.allo.telecom.CallPhase
import com.karelisio.allo.ui.call.CallActions
import com.karelisio.allo.ui.call.CallScreen
import com.karelisio.allo.ui.theme.AlloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tests de rendu des écrans d'appel (entrant, en cours, double appel). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class CallScreenRenderTest {
    @get:Rule val rule = createComposeRule()

    private fun call(phase: CallPhase, name: String? = "Camille Martin", number: String = "+33612345678", label: String? = null, id: String = "1") = CallInfo(
        id = id, phase = phase, number = number, name = name, photoUri = null, lookupKey = null, simLabel = "SIM 1 · Orange",
        connectTimeMillis = if (phase == CallPhase.ACTIVE) 1_000_000L else 0L, isConference = false, conferenceSize = 0,
        canHold = true, canMerge = true, canSwap = false, isIncoming = true, label = label, silenced = false,
    )

    @Test fun incomingShowsCallerAndActions() {
        var answered = 0
        var declined = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AlloTheme {
                CallScreen(listOf(call(CallPhase.RINGING)), AudioUi(), CallActions(onAnswer = { answered++ }, onDecline = { declined++ }))
            }
        }
        rule.mainClock.advanceTimeBy(3_000)
        rule.onNodeWithTag("call_name").assertIsDisplayed()
        rule.onNodeWithText("Camille Martin").assertIsDisplayed()
        rule.onNodeWithText("06 12 34 56 78").assertIsDisplayed()
        rule.onNodeWithText("Appel entrant · SIM 1 · Orange").assertIsDisplayed()
        rule.onNodeWithText("Message").assertIsDisplayed()
        rule.onNodeWithText("Rappel 10 min").assertIsDisplayed()
        rule.onNodeWithTag("btn_answer").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("btn_decline").performClick()
        rule.mainClock.advanceTimeBy(500)
        assertEquals(1, answered)
        assertEquals(1, declined)
    }

    @Test fun incomingTelemarketingLabel() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AlloTheme { CallScreen(listOf(call(CallPhase.RINGING, name = null, number = "+33162123456", label = "Démarchage probable")), AudioUi(), CallActions()) }
        }
        rule.mainClock.advanceTimeBy(3_000)
        rule.onNodeWithText("01 62 12 34 56").assertIsDisplayed()
        rule.onNodeWithText("Démarchage probable").assertIsDisplayed()
    }

    @Test fun ongoingShowsTimerAndControls() {
        var hung = 0
        var speaker = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AlloTheme {
                CallScreen(listOf(call(CallPhase.ACTIVE)), AudioUi(), CallActions(onHangup = { hung++ }, onToggleSpeaker = { speaker++ }),
                    now = { 1_000_000L + 75_000L })
            }
        }
        rule.mainClock.advanceTimeBy(3_000)
        rule.onNodeWithText("Appel en cours · SIM 1 · Orange").assertIsDisplayed()
        rule.onNodeWithTag("call_timer").assertIsDisplayed()
        listOf("Muet", "Clavier", "Haut-parleur", "Attente", "Ajouter").forEach { rule.onNodeWithContentDescription(it).assertIsDisplayed() }
        rule.onNodeWithContentDescription("Haut-parleur").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("btn_hangup").performClick()
        rule.mainClock.advanceTimeBy(500)
        assertEquals(1, speaker)
        assertEquals(1, hung)
    }

    @Test fun twoCallsShowSwapAndMerge() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AlloTheme {
                CallScreen(listOf(call(CallPhase.ACTIVE), call(CallPhase.HOLDING, name = "Alex", number = "+33700000000", id = "2")), AudioUi(), CallActions())
            }
        }
        rule.mainClock.advanceTimeBy(3_000)
        rule.onNodeWithText("Alex").assertIsDisplayed()
        rule.onNodeWithText("En attente").assertIsDisplayed()
        rule.onNodeWithText("Basculer").assertIsDisplayed()
        rule.onNodeWithContentDescription("Fusionner").assertIsDisplayed()
    }

    @Test fun bluetoothOutputShowsDeviceName() {
        val bt = AudioOutput(AudioRoute.CAR, "Peugeot 208", "AA:BB")
        val audio = AudioUi(current = bt, outputs = listOf(AudioOutput(AudioRoute.EARPIECE, "Écouteur"), AudioOutput(AudioRoute.SPEAKER, "Haut-parleur"), bt))
        rule.mainClock.autoAdvance = false
        rule.mainClock.autoAdvance = false
        rule.setContent { AlloTheme { CallScreen(listOf(call(CallPhase.ACTIVE)), audio, CallActions()) } }
        rule.mainClock.advanceTimeBy(3_000)
        rule.onNodeWithContentDescription("Peugeot 208").assertIsDisplayed()
    }
}

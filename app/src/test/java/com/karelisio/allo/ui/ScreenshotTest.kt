package com.karelisio.allo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.karelisio.allo.telecom.AudioUi
import com.karelisio.allo.telecom.CallInfo
import com.karelisio.allo.telecom.CallPhase
import com.karelisio.allo.ui.call.CallActions
import com.karelisio.allo.ui.call.CallScreen
import com.karelisio.allo.ui.components.DialPad
import com.karelisio.allo.ui.components.EmptyIllustration
import com.karelisio.allo.ui.theme.AlloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Captures d'écran des écrans clés (clair et sombre), publiées comme artefact par la CI. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val dir = File("build/outputs/screenshots").apply { mkdirs() }

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { AlloTheme(dark = dark) { content() } }
        rule.mainClock.advanceTimeBy(2_000)
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun call(p: CallPhase, label: String? = null) = CallInfo("1", p, "+33612345678", "Camille Martin", null, null, "SIM 1 · Orange",
        if (p == CallPhase.ACTIVE) 1_000_000L else 0L, false, 0, true, true, false, true, label, false)

    @Test fun incomingDark() = shot("appel-entrant", dark = true) { CallScreen(listOf(call(CallPhase.RINGING, "Démarchage probable")), AudioUi(), CallActions()) }
    @Test fun ongoingDark() = shot("appel-en-cours", dark = true) { CallScreen(listOf(call(CallPhase.ACTIVE)), AudioUi(), CallActions(), now = { 1_083_000L }) }
    @Test fun twoCalls() = shot("double-appel", dark = true) {
        CallScreen(listOf(call(CallPhase.ACTIVE), call(CallPhase.HOLDING).copy(id = "2", name = "Alex Dupont", number = "+33700000000")), AudioUi(), CallActions(), now = { 1_083_000L })
    }
    @Test fun dialPadLight() = shot("clavier") {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp), contentAlignment = Alignment.BottomCenter) { DialPad({}) }
    }
    @Test fun themedIcon() = shot("icone-a-theme") {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF1E2A22)), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Row {
                androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(com.karelisio.allo.R.drawable.ic_launcher_monochrome), null,
                    Modifier.padding(8.dp).size(160.dp), tint = androidx.compose.ui.graphics.Color(0xFFB8F0C8))
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.karelisio.allo.R.drawable.ic_launcher_foreground), null,
                    Modifier.padding(8.dp).size(160.dp).background(androidx.compose.ui.graphics.Color(0xFF1E6B3C), androidx.compose.foundation.shape.CircleShape))
            }
        }
    }
    @Test fun emptyLog() = shot("journal-vide") {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            EmptyIllustration(Icons.Rounded.History, "Aucun appel pour l'instant", "Tes appels apparaîtront ici, regroupés par jour.")
        }
    }
}

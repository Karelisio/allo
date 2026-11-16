package com.karelisio.allo.ui.call

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.karelisio.allo.ui.components.AlloListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.karelisio.allo.Graph
import com.karelisio.allo.MainActivity
import com.karelisio.allo.notifications.ReminderReceiver
import com.karelisio.allo.telecom.AudioUi
import com.karelisio.allo.telecom.CallInfo
import com.karelisio.allo.telecom.CallManager
import com.karelisio.allo.telecom.CallPhase
import com.karelisio.allo.ui.theme.AlloTheme
import com.karelisio.allo.ui.theme.LocalReduceMotion
import com.karelisio.allo.util.hasPermission
import com.karelisio.allo.util.openSms
import com.karelisio.allo.util.toast
import kotlinx.coroutines.launch

/** Écran d'appel plein écran : visible sur l'écran de verrouillage, allume l'écran. */
class InCallActivity : ComponentActivity() {
    private val preview get() = intent?.getBooleanExtra(EXTRA_PREVIEW, false) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(0), navigationBarStyle = SystemBarStyle.dark(0))
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handle(intent)
        val isPreview = preview
        setContent {
            AlloTheme(dark = true) {
                if (isPreview) PreviewHost(onFinished = { finishAndRemoveTask() })
                else LiveHost(onFinished = { finishAndRemoveTask() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_ANSWER) CallManager.answer()
    }

    @Composable
    private fun LiveHost(onFinished: () -> Unit) {
        val calls by CallManager.state.collectAsState()
        val audio by CallManager.audio.collectAsState()
        val last by CallManager.lastEnded.collectAsState()
        val settings by Graph.settings.settings.collectAsState(initial = null)
        var replyFor by remember { mutableStateOf<CallInfo?>(null) }
        val shown = calls.ifEmpty { listOfNotNull(last) }
        EndableCallScreen(
            calls = shown,
            ended = calls.none { it.isLive },
            audio = audio,
            remindMinutes = settings?.callbackDelayMin ?: 10,
            actions = CallActions(
                onAnswer = { CallManager.answer() },
                onDecline = { CallManager.reject() },
                onMessage = { replyFor = CallManager.state.value.firstOrNull { it.phase == CallPhase.RINGING } },
                onRemind = { remind() },
                onHangup = { CallManager.hangup() },
                onToggleMute = { CallManager.toggleMute() },
                onToggleSpeaker = { CallManager.toggleSpeaker() },
                onSelectOutput = { CallManager.selectOutput(it) },
                onToggleHold = { CallManager.toggleHold() },
                onAddCall = {
                    startActivity(Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_ADD_CALL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                onSwap = { CallManager.swap() },
                onMerge = { CallManager.merge() },
                onDtmf = { CallManager.playDtmf(it) },
            ),
            onFinished = onFinished,
        )
        replyFor?.let { c -> QuickReplySheet(c, onDismiss = { replyFor = null }) }
    }

    private fun remind() {
        val c = CallManager.state.value.firstOrNull { it.phase == CallPhase.RINGING } ?: return
        Graph.scope.launch {
            val min = Graph.settings.current().callbackDelayMin
            ReminderReceiver.schedule(this@InCallActivity, c.number, c.name, min)
            runOnUiThread { toast("Rappel dans $min min") }
        }
        CallManager.reject(c.id)
    }

    @Composable
    private fun QuickReplySheet(call: CallInfo, onDismiss: () -> Unit) {
        val replies by Graph.db.misc().replies().collectAsState(initial = emptyList())
        var custom by remember { mutableStateOf("") }
        fun send(text: String) {
            sendSms(call.number, text)
            CallManager.reject(call.id)
            onDismiss()
        }
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Text("Répondre par SMS", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            replies.forEach { r ->
                AlloListItem(headlineContent = { Text(r.text) }, modifier = Modifier.clickable { send(r.text) })
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = custom, onValueChange = { custom = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Écrire un message…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (custom.isNotBlank()) send(custom) }),
                    trailingIcon = { IconButton(onClick = { if (custom.isNotBlank()) send(custom) }) { Icon(Icons.AutoMirrored.Rounded.Send, "Envoyer") } },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    private fun sendSms(number: String, text: String) {
        if (hasPermission(android.Manifest.permission.SEND_SMS)) {
            runCatching {
                val sms = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
                sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null)
                toast("Message envoyé")
            }.onFailure { openSms(number, text) }
        } else openSms(number, text)
    }

    /** Test d'appel entrant simulé (écran « Fiabilité »). */
    @Composable
    private fun PreviewHost(onFinished: () -> Unit) {
        var phase by remember { mutableStateOf(CallPhase.RINGING) }
        var connect by remember { mutableStateOf(0L) }
        val fake = CallInfo(
            id = "preview", phase = phase, number = "+33612345678", name = "Camille Martin", photoUri = null, lookupKey = null,
            simLabel = "SIM 1", connectTimeMillis = connect, isConference = false, conferenceSize = 0, canHold = true,
            canMerge = false, canSwap = false, isIncoming = true, label = null, silenced = false,
        )
        var audio by remember { mutableStateOf(AudioUi()) }
        EndableCallScreen(
            calls = listOf(fake),
            ended = phase == CallPhase.DISCONNECTED,
            audio = audio,
            remindMinutes = 10,
            actions = CallActions(
                onAnswer = { connect = System.currentTimeMillis(); phase = CallPhase.ACTIVE },
                onDecline = { phase = CallPhase.DISCONNECTED },
                onHangup = { phase = CallPhase.DISCONNECTED },
                onMessage = { toast("Aperçu : réponse SMS") },
                onRemind = { toast("Aperçu : rappel programmé") },
                onToggleMute = { audio = audio.copy(muted = !audio.muted) },
                onToggleSpeaker = {
                    audio = audio.copy(current = if (audio.speaker) audio.outputs[0] else audio.outputs[1])
                },
                onToggleHold = { phase = if (phase == CallPhase.HOLDING) CallPhase.ACTIVE else CallPhase.HOLDING },
            ),
            onFinished = onFinished,
        )
    }

    companion object {
        const val ACTION_ANSWER = "com.karelisio.allo.ANSWER"
        const val EXTRA_PREVIEW = "preview"
    }
}

/**
 * Enveloppe l'écran d'appel avec l'animation de fin : l'écran se replie en pilule
 * qui disparaît vers la caméra.
 */
@Composable
fun EndableCallScreen(
    calls: List<CallInfo>,
    ended: Boolean,
    audio: AudioUi,
    remindMinutes: Int,
    actions: CallActions,
    onFinished: () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    val p by animateFloatAsState(
        if (ended) 1f else 0f,
        tween(if (reduce) 150 else 720, delayMillis = if (ended) 350 else 0, easing = FastOutLinearInEasing),
        label = "end",
        finishedListener = { if (it == 1f) onFinished() },
    )
    LaunchedEffect(calls.isEmpty()) { if (calls.isEmpty()) onFinished() }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            clip = p > 0f
            shape = EndCallShape(p, density)
        }.background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        CallScreen(
            calls = calls, audio = audio, actions = actions, remindMinutes = remindMinutes,
            modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = (1f - p * 2.2f).coerceIn(0f, 1f) },
        )
    }
}

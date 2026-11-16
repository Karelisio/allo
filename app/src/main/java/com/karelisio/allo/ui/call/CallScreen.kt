package com.karelisio.allo.ui.call

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddIcCall
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.graphics.shapes.Morph
import com.karelisio.allo.core.PhoneNumbers
import com.karelisio.allo.telecom.AudioOutput
import com.karelisio.allo.telecom.AudioRoute
import com.karelisio.allo.telecom.AudioUi
import com.karelisio.allo.telecom.CallInfo
import com.karelisio.allo.telecom.CallPhase
import com.karelisio.allo.ui.components.Avatar
import com.karelisio.allo.ui.components.Cascade
import com.karelisio.allo.ui.components.DialPad
import com.karelisio.allo.ui.components.MorphShape
import com.karelisio.allo.ui.components.rememberHaptics
import com.karelisio.allo.ui.theme.AlloType
import com.karelisio.allo.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

val AnswerGreen = Color(0xFF1F9D55)
val DeclineRed = Color(0xFFD93025)

class CallActions(
    val onAnswer: () -> Unit = {},
    val onDecline: () -> Unit = {},
    val onMessage: () -> Unit = {},
    val onRemind: () -> Unit = {},
    val onHangup: () -> Unit = {},
    val onToggleMute: () -> Unit = {},
    val onToggleSpeaker: () -> Unit = {},
    val onSelectOutput: (AudioOutput) -> Unit = {},
    val onToggleHold: () -> Unit = {},
    val onAddCall: () -> Unit = {},
    val onSwap: () -> Unit = {},
    val onMerge: () -> Unit = {},
    val onDtmf: (Char) -> Unit = {},
)

/** Appel principal : l'appel qui sonne, sinon l'appel au premier plan. */
fun mainCall(calls: List<CallInfo>): CallInfo? {
    val live = calls.filter { it.isLive }
    return live.firstOrNull { it.phase == CallPhase.RINGING }
        ?: live.firstOrNull { it.phase == CallPhase.DIALING || it.phase == CallPhase.CONNECTING }
        ?: live.firstOrNull { it.phase == CallPhase.ACTIVE }
        ?: live.firstOrNull()
        ?: calls.firstOrNull()
}

/**
 * Écran d'appel complet (entrant → en cours), sans état système : testable et prévisualisable.
 * Le passage de l'un à l'autre est l'animation « décroché » : l'avatar se rétracte et monte,
 * les contrôles arrivent en cascade.
 */
@Composable
fun CallScreen(
    calls: List<CallInfo>,
    audio: AudioUi,
    actions: CallActions,
    modifier: Modifier = Modifier,
    remindMinutes: Int = 10,
    now: () -> Long = { System.currentTimeMillis() },
) {
    val main = mainCall(calls) ?: return
    val answered = main.phase != CallPhase.RINGING
    val reduce = LocalReduceMotion.current
    val t by animateFloatAsState(
        if (answered) 1f else 0f,
        if (reduce) tween(0) else spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow), label = "answer",
    )
    val swipe = remember { mutableFloatStateOf(0f) }
    val hasPhoto = main.photoUri != null
    val onBg = if (hasPhoto) Color.White else MaterialTheme.colorScheme.onSurface
    val onBgVariant = if (hasPhoto) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier.fillMaxSize().testTag("call_screen")) {
        CallBackground(main.photoUri)
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(lerp(40.dp, 12.dp, t)))
            val status = when (main.phase) {
                CallPhase.RINGING -> "Appel entrant"
                CallPhase.DIALING, CallPhase.CONNECTING -> "Appel…"
                CallPhase.HOLDING -> "En attente"
                CallPhase.DISCONNECTING, CallPhase.DISCONNECTED -> "Appel terminé"
                CallPhase.ACTIVE -> if (main.isConference) "Conférence · ${main.conferenceSize} participants" else "Appel en cours"
            }
            Text(
                listOfNotNull(status, main.simLabel).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge, color = onBgVariant,
            )
            Spacer(Modifier.height(lerp(20.dp, 10.dp, t)))
            Box(Modifier.size(lerp(300.dp, 112.dp, t)), contentAlignment = Alignment.Center) {
                if (t < 0.98f) {
                    CallWaves(
                        color = if (hasPhoto) Color.White else MaterialTheme.colorScheme.primary,
                        intensity = { swipe.floatValue },
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - t },
                        innerFraction = 0.46f,
                    )
                }
                Avatar(main.name, main.photoUri, size = lerp(136.dp, 96.dp, t), key = main.name ?: main.number)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                main.displayName,
                style = AlloType.bigName.copy(fontSize = AlloType.bigName.fontSize * (1f - 0.22f * t)),
                color = onBg, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("call_name"),
            )
            if (main.name != null && main.number.isNotEmpty()) {
                Text(PhoneNumbers.format(main.number), style = MaterialTheme.typography.titleMedium, color = onBgVariant)
            }
            main.label?.let { label ->
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = CircleShape) {
                    Text(label, Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (answered) {
                Spacer(Modifier.height(6.dp))
                var tick by remember { mutableLongStateOf(now()) }
                LaunchedEffect(main.phase) { while (true) { tick = now(); delay(250) } }
                if (main.phase == CallPhase.ACTIVE && main.connectTimeMillis > 0) {
                    AnimatedTimer(((tick - main.connectTimeMillis) / 1000).coerceAtLeast(0), AlloType.timer, onBg, Modifier.testTag("call_timer"))
                }
            }
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                targetState = answered,
                transitionSpec = { (fadeIn(tween(220, 90)) + slideInVertically { it / 4 }) togetherWith fadeOut(tween(120)) },
                label = "controls",
            ) { ans ->
                if (!ans) IncomingControls(main, actions, remindMinutes, onBg, onSwipeProgress = { swipe.floatValue = it })
                else OngoingControls(calls, main, audio, actions, onBg)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun IncomingControls(
    call: CallInfo,
    actions: CallActions,
    remindMinutes: Int,
    onBg: Color,
    onSwipeProgress: (Float) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (call.number.isNotEmpty()) {
                FilledTonalButton(onClick = actions.onMessage) {
                    Icon(Icons.Rounded.Sms, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Message")
                }
                FilledTonalButton(onClick = actions.onRemind) {
                    Icon(Icons.Rounded.Alarm, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Rappel $remindMinutes min")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        AnswerSwipe(onAnswer = actions.onAnswer, onDecline = actions.onDecline, onProgress = onSwipeProgress, hint = onBg)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            BigRoundButton(Icons.Rounded.CallEnd, "Refuser", DeclineRed, actions.onDecline, onBg, Modifier.testTag("btn_decline"))
            BigRoundButton(Icons.Rounded.Call, "Répondre", AnswerGreen, actions.onAnswer, onBg, Modifier.testTag("btn_answer"))
        }
    }
}

@Composable
private fun BigRoundButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit, labelColor: Color, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(color).clickable(onClick = onClick).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor)
    }
}

/** Glisser vers le haut pour décrocher, vers le bas pour refuser. */
@Composable
private fun AnswerSwipe(onAnswer: () -> Unit, onDecline: () -> Unit, onProgress: (Float) -> Unit, hint: Color) {
    val density = LocalDensity.current
    val maxPx = with(density) { 95.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var done by remember { mutableStateOf(false) }
    val reduce = LocalReduceMotion.current
    val bob = rememberInfiniteTransition(label = "bob")
    val bobY by bob.animateFloat(0f, if (reduce) 0f else -10f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "bobY")
    val progress = (offset.value / maxPx).coerceIn(-1f, 1f)
    LaunchedEffect(progress) { onProgress((-progress).coerceIn(0f, 1f)) }
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val shape = remember(progress) { MorphShape(morph, abs(progress), rotation = progress * 45f) }
    val color = when {
        progress < 0 -> androidx.compose.ui.graphics.lerp(Color.White, AnswerGreen, -progress)
        else -> androidx.compose.ui.graphics.lerp(Color.White, DeclineRed, progress)
    }
    val iconTint = if (abs(progress) > 0.4f) Color.White else AnswerGreen
    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.align(Alignment.TopCenter).graphicsLayer { alpha = 1f - abs(progress) }, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.KeyboardArrowUp, null, tint = hint.copy(0.7f))
            Text("Glisser pour répondre", style = MaterialTheme.typography.labelMedium, color = hint.copy(0.8f))
        }
        Column(Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = 1f - abs(progress) }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Glisser pour refuser", style = MaterialTheme.typography.labelMedium, color = hint.copy(0.8f))
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = hint.copy(0.7f))
        }
        Box(
            Modifier
                .offset { IntOffset(0, (offset.value + if (offset.isRunning || offset.value != 0f) 0f else with(density) { bobY.dp.toPx() }).roundToInt()) }
                .size(84.dp)
                .graphicsLayer { val s = 1f + 0.12f * abs(progress); scaleX = s; scaleY = s }
                .clip(shape)
                .background(color)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        if (!done) scope.launch {
                            val before = offset.value
                            offset.snapTo((offset.value + delta).coerceIn(-maxPx, maxPx))
                            if (abs(before) < maxPx * 0.75f && abs(offset.value) >= maxPx * 0.75f) haptics.tick()
                        }
                    },
                    onDragStopped = {
                        when {
                            offset.value <= -maxPx * 0.75f -> { done = true; haptics.confirm(); onAnswer() }
                            offset.value >= maxPx * 0.75f -> { done = true; haptics.reject(); onDecline() }
                            else -> offset.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 300f))
                        }
                    },
                )
                .semantics { contentDescription = "Glisser vers le haut pour répondre, vers le bas pour refuser" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (progress > 0) Icons.Rounded.CallEnd else Icons.Rounded.Call, null, tint = iconTint, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun OngoingControls(calls: List<CallInfo>, main: CallInfo, audio: AudioUi, actions: CallActions, onBg: Color) {
    var keypad by remember { mutableStateOf(false) }
    var outputs by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val others = calls.filter { it.isLive && it.id != main.id }
    val two = others.isNotEmpty()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        others.forEachIndexed { i, other ->
            Cascade(i) { OtherCallCard(other, actions.onSwap) }
            Spacer(Modifier.height(12.dp))
        }
        AnimatedContent(keypad, label = "keypad", transitionSpec = {
            (fadeIn() + slideInVertically { it / 5 }) togetherWith (fadeOut() + slideOutVertically { it / 5 })
        }) { showPad ->
            if (showPad) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(typed.ifEmpty { " " }, style = AlloType.dialNumber, color = onBg, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    DialPad(onKey = { typed += it; actions.onDtmf(it) }, keySize = 66.dp, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { keypad = false }) { Text("Masquer le clavier") }
                }
            } else {
                val btIcon = when (audio.current.route) {
                    AudioRoute.BLUETOOTH -> Icons.Rounded.Bluetooth
                    AudioRoute.CAR -> Icons.Rounded.DirectionsCar
                    AudioRoute.WIRED -> Icons.Rounded.Headset
                    else -> Icons.AutoMirrored.Rounded.VolumeUp
                }
                val multiOutput = audio.outputs.size > 2
                val items = buildList {
                    add(ControlSpec(Icons.Rounded.MicOff, "Muet", audio.muted, true, actions.onToggleMute))
                    add(ControlSpec(Icons.Rounded.Dialpad, "Clavier", false, true) { keypad = true })
                    add(ControlSpec(btIcon, if (multiOutput) audio.current.name else "Haut-parleur",
                        if (multiOutput) audio.current.route != AudioRoute.EARPIECE else audio.speaker, true) {
                        if (multiOutput) outputs = true else actions.onToggleSpeaker()
                    })
                    add(ControlSpec(Icons.Rounded.Pause, "Attente", main.phase == CallPhase.HOLDING, main.canHold && !two, actions.onToggleHold))
                    add(ControlSpec(Icons.Rounded.AddIcCall, "Ajouter", false, !two, actions.onAddCall))
                    if (two) add(ControlSpec(Icons.Rounded.CallMerge, "Fusionner", false, main.canMerge || others.any { it.canMerge }, actions.onMerge))
                    else add(ControlSpec(Icons.Rounded.SwapCalls, "Basculer", false, false, actions.onSwap))
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items.chunked(3).forEachIndexed { r, row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEachIndexed { c, spec -> Cascade(r * 3 + c + 1) { CallControl(spec, onBg) } }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Cascade(8) { HangupButton(actions.onHangup) }
    }
    if (outputs) {
        ModalBottomSheet(onDismissRequest = { outputs = false }) {
            Text("Sortie audio", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            audio.outputs.forEach { o ->
                val icon = when (o.route) {
                    AudioRoute.EARPIECE -> Icons.Rounded.PhoneInTalk
                    AudioRoute.SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
                    AudioRoute.WIRED -> Icons.Rounded.Headset
                    AudioRoute.BLUETOOTH -> Icons.Rounded.Bluetooth
                    AudioRoute.CAR -> Icons.Rounded.DirectionsCar
                }
                val selected = o.route == audio.current.route && (o.address == null || o.address == audio.current.address)
                ListItem(
                    headlineContent = { Text(o.name) },
                    leadingContent = { Icon(icon, null) },
                    trailingContent = { if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { actions.onSelectOutput(o); outputs = false },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class ControlSpec(val icon: ImageVector, val label: String, val checked: Boolean, val enabled: Boolean, val onClick: () -> Unit)

@Composable
private fun CallControl(spec: ControlSpec, onBg: Color) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val p by animateFloatAsState(if (spec.checked) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 380f), label = "ctl")
    val shape = remember(p) { MorphShape(morph, p.coerceIn(0f, 1f), rotation = p * 30f) }
    val s = MaterialTheme.colorScheme
    val bg = when {
        spec.checked -> s.primary
        else -> s.surfaceContainerHighest.copy(alpha = 0.85f)
    }
    val fg = if (spec.checked) s.onPrimary else s.onSurface
    val haptics = rememberHaptics()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp).graphicsLayer { alpha = if (spec.enabled) 1f else 0.38f }) {
        Box(
            Modifier.size(68.dp).clip(shape).background(bg)
                .clickable(enabled = spec.enabled) { haptics.tick(); spec.onClick() }
                .semantics { contentDescription = spec.label },
            contentAlignment = Alignment.Center,
        ) { Icon(spec.icon, null, tint = fg) }
        Spacer(Modifier.height(6.dp))
        Text(spec.label, style = MaterialTheme.typography.labelMedium, color = onBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HangupButton(onHangup: () -> Unit) {
    val haptics = rememberHaptics()
    Box(
        Modifier.width(112.dp).height(68.dp).clip(RoundedCornerShape(34.dp)).background(DeclineRed)
            .clickable { haptics.reject(); onHangup() }
            .semantics { contentDescription = "Raccrocher" }
            .testTag("btn_hangup"),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
}

@Composable
private fun OtherCallCard(call: CallInfo, onSwap: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(call.name, call.photoUri, size = 44.dp, key = call.name ?: call.number)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(call.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(when (call.phase) {
                    CallPhase.HOLDING -> "En attente"
                    CallPhase.RINGING -> "Appel entrant"
                    CallPhase.ACTIVE -> "En cours"
                    else -> "Appel…"
                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (call.phase == CallPhase.HOLDING) {
                FilledTonalButton(onClick = onSwap) { Icon(Icons.Rounded.SwapCalls, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Basculer") }
            }
        }
    }
}

/** Petit indicateur d'appel actif visible au-dessus des autres écrans (retour à l'appel). */
@Composable
fun ReturnToCallBanner(call: CallInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(true, enter = scaleIn() + fadeIn()) {
        Surface(onClick = onClick, color = AnswerGreen, contentColor = Color.White, shape = CircleShape, modifier = modifier) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PhoneInTalk, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Retour à l'appel · ${call.displayName}", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

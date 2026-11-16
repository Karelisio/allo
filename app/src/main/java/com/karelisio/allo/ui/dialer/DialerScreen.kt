package com.karelisio.allo.ui.dialer

import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.navigation.NavHostController
import com.karelisio.allo.MainViewModel
import com.karelisio.allo.core.PhoneNumbers
import com.karelisio.allo.core.T9
import com.karelisio.allo.data.Sim
import com.karelisio.allo.telecom.Dialer
import com.karelisio.allo.ui.Routes
import com.karelisio.allo.ui.back
import com.karelisio.allo.ui.call.AnswerGreen
import com.karelisio.allo.ui.components.Avatar
import com.karelisio.allo.ui.components.Cascade
import com.karelisio.allo.ui.components.DialPad
import com.karelisio.allo.ui.components.LocalCaller
import com.karelisio.allo.ui.components.MorphShape
import com.karelisio.allo.ui.components.rememberDtmfPlayer
import com.karelisio.allo.ui.components.rememberHaptics
import com.karelisio.allo.ui.components.sharedBoundsKey
import com.karelisio.allo.ui.theme.AlloType
import com.karelisio.allo.ui.theme.LocalReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class T9Result(
    val id: String,
    val name: String?,
    val number: String,
    val photoUri: String?,
    val lookupKey: String?,
    val nameRanges: List<IntRange>,
    val numberRanges: List<IntRange>,
    val score: Int,
)

/** Recherche T9 dans les contacts et le journal. */
fun searchT9(query: String, vm: MainViewModel, limit: Int = 30): List<T9Result> {
    if (query.isEmpty()) return emptyList()
    val out = LinkedHashMap<String, T9Result>()
    vm.contacts.value.forEach { c ->
        val nm = T9.matchName(c.name, query)
        c.phones.forEach { p ->
            val shown = PhoneNumbers.format(p.number)
            val numM = T9.matchNumber(shown, query)
            if (nm != null || numM != null) {
                val k = PhoneNumbers.key(p.number)
                val score = maxOf(nm?.score ?: 0, numM?.score ?: 0) + if (c.starred) 5 else 0
                val prev = out[k]
                if (prev == null || prev.score < score) {
                    out[k] = T9Result("c${c.id}-$k", c.name, p.number, c.thumbUri ?: c.photoUri, c.lookupKey, nm?.ranges.orEmpty(), numM?.ranges.orEmpty(), score)
                }
            }
        }
    }
    vm.callLog.value.asSequence().filter { it.name == null && !PhoneNumbers.isHidden(it.number) }.forEach { e ->
        val k = PhoneNumbers.key(e.number)
        if (out.containsKey(k)) return@forEach
        val shown = PhoneNumbers.format(e.number)
        T9.matchNumber(shown, query)?.let { m -> out[k] = T9Result("l$k", null, e.number, null, null, emptyList(), m.ranges, m.score - 5) }
    }
    return out.values.sortedByDescending { it.score }.take(limit)
}

fun highlight(text: String, ranges: List<IntRange>, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    ranges.forEach { r ->
        if (r.first >= 0 && r.last < text.length) addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), r.first, r.last + 1)
    }
}

@Composable
fun DialerScreen(vm: MainViewModel, nav: NavHostController, initial: String) {
    val ctx = LocalContext.current
    val caller = LocalCaller.current
    val haptics = rememberHaptics()
    val settings by vm.settings.collectAsState()
    val sims by vm.sims.collectAsState()
    var typed by rememberSaveable { mutableStateOf(PhoneNumbers.clean(initial)) }
    var simChoice by remember { mutableStateOf<Sim?>(null) }
    val dtmf = rememberDtmfPlayer(settings.dtmfTones)
    var results by remember { mutableStateOf(emptyList<T9Result>()) }
    LaunchedEffect(typed) { results = withContext(Dispatchers.Default) { searchT9(typed, vm) } }

    fun press(c: Char) {
        if (typed.length >= 40) return
        if (settings.keyVibration) haptics.key()
        dtmf(c)
        typed += c
    }

    fun call(number: String = typed, lookupKey: String? = null) {
        if (number.isBlank()) {
            // Touche d'appel sans numéro : rappelle le dernier numéro composé.
            vm.callLog.value.firstOrNull { it.kind == com.karelisio.allo.core.CallKind.OUTGOING }?.let { typed = PhoneNumbers.clean(it.number) }
            return
        }
        haptics.confirm()
        if (PhoneNumbers.isSpecialCode(number)) { Dialer.place(ctx, number, simChoice?.handle); return }
        val sim = simChoice
        if (sim != null) caller.callWith(number, sim) else caller.call(number, lookupKey)
    }

    Column(
        Modifier.fillMaxSize().sharedBoundsKey("dialer-fab").background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            Spacer(Modifier.weight(1f))
            if (sims.size > 1) {
                AssistChip(
                    onClick = {
                        val i = sims.indexOf(simChoice)
                        simChoice = if (i + 1 >= sims.size) null else sims[i + 1]
                    },
                    label = { Text(simChoice?.let { "${it.shortLabel} · ${it.label}" } ?: "SIM : auto") },
                    leadingIcon = { Icon(Icons.Rounded.SimCard, null, Modifier.size(18.dp)) },
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        // Résultats T9 en cascade.
        val listState = rememberLazyListState()
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, reverseLayout = true, contentPadding = PaddingValues(vertical = 8.dp)) {
            itemsIndexed(results, key = { _, r -> r.id }) { i, r ->
                Cascade(i, Modifier.animateItem(), baseDelay = 22) { T9Row(r, onClick = { call(r.number, r.lookupKey) }, onLong = {
                    if (r.lookupKey != null) nav.navigate(Routes.contact(r.lookupKey)) else typed = PhoneNumbers.clean(r.number)
                }) }
            }
        }
        NumberDisplay(typed, onPaste = { typed = PhoneNumbers.clean(it) })
        AnimatedVisibility(typed.isNotEmpty() && results.none { it.name != null && PhoneNumbers.same(it.number, typed) }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                AssistChip(onClick = { nav.navigate(Routes.edit(number = typed)) }, label = { Text("Créer un contact") },
                    leadingIcon = { Icon(Icons.Rounded.PersonAdd, null, Modifier.size(18.dp)) })
            }
        }
        Spacer(Modifier.height(8.dp))
        DialPad(
            onKey = ::press,
            onLongKey = { d ->
                when (d) {
                    '0' -> { haptics.long(); typed += '+'; true }
                    '1' -> if (typed.isEmpty()) {
                        haptics.long()
                        val h = simChoice?.handle
                        Dialer.voicemail(ctx, h); true
                    } else false
                    else -> false
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f))
            CallButton(onClick = { call() })
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(typed.isNotEmpty(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(28.dp))
                            .combinedClickable(onClick = { haptics.tick(); typed = typed.dropLast(1) }, onLongClick = { haptics.long(); typed = "" })
                            .semantics { contentDescription = "Effacer" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Le numéro apparaît chiffre par chiffre avec un léger rebond. */
@Composable
private fun NumberDisplay(typed: String, onPaste: (String) -> Unit) {
    val ctx = LocalContext.current
    val reduce = LocalReduceMotion.current
    val clip = remember(typed) {
        if (typed.isNotEmpty()) null else runCatching {
            ctx.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
        }.getOrNull()?.let { PhoneNumbers.clean(it) }?.takeIf { it.count(Char::isDigit) in 3..16 }
    }
    val fontSize = when {
        typed.length > 18 -> 26.sp
        typed.length > 13 -> 32.sp
        else -> AlloType.dialNumber.fontSize
    }
    Box(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        if (typed.isEmpty()) {
            if (clip != null) {
                AssistChip(onClick = { onPaste(clip) }, label = { Text("Coller ${PhoneNumbers.format(clip)}") },
                    leadingIcon = { Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp)) })
            }
        } else {
            Row(Modifier.testTag("dial_number"), verticalAlignment = Alignment.CenterVertically) {
                typed.forEachIndexed { i, ch ->
                    key(i) {
                        val anim = remember { Animatable(if (reduce) 1f else 0f) }
                        LaunchedEffect(Unit) { anim.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 700f)) }
                        Text(
                            ch.toString(),
                            style = AlloType.dialNumber.copy(fontSize = fontSize),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.graphicsLayer {
                                val v = anim.value
                                scaleX = 0.4f + 0.6f * v; scaleY = 0.4f + 0.6f * v
                                translationY = (1f - v) * 24f
                                alpha = v.coerceIn(0f, 1f)
                            },
                        )
                        val spaceAfter = typed.length == 10 && typed.startsWith("0") && i % 2 == 1 && i < 9
                        if (spaceAfter) Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CallButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val morph = remember { Morph(MaterialShapes.Pill, MaterialShapes.Cookie9Sided) }
    val p by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "callBtn")
    val shape = remember(p) { MorphShape(morph, p.coerceIn(0f, 1f), rotation = p * 20f) }
    Box(
        Modifier.size(width = 96.dp, height = 72.dp).graphicsLayer { val s = 1f - 0.08f * p; scaleX = s; scaleY = s }
            .clip(shape).background(AnswerGreen)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .semantics { contentDescription = "Appeler" }.testTag("btn_call"),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
}

@Composable
private fun T9Row(r: T9Result, onClick: () -> Unit, onLong: () -> Unit) {
    val hl = MaterialTheme.colorScheme.primary
    Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(r.name, r.photoUri, size = 42.dp, key = r.name ?: r.number)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (r.name != null) Text(highlight(r.name, r.nameRanges, hl), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(highlight(PhoneNumbers.format(r.number), r.numberRanges, hl), style = if (r.name == null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    color = if (r.name == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.Call, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

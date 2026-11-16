package com.karelisio.allo.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.karelisio.allo.Graph
import com.karelisio.allo.R
import com.karelisio.allo.ui.call.CallWaves
import com.karelisio.allo.ui.components.AlloShapes
import com.karelisio.allo.ui.components.MorphShape
import com.karelisio.allo.ui.components.rememberHaptics
import com.karelisio.allo.ui.permissions.PermItem
import com.karelisio.allo.ui.permissions.PermKind
import com.karelisio.allo.ui.permissions.Permissions
import com.karelisio.allo.ui.permissions.rememberPermissionRequester
import com.karelisio.allo.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Intro(val title: String, val text: String, val icon: ImageVector?)

private val intros = listOf(
    Intro("Bienvenue dans Allô", "Ton nouveau téléphone : clavier vivant, journal clair, contacts et écrans d'appel Material You.", null),
    Intro("Un clavier qui répond", "Tape quelques touches : Allô retrouve tes contacts et tes appels en T9, en direct.", Icons.Rounded.Dialpad),
    Intro("Adieu le démarchage", "Les préfixes officiels de démarchage sont bloqués, et tu choisis quoi faire des numéros masqués ou inconnus.", Icons.Rounded.Block),
    Intro("100 % local", "Pas de compte, pas de serveur, pas de statistiques. Tes appels restent sur ton téléphone.", Icons.Rounded.Lock),
)

/** Onboarding animé : présentation, puis une page par autorisation. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, onPermissionsChanged: () -> Unit) {
    val ctx = LocalContext.current
    val items = remember { Permissions.all(ctx) }
    val pageCount = intros.size + items.size + 1
    val pager = rememberPagerState { pageCount }
    val scope = rememberCoroutineScope()
    val settings by Graph.settings.settings.collectAsState(initial = null)
    val manual = settings?.manualChecks.orEmpty()
    var version by remember { mutableIntStateOf(0) }
    var askManual by remember { mutableStateOf<PermItem?>(null) }
    val requester = rememberPermissionRequester { item ->
        version++
        onPermissionsChanged()
        if (item?.kind == PermKind.MANUAL) askManual = item
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { version++ }
    fun next() = scope.launch { if (pager.currentPage < pageCount - 1) pager.animateScrollToPage(pager.currentPage + 1) else onFinished() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            if (pager.currentPage < pageCount - 1) TextButton(onClick = { scope.launch { pager.animateScrollToPage(pageCount - 1) } }) { Text("Passer") }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            when {
                page < intros.size -> IntroPage(intros[page], page)
                page < intros.size + items.size -> {
                    val item = items[page - intros.size]
                    val granted = remember(version, manual) { Permissions.isGranted(ctx, item, manual) }
                    LaunchedEffect(granted) {
                        if (granted && pager.currentPage == page) { delay(900); if (pager.currentPage == page) next() }
                    }
                    PermissionPage(item, granted, onRequest = { requester.request(item) }, onSkip = { next() })
                }
                else -> DonePage(onFinished)
            }
        }
        PageDots(pageCount, pager.currentPage)
        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.End) {
            if (pager.currentPage < intros.size) Button(onClick = { next() }) { Text("Suivant") }
        }
    }
    askManual?.let { item ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askManual = null },
            title = { Text(item.title) },
            text = { Text("As-tu activé ce réglage ? MagicOS ne permet pas de le vérifier automatiquement.") },
            confirmButton = {
                TextButton(onClick = {
                    askManual = null
                    scope.launch { Graph.settings.update { it.copy(manualChecks = it.manualChecks + item.id) }; version++ }
                }) { Text("Oui, c'est fait") }
            },
            dismissButton = { TextButton(onClick = { askManual = null }) { Text("Pas encore") } },
        )
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            val w by animateFloatAsState(if (i == current) 22f else 7f, spring(dampingRatio = 0.6f), label = "dot")
            Box(Modifier.padding(2.dp).height(7.dp).width(w.dp).clip(CircleShape)
                .background(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
        }
    }
}

@Composable
private fun IntroPage(intro: Intro, index: Int) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            if (intro.icon == null) {
                CallWaves(MaterialTheme.colorScheme.primary, intensity = { 0.2f }, modifier = Modifier.fillMaxSize())
                Box(Modifier.size(132.dp).clip(AlloShapes.cookie).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_logo), null, Modifier.size(84.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            } else MorphingBlob(intro.icon, index)
        }
        Spacer(Modifier.height(32.dp))
        Text(intro.title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(intro.text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Illustration animée : une forme M3 qui respire et se déforme autour d'une icône. */
@Composable
fun MorphingBlob(icon: ImageVector, seed: Int, size: Int = 180, granted: Boolean = false) {
    val reduce = LocalReduceMotion.current
    val pairs = listOf(
        MaterialShapes.Cookie9Sided to MaterialShapes.Sunny,
        MaterialShapes.Clover4Leaf to MaterialShapes.Cookie12Sided,
        MaterialShapes.Puffy to MaterialShapes.SoftBurst,
        MaterialShapes.Gem to MaterialShapes.Flower,
    )
    val (a, b) = pairs[seed % pairs.size]
    val morph = remember(seed) { Morph(a, b) }
    val t = rememberInfiniteTransition(label = "blob")
    val p by t.animateFloat(0f, if (reduce) 0f else 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "p")
    val rot by t.animateFloat(0f, if (reduce) 0f else 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "r")
    val doneMorph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val g by animateFloatAsState(if (granted) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 300f), label = "g")
    val shape = remember(p, rot, g) { if (g > 0.01f) MorphShape(doneMorph, g.coerceIn(0f, 1f), rot) else MorphShape(morph, p, rot) }
    val bg = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.primary, g.coerceIn(0f, 1f))
    val fg = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.onPrimary, g.coerceIn(0f, 1f))
    Box(Modifier.size(size.dp).graphicsLayer { val s = lerp(1f, 0.9f, g); scaleX = s; scaleY = s }.clip(shape).background(bg), contentAlignment = Alignment.Center) {
        AnimatedContent(granted, transitionSpec = { scaleIn(spring(dampingRatio = 0.4f)) + fadeIn() togetherWith fadeOut() }, label = "icon") { ok ->
            Icon(if (ok) Icons.Rounded.Check else icon, null, Modifier.size((size * 0.38f).dp), tint = fg)
        }
    }
}

@Composable
private fun PermissionPage(item: PermItem, granted: Boolean, onRequest: () -> Unit, onSkip: () -> Unit) {
    val haptics = rememberHaptics()
    LaunchedEffect(granted) { if (granted) haptics.confirm() }
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        MorphingBlob(item.icon, item.id.hashCode() and 0xff, granted = granted)
        Spacer(Modifier.height(32.dp))
        Text(item.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(item.description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        AnimatedContent(granted, label = "btn") { ok ->
            if (ok) Text("Autorisé", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onRequest, modifier = Modifier.height(56.dp)) { Text(if (item.kind == PermKind.MANUAL) "Ouvrir le réglage" else "Autoriser") }
                if (!item.required || item.kind == PermKind.MANUAL) TextButton(onClick = onSkip) { Text("Plus tard") }
                else TextButton(onClick = onSkip) { Text("Passer") }
            }
        }
    }
}

@Composable
private fun DonePage(onFinished: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        MorphingBlob(Icons.Rounded.Check, 3, granted = true)
        Spacer(Modifier.height(32.dp))
        Text("C'est prêt !", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text("Tu peux revoir chaque autorisation dans Réglages → Autorisations, et tester l'écran d'appel dans Fiabilité.",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onFinished, modifier = Modifier.height(56.dp)) { Text("Commencer") }
    }
}

@Suppress("unused") private val unused = Color.Unspecified

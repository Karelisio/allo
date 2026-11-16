package com.karelisio.allo.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.karelisio.allo.util.safeStart
import kotlinx.coroutines.launch

/** Rendu minimal du Markdown des notes de version (titres, puces, gras). */
fun renderNotes(md: String): AnnotatedString = buildAnnotatedString {
    md.lines().forEach { raw ->
        val line = raw.trimEnd()
        when {
            line.startsWith("#") -> { pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(line.trimStart('#', ' ')); pop() }
            line.startsWith("- ") || line.startsWith("* ") -> append("•  " + line.drop(2))
            else -> append(line)
        }
        append('\n')
    }
}.let { s -> AnnotatedString(s.text.replace("**", "").trimEnd(), s.spanStyles) }

/** Vérifie au lancement (au plus toutes les 6 h) et affiche une bottom sheet avec le changelog. */
@Composable
fun UpdateHost() {
    val ctx = LocalContext.current
    val state by Updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Updater.check(ctx) }
    var waitingPermission by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (waitingPermission && ctx.packageManager.canRequestPackageInstalls()) {
            waitingPermission = false
            (Updater.state.value as? UpdateState.Available)?.let { r -> scope.launch { Updater.downloadAndInstall(ctx, r.release) } }
        }
    }
    val release = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.Installing -> s.release
        else -> null
    } ?: return
    @Suppress("DEPRECATION") val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { Updater.dismiss() }, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Allô ${release.version} est disponible", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(renderNotes(release.notes.ifBlank { "Améliorations et corrections." }), Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            AnimatedContent(state, label = "upd", contentKey = { it::class }) { s ->
                when (s) {
                    is UpdateState.Downloading -> {
                        val p by animateFloatAsState(s.progress, spring(stiffness = 200f), label = "dl")
                        Column {
                            LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Text("Téléchargement… ${(p * 100).toInt()} %", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    is UpdateState.Installing -> Column {
                        LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("Installation…", style = MaterialTheme.typography.labelLarge)
                    }
                    else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { scope.launch { Updater.skip(release) } }) { Text("Ignorer") }
                        TextButton(onClick = { Updater.dismiss() }) { Text("Plus tard") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            if (!ctx.packageManager.canRequestPackageInstalls()) {
                                waitingPermission = true
                                ctx.safeStart(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
                            } else scope.launch { Updater.downloadAndInstall(ctx, release) }
                        }) { Text("Mettre à jour") }
                    }
                }
            }
        }
    }
}

package com.karelisio.allo.ui.log

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.karelisio.allo.Graph
import com.karelisio.allo.MainViewModel
import com.karelisio.allo.core.CallLogGrouping
import com.karelisio.allo.core.PhoneNumbers
import com.karelisio.allo.data.Voicemail
import com.karelisio.allo.telecom.Dialer
import com.karelisio.allo.ui.back
import com.karelisio.allo.ui.components.Cascade
import com.karelisio.allo.ui.components.LocalCaller
import com.karelisio.allo.ui.components.EmptyIllustration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val vmFmt = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.FRENCH)

/** Messagerie vocale visuelle (si fournie par l'opérateur), sinon appel de la messagerie. */
@Composable
fun VoicemailScreen(vm: MainViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val caller = LocalCaller.current
    val scope = rememberCoroutineScope()
    val index by vm.index.collectAsState()
    var items by remember { mutableStateOf<List<Voicemail>?>(null) }
    var playing by remember { mutableStateOf<Long?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    val player = remember { MediaPlayer() }
    DisposableEffect(Unit) { onDispose { runCatching { player.release() } } }
    LaunchedEffect(Unit) { items = Graph.voicemail.all() }
    LaunchedEffect(playing) {
        while (playing != null) {
            progress = runCatching { player.currentPosition.toFloat() / player.duration.coerceAtLeast(1) }.getOrDefault(0f)
            delay(100)
        }
    }
    fun toggle(v: Voicemail) {
        if (playing == v.id) { runCatching { player.pause() }; playing = null; return }
        runCatching {
            player.reset()
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            player.setDataSource(ctx, v.uri)
            player.setOnCompletionListener { playing = null; progress = 0f }
            player.prepare()
            player.start()
            playing = v.id
            scope.launch { Graph.voicemail.markRead(v) }
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Messagerie vocale") },
            navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } })
    }) { pad ->
        val list = items
        if (list != null && list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(pad).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                EmptyIllustration(Icons.Rounded.Voicemail, "Pas de messagerie visuelle",
                    "Les messages apparaissent ici si ton opérateur fournit la messagerie visuelle au système. Sinon, appelle ta messagerie.")
                Spacer(Modifier.height(24.dp))
                Button(onClick = { Dialer.voicemail(ctx, null) }) { Icon(Icons.Rounded.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Appeler la messagerie") }
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(list.orEmpty(), key = { _, v -> v.id }) { i, v ->
                val contact = index.find(v.number)
                Cascade(i) {
                    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(
                        containerColor = if (v.isRead) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledIconButton(onClick = { toggle(v) }, enabled = v.hasContent) {
                                    Icon(if (playing == v.id) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing == v.id) "Pause" else "Écouter")
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(contact?.name ?: PhoneNumbers.format(v.number).ifEmpty { "Numéro masqué" }, style = MaterialTheme.typography.titleMedium)
                                    Text(vmFmt.format(Instant.ofEpochMilli(v.date).atZone(ZoneId.systemDefault())).replaceFirstChar { it.titlecase() } +
                                        " · " + CallLogGrouping.formatClock(v.durationSec), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (v.number.isNotBlank()) IconButton(onClick = { caller.call(v.number, contact?.lookupKey) }) { Icon(Icons.Rounded.Call, "Rappeler") }
                                IconButton(onClick = { scope.launch { Graph.voicemail.delete(v); items = Graph.voicemail.all() } }) { Icon(Icons.Rounded.Delete, "Supprimer") }
                            }
                            if (playing == v.id) {
                                Spacer(Modifier.height(10.dp))
                                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            }
                            v.transcription?.let {
                                Spacer(Modifier.height(8.dp))
                                Text("« $it »", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
                            }
                        }
                    }
                }
            }
        }
    }
}

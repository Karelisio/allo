package com.karelisio.allo.ui.log

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
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.karelisio.allo.ui.components.AlloListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.karelisio.allo.Graph
import com.karelisio.allo.MainViewModel
import com.karelisio.allo.core.CallLogGrouping
import com.karelisio.allo.core.PhoneNumbers
import com.karelisio.allo.ui.Routes
import com.karelisio.allo.ui.back
import com.karelisio.allo.ui.components.Avatar
import com.karelisio.allo.ui.components.Cascade
import com.karelisio.allo.ui.components.LocalCaller
import com.karelisio.allo.ui.components.SimBadge
import com.karelisio.allo.ui.components.sharedAvatar
import com.karelisio.allo.util.copyToClipboard
import com.karelisio.allo.util.openSms
import com.karelisio.allo.util.toast
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val fullFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy · HH:mm", Locale.FRENCH)

/** Détail d'un numéro : historique complet, durée totale, actions. */
@Composable
fun NumberDetailScreen(vm: MainViewModel, nav: NavHostController, number: String, sk: String?) {
    val ctx = LocalContext.current
    val caller = LocalCaller.current
    val scope = rememberCoroutineScope()
    val all by vm.callLog.collectAsState()
    val index by vm.index.collectAsState()
    val contact = index.find(number)
    LaunchedEffect(contact) { if (contact != null) { nav.popBackStack(); nav.navigate(Routes.contact(contact.lookupKey, sk)) } }
    val history = remember(all, number) { all.filter { if (number.isEmpty()) PhoneNumbers.isHidden(it.number) else PhoneNumbers.same(it.number, number) } }
    var blocked by remember { mutableStateOf(false) }
    LaunchedEffect(number) { blocked = Graph.block.isBlacklisted(number) }
    val hidden = number.isEmpty()
    Scaffold(topBar = {
        TopAppBar(title = {}, navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } })
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(null, null, size = 120.dp, key = number, modifier = Modifier.sharedAvatar(sk))
                    Spacer(Modifier.height(16.dp))
                    Text(if (hidden) "Numéro masqué" else PhoneNumbers.format(number), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    if (blocked) Text("Numéro bloqué", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(20.dp))
                    if (!hidden) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ActionButton(Icons.Rounded.Call, "Appeler") { caller.call(number) }
                        ActionButton(Icons.Rounded.Sms, "SMS") { ctx.openSms(number) }
                        ActionButton(Icons.Rounded.PersonAdd, "Ajouter") { nav.navigate(Routes.edit(number = number)) }
                        ActionButton(Icons.Rounded.ContentCopy, "Copier") { ctx.copyToClipboard("Numéro", number) }
                        ActionButton(Icons.Rounded.Block, if (blocked) "Débloquer" else "Bloquer", if (blocked) MaterialTheme.colorScheme.error else null) {
                            scope.launch {
                                if (blocked) Graph.block.unblock(number) else Graph.block.block(number)
                                blocked = !blocked
                                ctx.toast(if (blocked) "Numéro bloqué" else "Numéro débloqué")
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    StatsCard(history.size, history.sumOf { it.durationSec })
                    Spacer(Modifier.height(12.dp))
                }
            }
            itemsIndexed(history, key = { _, e -> e.id }) { i, e -> Cascade(i) { HistoryRow(e) } }
        }
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) { Icon(icon, label, tint = tint ?: MaterialTheme.colorScheme.onSecondaryContainer) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun StatsCard(count: Int, totalSec: Long) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$count", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text("appels", style = MaterialTheme.typography.labelLarge)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(CallLogGrouping.formatDuration(totalSec).ifEmpty { "0 s" }, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text("au total", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun HistoryRow(e: com.karelisio.allo.core.CallEntry) {
    val (ic, tint) = kindIcon(e.kind)
    AlloListItem(
        leadingContent = { Icon(ic, null, tint = tint) },
        headlineContent = { Text(kindLabel(e.kind)) },
        supportingContent = {
            Text(fullFmt.format(Instant.ofEpochMilli(e.date).atZone(ZoneId.systemDefault())).replaceFirstChar { it.titlecase() })
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SimBadge(e.simSlot)
                Spacer(Modifier.width(6.dp))
                Text(CallLogGrouping.formatDuration(e.durationSec), style = MaterialTheme.typography.bodyMedium)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

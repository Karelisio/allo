package com.karelisio.allo.ui.contacts

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import com.karelisio.allo.ui.components.AlloListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.karelisio.allo.Graph
import com.karelisio.allo.MainViewModel
import com.karelisio.allo.core.PhoneNumbers
import com.karelisio.allo.data.ContactSetting
import com.karelisio.allo.integrations.Echo
import com.karelisio.allo.ui.Routes
import com.karelisio.allo.ui.back
import com.karelisio.allo.ui.components.Avatar
import com.karelisio.allo.ui.components.Cascade
import com.karelisio.allo.ui.components.LocalCaller
import com.karelisio.allo.ui.components.rememberHaptics
import com.karelisio.allo.ui.components.sharedAvatar
import com.karelisio.allo.ui.log.ActionButton
import com.karelisio.allo.ui.log.HistoryRow
import com.karelisio.allo.ui.log.StatsCard
import com.karelisio.allo.ui.log.openWhatsapp
import com.karelisio.allo.util.copyToClipboard
import com.karelisio.allo.util.isPackageInstalled
import com.karelisio.allo.util.openSms
import com.karelisio.allo.util.safeStart
import com.karelisio.allo.util.toast
import kotlinx.coroutines.launch

/** Fiche contact animée. */
@Composable
fun ContactDetailScreen(vm: MainViewModel, nav: NavHostController, lookupKey: String, sk: String?) {
    val ctx = LocalContext.current
    val caller = LocalCaller.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val detail by remember(lookupKey) { Graph.contacts.observeDetail(lookupKey) }.collectAsState(initial = null)
    val setting by remember(lookupKey) { Graph.db.misc().contactSettingFlow(lookupKey) }.collectAsState(initial = null)
    val sims by vm.sims.collectAsState()
    val settings by vm.settings.collectAsState()
    val log by vm.callLog.collectAsState()
    val blacklist by Graph.block.blacklist.collectAsState(initial = emptyList())
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var simMenu by remember { mutableStateOf(false) }
    val d = detail
    val history = remember(log, d) { if (d == null) emptyList() else log.filter { e -> d.phones.any { PhoneNumbers.same(it.number, e.number) } } }
    val blocked = d != null && d.phones.isNotEmpty() && d.phones.all { p -> blacklist.any { !it.isPrefix && PhoneNumbers.same(it.pattern, p.number) } }
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && d != null) {
            @Suppress("DEPRECATION")
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            else res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            scope.launch { Graph.contacts.setRingtone(d.id, uri?.toString()) }
        }
    }
    val listState = rememberLazyListState()
    val collapse by remember { derivedStateOf { if (listState.firstVisibleItemIndex > 0) 1f else (listState.firstVisibleItemScrollOffset / 500f).coerceIn(0f, 1f) } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (collapse > 0.8f) d?.name.orEmpty() else "", maxLines = 1) },
            navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
            actions = {
                if (d != null) {
                    IconToggleButton(checked = d.starred, onCheckedChange = { haptics.confirm(); scope.launch { Graph.contacts.setStarred(d.id, it) } }) {
                        Icon(if (d.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favori", tint = if (d.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { nav.navigate(Routes.edit(lookupKey)) }) { Icon(Icons.Rounded.Edit, "Modifier") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Plus") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Supprimer le contact") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            },
        )
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), state = listState, contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp)) {
            item {
                Column(Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - collapse * 0.6f; val s = 1f - collapse * 0.15f; scaleX = s; scaleY = s },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(d?.name, d?.photoUri, size = if (d?.photoUri != null) 200.dp else 152.dp, key = d?.name ?: lookupKey, modifier = Modifier.sharedAvatar(sk))
                    Spacer(Modifier.height(16.dp))
                    Text(d?.name.orEmpty(), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                    if (blocked) Text("Contact bloqué", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(20.dp))
                }
            }
            if (d == null) return@LazyColumn
            val primary = d.phones.firstOrNull { it.isPrimary } ?: d.phones.firstOrNull()
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                    if (primary != null) {
                        ActionButton(Icons.Rounded.Call, "Appeler") { caller.call(primary.number, lookupKey) }
                        ActionButton(Icons.Rounded.Sms, "SMS") { ctx.openSms(primary.number) }
                        if (ctx.isPackageInstalled("com.whatsapp")) ActionButton(Icons.AutoMirrored.Rounded.Chat, "WhatsApp") { ctx.openWhatsapp(primary.number) }
                    }
                    d.emails.firstOrNull()?.let { e ->
                        ActionButton(Icons.Rounded.Email, "E-mail") { ctx.safeStart(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${e.address}"))) }
                    }
                    if (lookupKey in settings.echoContacts && Echo.isInstalled(ctx)) {
                        ActionButton(Icons.Rounded.Favorite, "Pense à toi", MaterialTheme.colorScheme.tertiary) { Echo.thinkingOfYou(ctx, d) }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            item {
                SectionCard {
                    d.phones.forEach { p ->
                        AlloListItem(
                            headlineContent = { Text(PhoneNumbers.format(p.number)) },
                            supportingContent = { Text(p.label + if (p.isPrimary) " · par défaut" else "") },
                            leadingContent = { Icon(Icons.Rounded.Call, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = { IconButton(onClick = { ctx.openSms(p.number) }) { Icon(Icons.Rounded.Sms, "SMS") } },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.combinedClickable(onClick = { caller.call(p.number, lookupKey) }, onLongClick = { haptics.long(); ctx.copyToClipboard("Numéro", p.number) }),
                        )
                    }
                    d.emails.forEach { e ->
                        AlloListItem(
                            headlineContent = { Text(e.address) }, supportingContent = { Text(e.label) },
                            leadingContent = { Icon(Icons.Rounded.Email, null, tint = MaterialTheme.colorScheme.primary) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.combinedClickable(onClick = { ctx.safeStart(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${e.address}"))) },
                                onLongClick = { ctx.copyToClipboard("E-mail", e.address) }),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            item {
                SectionCard {
                    val ringName = remember(d.ringtone) {
                        d.ringtone?.let { runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(it))?.getTitle(ctx) }.getOrNull() } ?: "Par défaut"
                    }
                    AlloListItem(
                        headlineContent = { Text("Sonnerie") }, supportingContent = { Text(ringName) },
                        leadingContent = { Icon(Icons.Rounded.MusicNote, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.combinedClickable(onClick = {
                            ringtoneLauncher.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, d.ringtone?.let(Uri::parse))
                            })
                        }),
                    )
                    if (sims.size > 1) {
                        val pref = sims.firstOrNull { it.accountId == setting?.preferredAccountId }
                        Column {
                            AlloListItem(
                                headlineContent = { Text("SIM préférée") },
                                supportingContent = { Text(pref?.let { "${it.shortLabel} · ${it.label}" } ?: "Demander / SIM par défaut") },
                                leadingContent = { Icon(Icons.Rounded.SimCard, null) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.combinedClickable(onClick = { simMenu = true }),
                            )
                            DropdownMenu(simMenu, { simMenu = false }) {
                                DropdownMenuItem(text = { Text("Aucune") }, onClick = { simMenu = false; scope.launch { Graph.db.misc().putContactSetting(ContactSetting(lookupKey, null)) } })
                                sims.forEach { s ->
                                    DropdownMenuItem(text = { Text("${s.shortLabel} · ${s.label}") }, onClick = {
                                        simMenu = false; scope.launch { Graph.db.misc().putContactSetting(ContactSetting(lookupKey, s.accountId)) }
                                    })
                                }
                            }
                        }
                    }
                    AlloListItem(
                        headlineContent = { Text("Bloquer ce contact") },
                        leadingContent = { Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.error) },
                        trailingContent = {
                            Switch(blocked, onCheckedChange = { on ->
                                scope.launch {
                                    d.phones.forEach { p -> if (on) Graph.block.block(p.number, label = d.name) else Graph.block.unblock(p.number) }
                                    ctx.toast(if (on) "Contact bloqué" else "Contact débloqué")
                                }
                            })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            if (d.note.isNotBlank()) item {
                SectionCard { Text(d.note, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge) }
                Spacer(Modifier.height(12.dp))
            }
            if (history.isNotEmpty()) {
                item {
                    Text("Historique", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
                    StatsCard(history.size, history.sumOf { it.durationSec })
                }
                itemsIndexed(history.take(50), key = { _, e -> e.id }) { i, e -> Cascade(i) { HistoryRow(e) } }
            }
        }
    }
    if (confirmDelete && d != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ${d.name} ?") },
            text = { Text("Le contact sera supprimé de ton téléphone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { Graph.contacts.delete(d.id); nav.back() } }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
fun SectionCard(content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

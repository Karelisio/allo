package com.karelisio.allo.ui.log

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.InputChip
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.History
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.karelisio.allo.util.RecentSearches
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.karelisio.allo.Graph
import com.karelisio.allo.MainViewModel
import com.karelisio.allo.core.CallFilter
import com.karelisio.allo.core.CallGroup
import com.karelisio.allo.core.CallKind
import com.karelisio.allo.core.CallLogGrouping
import com.karelisio.allo.core.PhoneNumbers
import com.karelisio.allo.ui.Routes
import com.karelisio.allo.ui.components.Avatar
import com.karelisio.allo.ui.components.Cascade
import com.karelisio.allo.ui.components.LocalCaller
import com.karelisio.allo.ui.components.SimBadge
import com.karelisio.allo.ui.components.rememberHaptics
import com.karelisio.allo.ui.components.sharedAvatar
import com.karelisio.allo.util.copyToClipboard
import com.karelisio.allo.util.openSms
import com.karelisio.allo.util.safeStart
import com.karelisio.allo.util.toast
import kotlinx.coroutines.launch

val FILTER_LABELS = listOf(CallFilter.ALL to "Tous", CallFilter.MISSED to "Manqués", CallFilter.INCOMING to "Entrants", CallFilter.OUTGOING to "Sortants", CallFilter.BLOCKED to "Bloqués")

@Composable
fun kindIcon(kind: CallKind): Pair<ImageVector, Color> {
    val s = MaterialTheme.colorScheme
    return when (kind) {
        CallKind.INCOMING -> Icons.AutoMirrored.Rounded.CallReceived to s.primary
        CallKind.OUTGOING -> Icons.AutoMirrored.Rounded.CallMade to s.tertiary
        CallKind.MISSED -> Icons.AutoMirrored.Rounded.CallMissed to s.error
        CallKind.REJECTED -> Icons.Rounded.CallEnd to s.error
        CallKind.BLOCKED -> Icons.Rounded.Block to s.outline
        CallKind.VOICEMAIL -> Icons.Rounded.Voicemail to s.secondary
    }
}

fun kindLabel(k: CallKind) = when (k) {
    CallKind.INCOMING -> "Entrant"; CallKind.OUTGOING -> "Sortant"; CallKind.MISSED -> "Manqué"
    CallKind.REJECTED -> "Refusé"; CallKind.BLOCKED -> "Bloqué"; CallKind.VOICEMAIL -> "Messagerie"
}

private val hourFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

fun timeLabel(date: Long): String = hourFmt.format(java.time.Instant.ofEpochMilli(date).atZone(java.time.ZoneId.systemDefault()))

@Composable
fun CallLogTab(vm: MainViewModel, nav: NavHostController) {
    val entries by vm.callLog.collectAsState()
    var filter by rememberSaveable { mutableStateOf(CallFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    val filtered = remember(entries, query) {
        if (query.isBlank()) entries else entries.filter {
            (it.name?.contains(query, true) == true) || it.number.filter(Char::isDigit).contains(query.filter(Char::isDigit).ifEmpty { "\u0000" }) ||
                PhoneNumbers.toNational(it.number).contains(query.filter(Char::isDigit).ifEmpty { "\u0000" })
        }
    }
    val sections = remember(filtered, filter) { CallLogGrouping.group(filtered, filter = filter) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Sélection multiple : identifiants des groupes sélectionnés.
    val selected = remember { mutableStateListOf<Long>() }
    val selecting = selected.isNotEmpty()
    var recent by remember { mutableStateOf(RecentSearches.get(ctx)) }
    LaunchedEffect(query) {
        if (query.trim().length >= 2) { kotlinx.coroutines.delay(1500); RecentSearches.add(ctx, query.trim()); recent = RecentSearches.get(ctx) }
    }
    androidx.activity.compose.BackHandler(selecting) { selected.clear() }
    Column(Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedContent(selecting, label = "selbar") { sel ->
            if (sel) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selected.clear() }) { Icon(Icons.Rounded.Close, "Annuler la sélection") }
                    Text("${selected.size} sélectionné${if (selected.size > 1) "s" else ""}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        val all = sections.flatMap { s -> s.groups.map { it.id } }
                        if (selected.size == all.size) selected.clear() else { selected.clear(); selected.addAll(all) }
                    }) { Icon(Icons.Rounded.SelectAll, "Tout sélectionner") }
                    IconButton(onClick = {
                        val ids = sections.flatMap { s -> s.groups.filter { it.id in selected }.flatMap { g -> g.entries.map { it.id } } }
                        selected.clear()
                        scope.launch { Graph.callLog.delete(ids); }
                        ctx.toast("${ids.size} appel(s) supprimé(s)")
                    }) { Icon(Icons.Rounded.Delete, "Supprimer", tint = MaterialTheme.colorScheme.error) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LazyRow(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(FILTER_LABELS) { (f, label) -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(label) }) }
                    }
                    IconButton(onClick = { nav.navigate(com.karelisio.allo.ui.Routes.VOICEMAIL) }) { Icon(Icons.Rounded.Voicemail, "Messagerie vocale") }
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Rechercher") }
                }
            }
        }
        AnimatedVisibility(searching && !selecting) {
            Column {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Nom ou numéro") }, singleLine = true, shape = RoundedCornerShape(28.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) { RecentSearches.add(ctx, query.trim()); recent = RecentSearches.get(ctx) } }))
                if (query.isEmpty() && recent.isNotEmpty()) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recent) { r ->
                            InputChip(selected = false, onClick = { query = r }, label = { Text(r) },
                                leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(16.dp)) },
                                trailingIcon = { Icon(Icons.Rounded.Close, "Oublier", Modifier.size(16.dp).clickable { RecentSearches.remove(ctx, r); recent = RecentSearches.get(ctx) }) })
                        }
                    }
                }
            }
        }
        if (sections.isEmpty()) {
            if (entries.isEmpty()) EmptyState("Aucun appel pour l'instant", detail = "Tes appels apparaîtront ici, regroupés par jour.")
            else EmptyState("Aucun appel ne correspond", Icons.Rounded.Search)
            return
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            var index = 0
            sections.forEach { sec ->
                stickyHeader(key = "h-${sec.day}") {
                    Text(sec.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 24.dp, vertical = 8.dp))
                }
                sec.groups.forEach { g ->
                    val i = index++
                    item(key = "g-${g.id}") {
                        Cascade(i, Modifier.animateItem()) {
                            CallRow(g, nav, RowSelection(
                                active = selecting,
                                selected = g.id in selected,
                                onToggle = { if (g.id in selected) selected.remove(g.id) else selected.add(g.id) },
                            ))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(text: String, icon: ImageVector = Icons.Rounded.History, detail: String? = null) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        com.karelisio.allo.ui.components.EmptyIllustration(icon, text, detail)
    }
}

/** État de sélection multiple d'une ligne. */
class RowSelection(val active: Boolean, val selected: Boolean, val onToggle: () -> Unit)

/** Ligne du journal : glisser à droite = rappeler, à gauche = SMS ; appui long = menu. */
@Composable
fun CallRow(g: CallGroup, nav: NavHostController, selection: RowSelection? = null) {
    val ctx = LocalContext.current
    val caller = LocalCaller.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val e = g.first
    val hidden = PhoneNumbers.isHidden(e.number)
    var menu by remember { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState()
    val sk = "log-${g.id}"
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = !hidden && selection?.active != true,
        enableDismissFromEndToStart = !hidden && selection?.active != true,
        onDismiss = { v ->
            haptics.confirm()
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> caller.call(e.number, e.lookupUri)
                SwipeToDismissBoxValue.EndToStart -> ctx.openSms(e.number)
                else -> {}
            }
            scope.launch { state.reset() }
        },
        backgroundContent = {
            val dir = state.dismissDirection
            val (bg, icon, align) = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(MaterialTheme.colorScheme.primaryContainer, Icons.Rounded.Call, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(MaterialTheme.colorScheme.tertiaryContainer, Icons.Rounded.Sms, Alignment.CenterEnd)
                else -> Triple(Color.Transparent, Icons.Rounded.Call, Alignment.Center)
            }
            Box(Modifier.fillMaxSize().padding(horizontal = 8.dp).clip(RoundedCornerShape(28.dp)).background(bg).padding(horizontal = 24.dp), contentAlignment = align) {
                if (dir != SwipeToDismissBoxValue.Settled) Icon(icon, null)
            }
        },
    ) {
        Box {
            Row(
                Modifier.fillMaxWidth()
                    .background(if (selection?.selected == true) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .combinedClickable(
                        onClick = {
                            if (selection?.active == true) { haptics.tick(); selection.onToggle() }
                            else nav.navigate(if (e.lookupUri != null) Routes.contact(e.lookupUri, sk) else Routes.number(e.number, sk))
                        },
                        onLongClick = { haptics.long(); if (selection?.active == true) selection.onToggle() else menu = true },
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selection?.active == true) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Checkbox(selection.selected, { selection.onToggle() }) }
                } else {
                    Avatar(e.name, e.photoUri, size = 48.dp, key = e.name ?: e.number, modifier = Modifier.sharedAvatar(sk))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            e.name ?: if (hidden) "Numéro masqué" else PhoneNumbers.format(e.number),
                            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (e.kind == CallKind.MISSED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (g.count > 1) Text("  (${g.count})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        g.kinds.distinct().take(3).forEach { k ->
                            val (ic, tint) = kindIcon(k)
                            Icon(ic, kindLabel(k), Modifier.size(16.dp), tint = tint)
                        }
                        Spacer(Modifier.width(4.dp))
                        SimBadge(e.simSlot)
                        if (e.simSlot != null) Spacer(Modifier.width(4.dp))
                        val dur = CallLogGrouping.formatDuration(g.totalDuration)
                        val info = if (e.name == null) remember(e.number) { com.karelisio.allo.core.NumberInfo.describe(e.number)?.substringBefore(" ·") } else null
                        Text(listOfNotNull(timeLabel(e.date), dur.ifEmpty { null }, info).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                if (!hidden && selection?.active != true) IconButton(onClick = { haptics.tick(); caller.call(e.number, e.lookupUri) }) { Icon(Icons.Rounded.Call, "Appeler", tint = MaterialTheme.colorScheme.primary) }
            }
            DropdownMenu(menu, { menu = false }) {
                if (!hidden) {
                    DropdownMenuItem(text = { Text("Copier le numéro") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                        onClick = { menu = false; ctx.copyToClipboard("Numéro", e.number) })
                    DropdownMenuItem(text = { Text("Bloquer") }, leadingIcon = { Icon(Icons.Rounded.Block, null) },
                        onClick = { menu = false; scope.launch { Graph.block.block(e.number) }; ctx.toast("Numéro bloqué") })
                    if (e.name == null) DropdownMenuItem(text = { Text("Signaler comme démarchage") }, leadingIcon = { Icon(Icons.Rounded.Block, null) },
                        onClick = { menu = false; scope.launch { Graph.block.block(e.number, label = REPORTED_LABEL) }; ctx.toast("Signalé comme démarchage et bloqué") })
                    if (e.name == null) DropdownMenuItem(text = { Text("Ajouter aux contacts") }, leadingIcon = { Icon(Icons.Rounded.PersonAdd, null) },
                        onClick = { menu = false; nav.navigate(Routes.edit(number = e.number)) })
                }
                if (selection != null) DropdownMenuItem(text = { Text("Sélectionner") }, leadingIcon = { Icon(Icons.Rounded.Checklist, null) },
                    onClick = { menu = false; selection.onToggle() })
                DropdownMenuItem(text = { Text("Supprimer") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = { menu = false; scope.launch { Graph.callLog.delete(g.entries.map { it.id }) } })
            }
        }
    }
}

fun addToContactsIntent(number: String) = Intent(ContactsContract.Intents.Insert.ACTION).apply {
    type = ContactsContract.RawContacts.CONTENT_TYPE
    putExtra(ContactsContract.Intents.Insert.PHONE, number)
}

fun whatsappIntent(number: String): Intent {
    val n = PhoneNumbers.normalize(number).removePrefix("+")
    return Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$n")).setPackage("com.whatsapp")
}

fun android.content.Context.openWhatsapp(number: String) { if (!safeStart(whatsappIntent(number))) toast("WhatsApp n'est pas installé") }

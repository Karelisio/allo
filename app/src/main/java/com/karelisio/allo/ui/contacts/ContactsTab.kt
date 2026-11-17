package com.karelisio.allo.ui.contacts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.karelisio.allo.MainViewModel
import com.karelisio.allo.core.CallKind
import com.karelisio.allo.core.PhoneNumbers
import com.karelisio.allo.core.T9
import com.karelisio.allo.data.Contact
import com.karelisio.allo.ui.Routes
import com.karelisio.allo.ui.components.AlloShapes
import com.karelisio.allo.ui.components.Avatar
import com.karelisio.allo.ui.components.Cascade
import com.karelisio.allo.ui.components.LocalCaller
import com.karelisio.allo.ui.components.rememberHaptics
import com.karelisio.allo.ui.components.sharedAvatar
import com.karelisio.allo.ui.components.sharedName
import com.karelisio.allo.ui.components.pressBounce
import com.karelisio.allo.ui.log.EmptyState
import com.karelisio.allo.widget.Shortcuts
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

fun sectionLetter(name: String): Char {
    val c = T9.stripAccents(name).firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c else '#'
}

@Composable
fun FavoritesTab(vm: MainViewModel, nav: NavHostController) {
    val contacts by vm.contacts.collectAsState()
    val log by vm.callLog.collectAsState()
    val favorites = remember(contacts) { contacts.filter { it.starred } }
    val ctx = LocalContext.current
    LaunchedEffect(favorites) { Shortcuts.update(ctx, favorites) }
    val frequent = remember(log, favorites) {
        log.asSequence().filter { it.lookupUri != null && it.kind != CallKind.BLOCKED }
            .groupBy { it.lookupUri }.entries.sortedByDescending { it.value.size }
            .mapNotNull { e -> contacts.firstOrNull { it.lookupKey == e.key } }
            .filter { !it.starred }.take(8).toList()
    }
    val caller = LocalCaller.current
    val haptics = rememberHaptics()
    if (favorites.isEmpty() && frequent.isEmpty()) {
        EmptyState("Pas encore de favoris", androidx.compose.material.icons.Icons.Rounded.StarOutline, "Touche l'étoile sur la fiche d'un contact pour l'épingler ici.")
        return
    }
    LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 96.dp)) {
        itemsIndexed(favorites, key = { _, c -> "f${c.id}" }) { i, c ->
            Cascade(i) {
                FavoriteTile(c, sk = "fav-${c.lookupKey}",
                    onClick = { haptics.tick(); c.primaryNumber?.let { caller.call(it, c.lookupKey) } },
                    onLong = { haptics.long(); nav.navigate(Routes.contact(c.lookupKey, "fav-${c.lookupKey}")) })
            }
        }
        if (frequent.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Appelés souvent", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp))
            }
            itemsIndexed(frequent, key = { _, c -> "q${c.id}" }) { i, c ->
                Cascade(favorites.size + i) {
                    FavoriteTile(c, sk = "freq-${c.lookupKey}", small = true,
                        onClick = { c.primaryNumber?.let { caller.call(it, c.lookupKey) } },
                        onLong = { nav.navigate(Routes.contact(c.lookupKey, "freq-${c.lookupKey}")) })
                }
            }
        }
    }
}

@Composable
private fun FavoriteTile(c: Contact, sk: String, small: Boolean = false, onClick: () -> Unit, onLong: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        Modifier.padding(6.dp).pressBounce(interaction, 0.92f).clip(RoundedCornerShape(24.dp))
            .combinedClickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick, onLongClick = onLong).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(c.name, c.photoUri, size = if (small) 60.dp else 76.dp, key = c.name, modifier = Modifier.sharedAvatar(sk))
        Spacer(Modifier.size(8.dp))
        Text(c.name.substringBefore(' '), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
fun ContactsTab(vm: MainViewModel, nav: NavHostController) {
    val contacts by vm.contacts.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(contacts, query) {
        if (query.isBlank()) contacts else {
            val q = T9.stripAccents(query).lowercase()
            val digits = query.filter(Char::isDigit)
            contacts.filter { c ->
                T9.stripAccents(c.name).lowercase().contains(q) ||
                    (digits.length >= 3 && c.phones.any { PhoneNumbers.toNational(it.number).contains(digits) })
            }
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { sectionLetter(it.name) }.toSortedMap(compareBy { if (it == '#') '~' else it }) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Position de chaque lettre dans la liste (en-tête compris).
    val letterIndex = remember(grouped) {
        var i = 2
        grouped.map { (l, list) -> val at = i; i += list.size + 1; l to at }.toMap()
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 96.dp, end = 24.dp)) {
            item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Rechercher un contact") }, singleLine = true, shape = RoundedCornerShape(28.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, null) })
            }
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { nav.navigate(Routes.edit()) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp).clip(AlloShapes.sunny).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PersonAdd, null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text("Nouveau contact", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            grouped.forEach { (letter, list) ->
                stickyHeader(key = "L$letter") {
                    Text(letter.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 28.dp, vertical = 6.dp))
                }
                list.forEach { c ->
                    item(key = "c${c.id}") {
                        val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        Row(
                            Modifier.fillMaxWidth().animateItem().pressBounce(interaction)
                                .clickable(interaction, androidx.compose.material3.ripple()) { nav.navigate(Routes.contact(c.lookupKey, "list-${c.lookupKey}")) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(c.name, c.thumbUri ?: c.photoUri, size = 44.dp, key = c.name, modifier = Modifier.sharedAvatar("list-${c.lookupKey}"))
                            Spacer(Modifier.width(16.dp))
                            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.sharedName("list-${c.lookupKey}"))
                        }
                    }
                }
            }
            if (filtered.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    com.karelisio.allo.ui.components.EmptyIllustration(
                        if (contacts.isEmpty()) androidx.compose.material.icons.Icons.Rounded.PersonAdd else Icons.Rounded.Search,
                        if (contacts.isEmpty()) "Aucun contact" else "Aucun résultat",
                        if (contacts.isEmpty()) "Crée ton premier contact ou autorise l'accès aux contacts." else null)
                }
            }
        }
        if (grouped.size > 4 && query.isBlank()) {
            FastScroller(
                letters = grouped.keys.toList(),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 72.dp),
                onLetter = { l -> letterIndex[l]?.let { scope.launch { listState.scrollToItem(it) } } },
            )
        }
    }
}

/** Index de défilement alphabétique animé, avec une bulle qui suit le doigt. */
@Composable
fun FastScroller(letters: List<Char>, modifier: Modifier = Modifier, onLetter: (Char) -> Unit) {
    var height by remember { mutableFloatStateOf(1f) }
    var touchY by remember { mutableFloatStateOf(-1f) }
    var current by remember { mutableStateOf<Char?>(null) }
    val haptics = rememberHaptics()
    fun pick(y: Float) {
        val i = ((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val l = letters[i]
        if (l != current) { current = l; haptics.tick(); onLetter(l) }
    }
    Box(modifier) {
        Column(
            Modifier.width(28.dp).fillMaxHeight().onSizeChanged { height = it.height.toFloat() }
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { touchY = it.y; pick(it.y) },
                        onDragEnd = { touchY = -1f; current = null },
                        onDragCancel = { touchY = -1f; current = null },
                    ) { change, _ -> touchY = change.position.y; pick(change.position.y) }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { l ->
                val active = l == current
                val s by animateFloatAsState(if (active) 1.6f else 1f, spring(dampingRatio = 0.4f, stiffness = 600f), label = "l")
                Text(l.toString(), style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s; translationX = -(s - 1f) * 18f })
            }
        }
        AnimatedVisibility(current != null && touchY >= 0, enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(), exit = scaleOut() + fadeOut(),
            modifier = Modifier.offset { IntOffset(-140, (touchY - 90).roundToInt()) }) {
            Box(Modifier.size(64.dp).clip(AlloShapes.cookie).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text(current?.toString().orEmpty(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

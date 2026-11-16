package com.karelisio.allo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.karelisio.allo.Graph
import com.karelisio.allo.MainViewModel
import com.karelisio.allo.core.BlockAction
import com.karelisio.allo.core.BlockCategory
import com.karelisio.allo.core.BlockConfig
import com.karelisio.allo.core.PhoneNumbers
import com.karelisio.allo.data.TelemarketingPrefix
import com.karelisio.allo.ui.components.Cascade
import com.karelisio.allo.ui.log.timeLabel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)

private fun categoryTitle(c: BlockCategory) = when (c) {
    BlockCategory.TELEMARKETING -> "Préfixes de démarchage"
    BlockCategory.HIDDEN -> "Numéros masqués"
    BlockCategory.UNKNOWN -> "Inconnus (hors contacts)"
    BlockCategory.BLACKLIST -> "Liste noire"
}

private fun BlockConfig.with(c: BlockCategory, a: BlockAction) = when (c) {
    BlockCategory.TELEMARKETING -> copy(telemarketing = a)
    BlockCategory.HIDDEN -> copy(hidden = a)
    BlockCategory.UNKNOWN -> copy(unknown = a)
    BlockCategory.BLACKLIST -> copy(blacklist = a)
}

@Composable
fun BlockingScreen(vm: MainViewModel, nav: NavHostController) {
    val s by vm.settings.collectAsState()
    val count by vm.blockedThisMonth.collectAsState()
    val prefixes by Graph.block.prefixes.collectAsState(initial = emptyList())
    val black by Graph.block.blacklist.collectAsState(initial = emptyList())
    val white by Graph.block.whitelist.collectAsState(initial = emptyList())
    val blocked by Graph.block.blockedCalls.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var addDialog by remember { mutableStateOf<String?>(null) } // "black" | "white" | "prefix"

    SettingsScaffold("Blocage", nav) {
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("$count", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(if (count <= 1) "appel filtré ce mois-ci" else "appels filtrés ce mois-ci", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
        section("Que faire de ces appels ?")
        BlockCategory.entries.forEach { c ->
            item(key = "cat-$c") {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(categoryTitle(c), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    val current = s.blockConfig.actionFor(c)
                    val options = listOf(BlockAction.BLOCK to "Bloquer", BlockAction.SILENCE to "Silencieux", BlockAction.ALLOW to "Laisser passer")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { i, (a, label) ->
                            SegmentedButton(
                                selected = current == a,
                                onClick = { scope.launch { Graph.settings.update { it.copy(blockConfig = it.blockConfig.with(c, a)) } } },
                                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                }
            }
        }
        item { SwitchRow("Notification discrète à chaque blocage", s.blockedNotification, { v -> scope.launch { Graph.settings.update { it.copy(blockedNotification = v) } } }) }

        section("Préfixes de démarchage (ARCEP)")
        items(prefixes.size) { i ->
            val p = prefixes[i]
            ListItem(
                headlineContent = { Text("${p.prefix} · +33 ${p.prefix.drop(1)}") },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(p.enabled, { v -> scope.launch { Graph.block.putPrefix(p.copy(enabled = v)) } })
                        IconButton(onClick = { scope.launch { Graph.block.removePrefix(p.prefix) } }) { Icon(Icons.Rounded.Delete, "Retirer") }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { addDialog = "prefix" }) { Icon(Icons.Rounded.Add, null); Text("Ajouter un préfixe") }
                TextButton(onClick = { scope.launch { Graph.block.resetPrefixes() } }) { Text("Rétablir la liste officielle") }
            }
        }

        section("Liste noire")
        items(black.size) { i ->
            val e = black[i]
            ListItem(
                headlineContent = { Text(if (e.isPrefix) "Préfixe ${e.pattern}" else PhoneNumbers.format(e.pattern)) },
                supportingContent = e.label?.let { { Text(it) } },
                trailingContent = { IconButton(onClick = { scope.launch { Graph.block.removeBlack(e.id) } }) { Icon(Icons.Rounded.Delete, "Retirer") } },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        item { FilledTonalButton(onClick = { addDialog = "black" }, modifier = Modifier.padding(horizontal = 16.dp)) { Icon(Icons.Rounded.Add, null); Text("Bloquer un numéro ou un préfixe") } }

        section("Liste blanche (toujours autorisés)")
        items(white.size) { i ->
            val e = white[i]
            ListItem(
                headlineContent = { Text(if (e.isPrefix) "Préfixe ${e.pattern}" else PhoneNumbers.format(e.pattern)) },
                trailingContent = { IconButton(onClick = { scope.launch { Graph.block.removeWhite(e.id) } }) { Icon(Icons.Rounded.Delete, "Retirer") } },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        item { FilledTonalButton(onClick = { addDialog = "white" }, modifier = Modifier.padding(horizontal = 16.dp)) { Icon(Icons.Rounded.Add, null); Text("Autoriser un numéro ou un préfixe") } }

        section("Journal des appels filtrés")
        if (blocked.isEmpty()) item { Text("Aucun appel filtré.", Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(blocked.size.coerceAtMost(200)) { i ->
            val b = blocked[i]
            val cat = runCatching { BlockCategory.valueOf(b.category) }.getOrNull()
            val act = runCatching { BlockAction.valueOf(b.action) }.getOrNull()
            Cascade(i) {
                ListItem(
                    headlineContent = { Text(if (b.number.isBlank()) "Numéro masqué" else PhoneNumbers.format(b.number)) },
                    supportingContent = {
                        Text(listOfNotNull(
                            dayFmt.format(Instant.ofEpochMilli(b.date).atZone(ZoneId.systemDefault())) + " " + timeLabel(b.date),
                            cat?.let(::categoryTitle),
                            if (act == BlockAction.SILENCE) "sonnerie coupée" else "bloqué",
                        ).joinToString(" · "))
                    },
                    trailingContent = {
                        if (b.number.isNotBlank()) TextButton(onClick = { scope.launch { Graph.block.allow(b.number) } }) { Text("Autoriser") }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        if (blocked.isNotEmpty()) item { TextButton(onClick = { scope.launch { Graph.block.clearBlockedLog() } }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Effacer le journal") } }
    }

    addDialog?.let { kind ->
        var value by remember { mutableStateOf("") }
        var prefix by remember { mutableStateOf(kind == "prefix") }
        AlertDialog(
            onDismissRequest = { addDialog = null },
            title = { Text(when (kind) { "prefix" -> "Préfixe de démarchage"; "black" -> "Bloquer"; else -> "Autoriser" }) },
            text = {
                Column {
                    OutlinedTextField(value, { value = it }, singleLine = true, label = { Text(if (prefix) "Préfixe (ex. 0162 ou +33162)" else "Numéro") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    if (kind != "prefix") Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(prefix, { prefix = it }); Text("C'est un préfixe")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = value.trim()
                    addDialog = null
                    if (v.isEmpty()) return@TextButton
                    scope.launch {
                        when (kind) {
                            "prefix" -> Graph.block.putPrefix(TelemarketingPrefix(PhoneNumbers.toNationalPrefix(v)))
                            "black" -> Graph.block.block(if (prefix) PhoneNumbers.toNationalPrefix(v) else v, prefix)
                            else -> Graph.block.allow(if (prefix) PhoneNumbers.toNationalPrefix(v) else v, prefix)
                        }
                    }
                }) { Text("Ajouter") }
            },
            dismissButton = { TextButton(onClick = { addDialog = null }) { Text("Annuler") } },
        )
    }
}

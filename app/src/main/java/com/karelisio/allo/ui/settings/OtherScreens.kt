package com.karelisio.allo.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import com.karelisio.allo.ui.components.AlloListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import com.karelisio.allo.BuildConfig
import com.karelisio.allo.Graph
import com.karelisio.allo.MainViewModel
import com.karelisio.allo.core.BlockAction
import com.karelisio.allo.data.QuickReply
import com.karelisio.allo.integrations.Echo
import com.karelisio.allo.telecom.Dialer
import com.karelisio.allo.ui.call.InCallActivity
import com.karelisio.allo.ui.components.Avatar
import com.karelisio.allo.ui.permissions.PermItem
import com.karelisio.allo.ui.permissions.PermKind
import com.karelisio.allo.ui.permissions.Permissions
import com.karelisio.allo.ui.permissions.rememberPermissionRequester
import com.karelisio.allo.update.UpdateState
import com.karelisio.allo.update.Updater
import com.karelisio.allo.util.isHonorDevice
import com.karelisio.allo.util.safeStart
import kotlinx.coroutines.launch

@Composable
fun QuickRepliesScreen(nav: NavHostController) {
    val replies by Graph.db.misc().replies().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    val edits = remember { mutableStateMapOf<Long, String>() }
    SettingsScaffold("Réponses rapides", nav) {
        item { Text("Envoyées par SMS quand tu refuses un appel.", Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(replies.size, key = { replies[it].id }) { i ->
            val r = replies[i]
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(edits[r.id] ?: r.text, { edits[r.id] = it }, Modifier.weight(1f), shape = RoundedCornerShape(20.dp))
                if (edits[r.id] != null && edits[r.id] != r.text) TextButton(onClick = {
                    val t = edits.remove(r.id).orEmpty(); scope.launch { Graph.db.misc().putReply(r.copy(text = t)) }
                }) { Text("OK") }
                IconButton(onClick = { scope.launch { Graph.db.misc().removeReply(r.id) } }) { Icon(Icons.Rounded.Delete, "Supprimer") }
            }
        }
        item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text("Nouveau message") }, shape = RoundedCornerShape(20.dp))
                IconButton(onClick = {
                    val t = draft.trim(); draft = ""
                    if (t.isNotEmpty()) scope.launch { Graph.db.misc().putReply(QuickReply(text = t, position = replies.size)) }
                }) { Icon(Icons.Rounded.Add, "Ajouter") }
            }
        }
    }
}

@Composable
private fun StatusIcon(ok: Boolean?) {
    when (ok) {
        true -> Icon(Icons.Rounded.CheckCircle, "OK", tint = MaterialTheme.colorScheme.primary)
        false -> Icon(Icons.Rounded.ErrorOutline, "À corriger", tint = MaterialTheme.colorScheme.error)
        null -> Icon(Icons.AutoMirrored.Rounded.HelpOutline, "Inconnu", tint = MaterialTheme.colorScheme.outline)
    }
}

/** Écran « Autorisations » : état de chacune et bouton de correction. */
@Composable
fun PermissionsScreen(vm: MainViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val items = remember { Permissions.all(ctx) }
    val s by vm.settings.collectAsState()
    var version by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var askManual by remember { mutableStateOf<PermItem?>(null) }
    val requester = rememberPermissionRequester { item -> version++; vm.recheck(); if (item?.kind == PermKind.MANUAL) askManual = item }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { version++ }
    SettingsScaffold("Autorisations", nav) {
        items(items.size) { i ->
            val item = items[i]
            val granted = remember(version, s.manualChecks) { Permissions.isGranted(ctx, item, s.manualChecks) }
            PermRow(item, granted) { requester.request(item) }
        }
    }
    askManual?.let { item -> ManualConfirm(item, onDone = { ok ->
        askManual = null
        scope.launch { Graph.settings.update { st -> st.copy(manualChecks = if (ok) st.manualChecks + item.id else st.manualChecks - item.id) } }
    }) }
}

@Composable
private fun PermRow(item: PermItem, granted: Boolean, onFix: () -> Unit) {
    AlloListItem(
        leadingContent = { Icon(item.icon, null) },
        headlineContent = { Text(item.title) },
        supportingContent = { Text(item.description) },
        trailingContent = {
            if (granted) StatusIcon(true)
            else FilledTonalButton(onClick = onFix) { Text(if (item.kind == PermKind.MANUAL) "Ouvrir" else "Corriger") }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun ManualConfirm(item: PermItem, onDone: (Boolean) -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onDone(false) },
        title = { Text(item.title) },
        text = { Text("As-tu activé ce réglage ? MagicOS ne permet pas de le vérifier automatiquement. Si l'option n'existe pas sur ton téléphone, choisis « Option introuvable ».") },
        confirmButton = { TextButton(onClick = { onDone(true) }) { Text("C'est fait") } },
        dismissButton = { Row { TextButton(onClick = { onDone(true) }) { Text("Option introuvable") }; TextButton(onClick = { onDone(false) }) { Text("Pas encore") } } },
    )
}

/** Écran « Fiabilité » : test d'appel simulé et vérifications. */
@Composable
fun ReliabilityScreen(vm: MainViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    val isDefault by vm.isDefaultDialer.collectAsState()
    var version by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { version++; vm.recheck() }
    val items = remember { Permissions.all(ctx) }
    val required = remember { items.filter { it.required } }
    val granted = remember(version, s.manualChecks) { required.count { Permissions.isGranted(ctx, it, s.manualChecks) } }
    val honor = remember { items.filter { it.kind == PermKind.MANUAL || it.id == "battery" } }
    val blockingActive = s.blockConfig.let { c -> listOf(c.telemarketing, c.hidden, c.unknown, c.blacklist).any { it != BlockAction.ALLOW } }
    val requester = rememberPermissionRequester { version++ }
    SettingsScaffold("Fiabilité", nav) {
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Test d'appel entrant", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("Affiche l'écran d'appel avec un appel simulé : glisse pour répondre, puis raccroche.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        ctx.startActivity(Intent(ctx, InCallActivity::class.java).putExtra(InCallActivity.EXTRA_PREVIEW, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Icon(Icons.Rounded.PhoneInTalk, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Lancer le test") }
                }
            }
        }
        section("État")
        item { CheckRow("Appli Téléphone par défaut", if (isDefault) "Oui" else "Non : les appels ne passent pas par Allô", isDefault) {
            Dialer.roleRequestIntent(ctx)?.let { ctx.safeStart(it) }
        } }
        item { CheckRow("Blocage du démarchage", if (blockingActive) (if (isDefault || Dialer.isScreeningRoleHeld(ctx)) "Actif" else "Configuré mais inactif (rôle requis)") else "Désactivé",
            blockingActive && (isDefault || Dialer.isScreeningRoleHeld(ctx))) { nav.navigate(com.karelisio.allo.ui.Routes.BLOCKING) } }
        item { CheckRow("Autorisations", "$granted / ${required.size} indispensables accordées", granted == required.size) { nav.navigate(com.karelisio.allo.ui.Routes.PERMISSIONS) } }
        if (isHonorDevice) {
            section("MagicOS")
            items(honor.size) { i ->
                val it2 = honor[i]
                val ok = remember(version, s.manualChecks) { Permissions.isGranted(ctx, it2, s.manualChecks) }
                CheckRow(it2.title, it2.description, if (it2.kind == PermKind.MANUAL && !ok) null else ok) { requester.request(it2) }
            }
        } else {
            item { Text("Appareil non Honor : pas de réglage MagicOS à vérifier.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun CheckRow(title: String, subtitle: String, ok: Boolean?, onClick: () -> Unit) {
    AlloListItem(
        headlineContent = { Text(title) }, supportingContent = { Text(subtitle) },
        trailingContent = { StatusIcon(ok) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

/** Choix des contacts qui affichent « Pense à toi » (Écho). */
@Composable
fun EchoSettingsScreen(vm: MainViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val contacts by vm.contacts.collectAsState()
    val s by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val installed = remember { Echo.isInstalled(ctx) }
    SettingsScaffold("Écho", nav) {
        item {
            Text(if (installed) "Choisis les contacts dont la fiche affiche le bouton « Pense à toi », qui ouvre Écho."
                else "Écho n'est pas installé : le bouton « Pense à toi » n'apparaîtra qu'une fois Écho installé.",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(contacts.size, key = { contacts[it].id }) { i ->
            val c = contacts[i]
            val on = c.lookupKey in s.echoContacts
            AlloListItem(
                leadingContent = { Avatar(c.name, c.thumbUri, size = 40.dp, key = c.name) },
                headlineContent = { Text(c.name) },
                trailingContent = { Checkbox(on, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    scope.launch { Graph.settings.update { st -> st.copy(echoContacts = if (on) st.echoContacts - c.lookupKey else st.echoContacts + c.lookupKey) } }
                }.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
fun AboutScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val state by Updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    SettingsScaffold("À propos", nav) {
        item {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("Allô", style = MaterialTheme.typography.displaySmall)
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Appli Téléphone 100 % locale : sans compte, sans serveur, sans statistiques. Seule la vérification des mises à jour contacte GitHub.")
            }
        }
        item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                when (val st = state) {
                    is UpdateState.Checking -> { Text("Recherche…"); LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
                    is UpdateState.UpToDate -> Text("Allô est à jour.")
                    is UpdateState.Available -> Text("Version ${st.release.version} disponible.")
                    is UpdateState.Error -> Text("Erreur : ${st.message}", color = MaterialTheme.colorScheme.error)
                    else -> {}
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { scope.launch { Updater.check(ctx, force = true) } }) {
                    Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Rechercher une mise à jour")
                }
                TextButton(onClick = { ctx.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.RELEASES_REPO}/releases"))) }) {
                    Text("Voir les versions sur GitHub")
                }
            }
        }
    }
    LaunchedEffect(Unit) {}
}

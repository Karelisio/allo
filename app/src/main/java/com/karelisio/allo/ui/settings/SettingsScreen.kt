package com.karelisio.allo.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.karelisio.allo.Graph
import com.karelisio.allo.MainViewModel
import com.karelisio.allo.data.AppSettings
import com.karelisio.allo.data.Backup
import com.karelisio.allo.integrations.Bulle
import com.karelisio.allo.ui.Routes
import com.karelisio.allo.util.toast
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(vm: MainViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    val sims by vm.sims.collectAsState()
    val scope = rememberCoroutineScope()
    fun upd(f: (AppSettings) -> AppSettings) = scope.launch { Graph.settings.update(f) }
    var vmDialog by remember { mutableStateOf(false) }
    var simDialog by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { ctx.toast(if (Backup.export(ctx, uri)) "Réglages exportés" else "Export impossible") }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { ctx.toast(if (Backup.import(ctx, uri)) "Réglages importés" else "Fichier invalide") }
    }

    SettingsScaffold("Réglages", nav) {
        section("Appels")
        if (sims.size > 1) item {
            val def = sims.firstOrNull { it.accountId == s.defaultAccountId }
            NavRow("SIM par défaut", def?.let { "${it.shortLabel} · ${it.label}" } ?: "Demander à chaque appel", Icons.Rounded.SimCard) { simDialog = true }
        }
        item { SwitchRow("Vibration des touches", s.keyVibration, { v -> upd { it.copy(keyVibration = v) } }, icon = Icons.Rounded.Vibration) }
        item { SwitchRow("Sons du clavier (DTMF)", s.dtmfTones, { v -> upd { it.copy(dtmfTones = v) } }, icon = Icons.AutoMirrored.Rounded.VolumeUp) }
        item { NavRow("Messagerie vocale de secours", "Utilisé si l'opérateur ne fournit pas de numéro : ${s.voicemailFallback}", Icons.Rounded.Voicemail) { vmDialog = true } }

        section("Appel entrant")
        item { SwitchRow("Notification quand l'écran est déverrouillé", s.headsUpWhenUnlocked, { v -> upd { it.copy(headsUpWhenUnlocked = v) } },
            "Sinon, l'écran d'appel s'ouvre en plein écran", Icons.Rounded.NotificationsActive) }
        item { SwitchRow("Retourner pour couper la sonnerie", s.flipToSilence, { v -> upd { it.copy(flipToSilence = v) } }, icon = Icons.Rounded.ScreenRotation) }
        item { NavRow("Réponses rapides par SMS", icon = Icons.Rounded.Sms) { nav.navigate(Routes.REPLIES) } }
        item {
            androidx.compose.material3.ListItem(
                headlineContent = { Text("« Rappelle-moi dans… »") },
                leadingContent = { androidx.compose.material3.Icon(Icons.Rounded.Timer, null) },
                supportingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15, 30, 60).forEach { m -> FilterChip(s.callbackDelayMin == m, { upd { it.copy(callbackDelayMin = m) } }, { Text("$m min") }) }
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp),
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        }

        section("Blocage et notifications")
        item { NavRow("Blocage du démarchage", "Préfixes, masqués, inconnus, listes", Icons.Rounded.Block) { nav.navigate(Routes.BLOCKING) } }
        item { SwitchRow("Notifications d'appels manqués", s.missedNotification, { v -> upd { it.copy(missedNotification = v) } }, icon = Icons.Rounded.Notifications) }

        section("Intégrations")
        item {
            SwitchRow("Laisser Bulle gérer l'appel entrant", s.bulleHandlesIncoming, { v -> upd { it.copy(bulleHandlesIncoming = v) } },
                if (Bulle.isInstalled(ctx)) "Écran déverrouillé : pas de plein écran, seulement la notification" else "Bulle n'est pas installée", Icons.Rounded.ChatBubble)
        }
        item { NavRow("Écho · « Pense à toi »", "${s.echoContacts.size} contact(s)", Icons.Rounded.Favorite) { nav.navigate(Routes.ECHO) } }

        section("Sauvegarde")
        item { NavRow("Exporter réglages et listes", "Fichier JSON", Icons.Rounded.Backup) { export.launch("allo-${LocalDate.now()}.json") } }
        item { NavRow("Importer", "Remplace les réglages et les listes", Icons.Rounded.Restore) { import.launch(arrayOf("application/json", "*/*")) } }

        section("Appli")
        item { NavRow("Autorisations", icon = Icons.Rounded.VerifiedUser) { nav.navigate(Routes.PERMISSIONS) } }
        item { NavRow("Fiabilité", "Tester l'écran d'appel, vérifier MagicOS", Icons.Rounded.Verified) { nav.navigate(Routes.RELIABILITY) } }
        item { NavRow("À propos et mises à jour", icon = Icons.Rounded.Info) { nav.navigate(Routes.ABOUT) } }
    }

    if (vmDialog) {
        var v by remember { mutableStateOf(s.voicemailFallback) }
        AlertDialog(
            onDismissRequest = { vmDialog = false },
            title = { Text("Messagerie vocale") },
            text = { OutlinedTextField(v, { v = it }, singleLine = true, label = { Text("Numéro") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)) },
            confirmButton = { TextButton(onClick = { vmDialog = false; upd { it.copy(voicemailFallback = v.trim()) } }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { vmDialog = false }) { Text("Annuler") } },
        )
    }
    if (simDialog) {
        AlertDialog(
            onDismissRequest = { simDialog = false },
            title = { Text("SIM par défaut") },
            text = {
                androidx.compose.foundation.layout.Column {
                    TextButton(onClick = { simDialog = false; upd { it.copy(defaultAccountId = "") } }) { Text("Demander à chaque appel") }
                    sims.forEach { sim -> TextButton(onClick = { simDialog = false; upd { it.copy(defaultAccountId = sim.accountId) } }) { Text("${sim.shortLabel} · ${sim.label}") } }
                }
            },
            confirmButton = {},
        )
    }
}

package com.karelisio.allo.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karelisio.allo.Graph
import com.karelisio.allo.core.PhoneNumbers
import com.karelisio.allo.data.ContactSetting
import com.karelisio.allo.data.Sim
import com.karelisio.allo.telecom.Dialer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Lance les appels depuis l'interface, avec choix de la SIM quand il le faut. */
class Caller(private val context: Context, private val scope: CoroutineScope) {
    var pending by mutableStateOf<Pending?>(null)
        private set

    data class Pending(val number: String, val lookupKey: String?, val sims: List<Sim>)

    fun call(number: String, lookupKey: String? = null) {
        if (number.isBlank()) return
        scope.launch {
            when (val c = Dialer.resolveAccount(context, lookupKey, number)) {
                is Dialer.AccountChoice.Resolved -> Dialer.place(context, number, c.handle)
                is Dialer.AccountChoice.Ask -> pending = Pending(number, lookupKey, c.sims)
            }
        }
    }

    fun callWith(number: String, sim: Sim?) = Dialer.place(context, number, sim?.handle)

    fun dismiss() { pending = null }
}

val LocalCaller = compositionLocalOf<Caller> { error("Caller absent") }

@Composable
fun rememberCaller(): Caller {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { Caller(ctx, scope) }
}

@Composable
fun CallerHost(caller: Caller) {
    val p = caller.pending ?: return
    SimPickerDialog(p.sims, p.number, p.lookupKey, onPick = { caller.dismiss(); caller.callWith(p.number, it) }, onDismiss = { caller.dismiss() })
}

@Composable
fun SimPickerDialog(sims: List<Sim>, number: String, lookupKey: String?, onPick: (Sim) -> Unit, onDismiss: () -> Unit) {
    var remember by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Appeler avec") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(PhoneNumbers.format(number), style = MaterialTheme.typography.bodyLarge)
                sims.forEach { sim ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable {
                            if (remember && lookupKey != null) scope.launch {
                                Graph.db.misc().putContactSetting(ContactSetting(lookupKey, sim.accountId))
                            }
                            onPick(sim)
                        },
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).clip(AlloShapes.cookie).background(if (sim.color != 0) Color(sim.color) else MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                Text("${sim.slot + 1}", color = Color.White, style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(sim.label, style = MaterialTheme.typography.titleMedium)
                                sim.carrier?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
                if (lookupKey != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { remember = !remember }) {
                        Checkbox(remember, { remember = it })
                        Text("Toujours utiliser pour ce contact")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

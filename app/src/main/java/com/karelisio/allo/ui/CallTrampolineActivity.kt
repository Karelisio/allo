package com.karelisio.allo.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.karelisio.allo.telecom.Dialer
import com.karelisio.allo.ui.components.SimPickerDialog
import com.karelisio.allo.ui.theme.AlloTheme
import kotlinx.coroutines.launch

/**
 * Reçoit ACTION_CALL (liens tel:, autres applis, widget, raccourcis, notifications),
 * choisit la SIM puis passe l'appel via Telecom.
 */
class CallTrampolineActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        val number = data?.schemeSpecificPart?.takeIf { it.isNotBlank() }
        if (intent?.action != Intent.ACTION_CALL || number == null) {
            // Sans numéro : on ouvre simplement le clavier.
            startActivity(Intent(Intent.ACTION_DIAL, data).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish(); return
        }
        val lookupKey = intent.getStringExtra(EXTRA_LOOKUP_KEY)
        lifecycleScope.launch {
            when (val choice = Dialer.resolveAccount(this@CallTrampolineActivity, lookupKey, number)) {
                is Dialer.AccountChoice.Resolved -> { Dialer.place(this@CallTrampolineActivity, number, choice.handle); finish() }
                is Dialer.AccountChoice.Ask -> setContent {
                    AlloTheme {
                        SimPickerDialog(
                            sims = choice.sims, number = number, lookupKey = lookupKey,
                            onPick = { sim -> Dialer.place(this@CallTrampolineActivity, number, sim.handle); finish() },
                            onDismiss = { finish() },
                        )
                    }
                }
            }
        }
    }

    companion object { const val EXTRA_LOOKUP_KEY = "lookup_key" }
}

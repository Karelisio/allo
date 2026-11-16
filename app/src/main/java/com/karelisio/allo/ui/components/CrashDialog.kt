package com.karelisio.allo.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.allo.util.CrashLog
import com.karelisio.allo.util.copyToClipboard

/** Affiche la trace du dernier crash, à copier pour le signaler. */
@Composable
fun CrashReportDialog() {
    val ctx = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.read(ctx)) }
    val r = report ?: return
    AlertDialog(
        onDismissRequest = { CrashLog.clear(ctx); report = null },
        title = { Text("Allô s'est arrêté") },
        text = {
            Text(
                r, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = { ctx.copyToClipboard("Rapport de crash Allô", r) }) { Text("Copier") } },
        dismissButton = { TextButton(onClick = { CrashLog.clear(ctx); report = null }) { Text("Fermer") } },
    )
}

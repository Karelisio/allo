package com.karelisio.allo.ui.settings

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.karelisio.allo.Graph
import com.karelisio.allo.MainViewModel
import com.karelisio.allo.core.CallEntry
import com.karelisio.allo.core.CallKind
import com.karelisio.allo.core.CallLogGrouping
import com.karelisio.allo.core.PhoneNumbers
import com.karelisio.allo.data.ContactsRepository
import com.karelisio.allo.ui.components.AlloListItem
import com.karelisio.allo.ui.components.Avatar
import com.karelisio.allo.ui.components.Cascade
import com.karelisio.allo.ui.components.EmptyIllustration
import com.karelisio.allo.ui.theme.LocalReduceMotion
import com.karelisio.allo.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Statistiques calculées localement à partir du journal. */
data class CallStats(
    val count: Int,
    val talkSec: Long,
    val incoming: Int,
    val outgoing: Int,
    val missed: Int,
    val byWeekday: List<Int>,
    val top: List<Triple<String, String?, Long>>,
) {
    companion object {
        fun from(entries: List<CallEntry>, sinceMillis: Long): CallStats {
            val e = entries.filter { it.date >= sinceMillis && it.kind != CallKind.BLOCKED }
            val zone = ZoneId.systemDefault()
            val days = IntArray(7)
            e.forEach { days[Instant.ofEpochMilli(it.date).atZone(zone).dayOfWeek.value - 1]++ }
            val top = e.filter { !PhoneNumbers.isHidden(it.number) && it.durationSec > 0 }
                .groupBy { it.key }.values
                .map { calls -> Triple(calls.first().name ?: PhoneNumbers.format(calls.first().number), calls.first().photoUri, calls.sumOf { it.durationSec }) }
                .sortedByDescending { it.third }.take(5)
            return CallStats(
                count = e.size, talkSec = e.sumOf { it.durationSec },
                incoming = e.count { it.kind == CallKind.INCOMING }, outgoing = e.count { it.kind == CallKind.OUTGOING },
                missed = e.count { it.kind == CallKind.MISSED || it.kind == CallKind.REJECTED },
                byWeekday = days.toList(), top = top,
            )
        }
    }
}

private enum class Period(val label: String) { MONTH("Ce mois"), DAYS30("30 jours"), YEAR("Cette année"), ALL("Tout") }

@Composable
fun StatsScreen(vm: MainViewModel, nav: NavHostController) {
    val entries by vm.callLog.collectAsState()
    var period by remember { mutableStateOf(Period.MONTH) }
    val since = remember(period) {
        val today = LocalDate.now()
        val d = when (period) {
            Period.MONTH -> today.withDayOfMonth(1)
            Period.DAYS30 -> today.minusDays(29)
            Period.YEAR -> today.withDayOfYear(1)
            Period.ALL -> LocalDate.of(1970, 1, 1)
        }
        d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val stats = remember(entries, since) { CallStats.from(entries, since) }
    SettingsScaffold("Statistiques", nav) {
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Period.entries.forEach { p -> FilterChip(period == p, { period = p }, { Text(p.label) }) }
            }
        }
        if (stats.count == 0) {
            item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { EmptyIllustration(Icons.Rounded.Groups, "Pas encore d'appels sur cette période") } }
            return@SettingsScaffold
        }
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroTile("${stats.count}", if (stats.count > 1) "appels" else "appel", Modifier.weight(1f))
                HeroTile(CallLogGrouping.formatDuration(stats.talkSec).ifEmpty { "0 s" }, "au téléphone", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallTile(Icons.AutoMirrored.Rounded.CallReceived, "${stats.incoming}", "entrants", Modifier.weight(1f))
                SmallTile(Icons.AutoMirrored.Rounded.CallMade, "${stats.outgoing}", "sortants", Modifier.weight(1f))
                SmallTile(Icons.AutoMirrored.Rounded.CallMissed, "${stats.missed}", "manqués", Modifier.weight(1f))
            }
        }
        section("Appels par jour de la semaine")
        item { WeekdayBars(stats.byWeekday, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) }
        if (stats.top.isNotEmpty()) {
            section("Temps passé avec")
            val max = stats.top.maxOf { it.third }.coerceAtLeast(1)
            stats.top.forEachIndexed { i, (name, photo, sec) ->
                item(key = "top$i") { Cascade(i) { TopRow(name, photo, sec, sec.toFloat() / max) } }
            }
        }
    }
}

@Composable
private fun HeroTile(value: String, label: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun SmallTile(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Barres fines à une seule série, coins de 4 dp côté données, valeur au toucher. */
@Composable
private fun WeekdayBars(values: List<Int>, modifier: Modifier) {
    val reduce = LocalReduceMotion.current
    val grow = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(values) { if (!reduce) { grow.snapTo(0f); grow.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 120f)) } }
    var selected by remember { mutableIntStateOf(-1) }
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val bar = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val names = DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.FRENCH).removeSuffix(".") }
    Column(modifier.semantics { contentDescription = "Appels par jour : " + names.zip(values).joinToString { "${it.first} ${it.second}" } }) {
        Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
            if (selected >= 0) Text("${names[selected]} : ${values[selected]} appel${if (values[selected] > 1) "s" else ""}", style = MaterialTheme.typography.labelLarge)
        }
        Canvas(Modifier.fillMaxWidth().height(140.dp).pointerInput(values) {
            detectTapGestures { o -> val slot = size.width / 7f; selected = (o.x / slot).toInt().coerceIn(0, 6).let { if (it == selected) -1 else it } }
        }) {
            val slot = size.width / 7f
            val w = (slot * 0.42f).coerceAtMost(28.dp.toPx())
            val r = 4.dp.toPx()
            values.forEachIndexed { i, v ->
                val h = (size.height - 2.dp.toPx()) * (v.toFloat() / max) * grow.value
                if (h <= 0f) return@forEachIndexed
                val left = slot * i + (slot - w) / 2f
                val top = size.height - h
                // Coins arrondis en haut seulement, posé sur la ligne de base.
                val p = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + w, size.height,
                        topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r)))
                }
                drawPath(p, if (selected < 0 || selected == i) bar else muted)
            }
            drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth()) {
            names.forEach { n -> Text(n, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

@Composable
private fun TopRow(name: String, photo: String?, sec: Long, fraction: Float) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, photo, size = 40.dp, key = name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(CallLogGrouping.formatDuration(sec), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Bar(fraction)
        }
    }
}

@Composable
private fun Bar(fraction: Float) {
    val c = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        drawRoundRect(c, size = Size(size.width * fraction.coerceIn(0.03f, 1f), size.height), cornerRadius = CornerRadius(3.dp.toPx()))
    }
}

/** Sonnerie appliquée à tous les membres d'un groupe de contacts. */
@Composable
fun GroupRingtoneScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<ContactsRepository.ContactGroup>?>(null) }
    var target by remember { mutableStateOf<ContactsRepository.ContactGroup?>(null) }
    LaunchedEffect(Unit) { groups = withContext(Dispatchers.IO) { Graph.contacts.groups() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val g = target ?: return@rememberLauncherForActivityResult
        if (res.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            else res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            scope.launch { val n = Graph.contacts.setGroupRingtone(g.title, uri?.toString()); ctx.toast("Sonnerie appliquée à $n contact(s)") }
        }
    }
    SettingsScaffold("Sonnerie par groupe", nav) {
        item {
            Text("Choisis une sonnerie pour tous les contacts d'un groupe. Elle est enregistrée sur chaque contact : la vibration reste celle du système.",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val list = groups
        if (list != null && list.isEmpty()) item {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                EmptyIllustration(Icons.Rounded.Groups, "Aucun groupe", "Crée des groupes (libellés) dans tes contacts Google pour les retrouver ici.")
            }
        }
        list.orEmpty().forEachIndexed { i, g ->
            item(key = "g${g.id}") {
                Cascade(i) {
                    AlloListItem(
                        headlineContent = { Text(g.title) },
                        supportingContent = { Text("${g.count} contact${if (g.count > 1) "s" else ""}") },
                        leadingContent = { Icon(Icons.Rounded.Groups, null) },
                        trailingContent = {
                            TextButton(onClick = { scope.launch { val n = Graph.contacts.setGroupRingtone(g.title, null); ctx.toast("Sonnerie par défaut rétablie ($n)") } }) { Text("Réinitialiser") }
                        },
                        modifier = Modifier.clickable {
                            target = g
                            picker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sonnerie pour ${g.title}")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            })
                        }.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

package com.karelisio.allo.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.karelisio.allo.Graph
import com.karelisio.allo.MainActivity
import com.karelisio.allo.core.CallKind
import com.karelisio.allo.core.PhoneNumbers
import com.karelisio.allo.ui.CallTrampolineActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class RecentRow(val title: String, val subtitle: String, val number: String, val missed: Boolean, val bitmap: android.graphics.Bitmap)

/** Widget des derniers appels (manqués en évidence), un toucher pour rappeler. */
class RecentCallsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rows = withContext(Dispatchers.IO) {
            Graph.init(context)
            val px = (40 * context.resources.displayMetrics.density).toInt()
            val hm = DateTimeFormatter.ofPattern("HH:mm")
            Graph.callLog.query(60).filter { !PhoneNumbers.isHidden(it.number) && it.kind != CallKind.BLOCKED }
                .distinctBy { it.key }.take(6).map { e ->
                    val c = Graph.contacts.lookup(e.number)
                    val title = c?.name ?: PhoneNumbers.format(e.number)
                    val kind = when (e.kind) { CallKind.MISSED, CallKind.REJECTED -> "Manqué"; CallKind.OUTGOING -> "Sortant"; else -> "Entrant" }
                    RecentRow(title, "$kind · ${hm.format(Instant.ofEpochMilli(e.date).atZone(ZoneId.systemDefault()))}", e.number,
                        e.kind == CallKind.MISSED, AvatarBitmaps.render(context, c?.name ?: e.number, c?.photoUri, px))
                }
        }
        provideContent { GlanceTheme { Content(context, rows) } }
    }

    @Composable
    private fun Content(context: Context, rows: List<RecentRow>) {
        val max = ((LocalSize.current.height.value - 40f) / 52f).toInt().coerceIn(1, 6)
        val radius = if (Build.VERSION.SDK_INT >= 31) GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius) else GlanceModifier.cornerRadius(24.dp)
        Column(GlanceModifier.fillMaxSize().then(radius).background(GlanceTheme.colors.widgetBackground).padding(12.dp)) {
            Text("Récents", style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.padding(start = 4.dp, bottom = 6.dp)
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_LOG))))
            if (rows.isEmpty()) Text("Aucun appel récent", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
            rows.take(max).forEach { r ->
                Row(
                    GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(actionStartActivity(
                        Intent(context, CallTrampolineActivity::class.java).setAction(Intent.ACTION_CALL).setData(Uri.fromParts("tel", r.number, null)))),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(ImageProvider(r.bitmap), r.title, GlanceModifier.size(40.dp))
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(r.title, maxLines = 1, style = TextStyle(color = if (r.missed) GlanceTheme.colors.error else GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                        Text(r.subtitle, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                    }
                }
            }
        }
    }

    companion object {
        /** Rafraîchit le widget après un appel (appelé depuis le service d'appel et les notifications). */
        fun refresh(context: Context) { Graph.scope.launch { runCatching { RecentCallsWidget().updateAll(context) } } }
    }
}

class RecentCallsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentCallsWidget()
}

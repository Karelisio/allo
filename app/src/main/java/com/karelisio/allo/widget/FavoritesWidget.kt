package com.karelisio.allo.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.karelisio.allo.Graph
import com.karelisio.allo.MainActivity
import com.karelisio.allo.ui.CallTrampolineActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class WidgetFav(val name: String, val number: String, val lookupKey: String, val bitmap: android.graphics.Bitmap)

/** Widget de favoris (2x2 à 4x4) : avatars cliquables pour appeler directement. */
class FavoritesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val favs = withContext(Dispatchers.IO) {
            Graph.init(context)
            val px = (64 * context.resources.displayMetrics.density).toInt()
            Graph.contacts.all().filter { it.starred && it.primaryNumber != null }.take(16).map {
                WidgetFav(it.name, it.primaryNumber!!, it.lookupKey, AvatarBitmaps.render(context, it.name, it.thumbUri ?: it.photoUri, px))
            }
        }
        provideContent { GlanceTheme { Content(context, favs) } }
    }

    @Composable
    private fun Content(context: Context, favs: List<WidgetFav>) {
        val size = LocalSize.current
        val cols = (size.width.value / 80f).toInt().coerceIn(2, 4)
        val rows = (size.height.value / 92f).toInt().coerceIn(1, 4)
        val shown = favs.take(cols * rows)
        val radius = if (Build.VERSION.SDK_INT >= 31) GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius) else GlanceModifier.cornerRadius(24.dp)
        Column(
            GlanceModifier.fillMaxSize().then(radius).background(GlanceTheme.colors.widgetBackground).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (shown.isEmpty()) {
                Text("Ajoute des favoris dans Allô", style = TextStyle(color = GlanceTheme.colors.onSurface, textAlign = TextAlign.Center),
                    modifier = GlanceModifier.clickable(actionStartActivity(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_FAVORITES))))
                return@Column
            }
            shown.chunked(cols).forEach { row ->
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    row.forEach { f ->
                        Column(
                            GlanceModifier.defaultWeight().clickable(actionStartActivity(
                                Intent(context, CallTrampolineActivity::class.java).setAction(Intent.ACTION_CALL)
                                    .setData(Uri.fromParts("tel", f.number, null))
                                    .putExtra(CallTrampolineActivity.EXTRA_LOOKUP_KEY, f.lookupKey),
                            )),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(ImageProvider(f.bitmap), f.name, GlanceModifier.size(52.dp))
                            Spacer(GlanceModifier.height(2.dp))
                            Text(f.name.substringBefore(' '), maxLines = 1,
                                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center))
                        }
                    }
                    repeat(cols - row.size) { Box(GlanceModifier.defaultWeight()) {} }
                }
            }
        }
    }
}

class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavoritesWidget()
}

@Suppress("unused") private val previewSize = DpSize(160.dp, 160.dp)

package com.karelisio.allo.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.glance.appwidget.updateAll
import com.karelisio.allo.data.Contact
import com.karelisio.allo.ui.CallTrampolineActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Raccourcis d'appli (appui long sur l'icône) vers les 4 premiers favoris, et rafraîchissement du widget. */
object Shortcuts {
    private var lastKeys: List<String>? = null

    suspend fun update(ctx: Context, favorites: List<Contact>) = withContext(Dispatchers.IO) { runCatching { doUpdate(ctx, favorites) }; Unit }

    private suspend fun doUpdate(ctx: Context, favorites: List<Contact>) {
        val top = favorites.filter { it.primaryNumber != null }.take(4)
        val keys = top.map { it.lookupKey + it.photoUri + it.name }
        if (keys == lastKeys) return
        lastKeys = keys
        val px = (108 * ctx.resources.displayMetrics.density).toInt()
        val list = top.mapIndexed { i, c ->
            ShortcutInfoCompat.Builder(ctx, "fav-${c.lookupKey}")
                .setShortLabel(c.name.trim().substringBefore(' ').ifBlank { c.name.ifBlank { c.primaryNumber ?: "?" } })
                .setLongLabel("Appeler ${c.name}")
                .setIcon(IconCompat.createWithAdaptiveBitmap(AvatarBitmaps.render(ctx, c.name, c.photoUri, px, adaptive = true)))
                .setIntent(Intent(ctx, CallTrampolineActivity::class.java).setAction(Intent.ACTION_CALL)
                    .setData(Uri.fromParts("tel", c.primaryNumber, null))
                    .putExtra(CallTrampolineActivity.EXTRA_LOOKUP_KEY, c.lookupKey))
                .setRank(i)
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(ctx, list) }
        runCatching { FavoritesWidget().updateAll(ctx) }
    }
}

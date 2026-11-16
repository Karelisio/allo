package com.karelisio.allo.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.karelisio.allo.notifications.Notifications
import com.karelisio.allo.ui.components.initialsOf

/** Rendu d'avatars en bitmap (widget, raccourcis) avec les mêmes formes MaterialShapes. */
object AvatarBitmaps {
    private val polygons: List<RoundedPolygon> by lazy {
        listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Cookie12Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny,
            MaterialShapes.Pentagon, MaterialShapes.Gem, MaterialShapes.Puffy, MaterialShapes.Cookie7Sided,
            MaterialShapes.SoftBurst, MaterialShapes.Arch, MaterialShapes.Circle, MaterialShapes.Clover8Leaf)
    }

    private fun colors(ctx: Context, key: String): Pair<Int, Int> {
        val pal = if (Build.VERSION.SDK_INT >= 31) listOf(
            android.R.color.system_accent1_200 to android.R.color.system_accent1_900,
            android.R.color.system_accent2_200 to android.R.color.system_accent2_900,
            android.R.color.system_accent3_200 to android.R.color.system_accent3_900,
            android.R.color.system_accent1_600 to android.R.color.system_accent1_10,
        ).map { ctx.getColor(it.first) to ctx.getColor(it.second) }
        else listOf(0xFFA6F4B8.toInt() to 0xFF00210D.toInt(), 0xFFD1E8D4.toInt() to 0xFF0C1F13.toInt(), 0xFFBEEAF6.toInt() to 0xFF001F26.toInt())
        return pal[((key.hashCode() / 7) and 0x7fffffff) % pal.size]
    }

    fun shapePath(key: String, size: Float, inset: Float = 0f): Path {
        val poly = polygons[(key.hashCode() and 0x7fffffff) % polygons.size]
        val p = poly.toPath()
        val m = Matrix().apply { setScale(size - 2 * inset, size - 2 * inset); postTranslate(inset, inset) }
        p.transform(m)
        return p
    }

    /** [adaptive] : laisse la marge des icônes adaptatives (108 dp, contenu dans 72 dp). */
    fun render(ctx: Context, name: String?, photoUri: String?, sizePx: Int, adaptive: Boolean = false): Bitmap {
        val key = name ?: "?"
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val (bg, fg) = colors(ctx, key)
        val inset = if (adaptive) sizePx * 18f / 108f else 0f
        if (adaptive) c.drawColor(bg)
        val path = shapePath(key, sizePx.toFloat(), inset)
        val photo = Notifications.loadBitmap(ctx, photoUri, sizePx)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (photo != null) {
            val side = minOf(photo.width, photo.height)
            val sq = Bitmap.createBitmap(photo, (photo.width - side) / 2, (photo.height - side) / 2, side, side)
            val shader = BitmapShader(sq, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val target = sizePx - 2 * inset
            shader.setLocalMatrix(Matrix().apply { setScale(target / side, target / side); postTranslate(inset, inset) })
            paint.shader = shader
            c.drawPath(path, paint)
        } else {
            paint.color = if (adaptive) fg else bg
            if (!adaptive) c.drawPath(path, paint)
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (adaptive) bg else fg
                if (adaptive) { color = fg }
                textAlign = Paint.Align.CENTER
                textSize = (sizePx - 2 * inset) * 0.4f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val initials = initialsOf(name).ifEmpty { "?" }
            c.drawText(initials, sizePx / 2f, sizePx / 2f - (t.descent() + t.ascent()) / 2f, t)
        }
        return bmp
    }
}

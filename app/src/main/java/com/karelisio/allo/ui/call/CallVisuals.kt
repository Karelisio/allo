package com.karelisio.allo.ui.call

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.graphics.shapes.Morph
import coil3.compose.AsyncImage
import com.karelisio.allo.core.CallLogGrouping
import com.karelisio.allo.ui.theme.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.sin

private val waveMorphs by lazy {
    listOf(
        Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle),
        Morph(MaterialShapes.Sunny, MaterialShapes.Cookie12Sided),
        Morph(MaterialShapes.Clover4Leaf, MaterialShapes.SoftBurst),
    )
}

/**
 * Ondes d'appel : anneaux concentriques aux formes M3 qui se déforment en partant de l'avatar,
 * au rythme de la sonnerie. [intensity] (0..1) accélère les ondes quand le doigt approche du décroché.
 */
@Composable
fun CallWaves(color: Color, intensity: () -> Float, modifier: Modifier = Modifier, innerFraction: Float = 0.5f) {
    val reduce = LocalReduceMotion.current
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduce) {
        if (reduce) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) {
                    val dt = (t - last) / 1_000_000_000f
                    val period = 2.1f * (1f - 0.7f * intensity().coerceIn(0f, 1f))
                    phase.floatValue += dt / period
                }
                last = t
            }
        }
    }
    val path = remember { Path() }
    val m = remember { Matrix() }
    Canvas(modifier) {
        val rings = 3
        val p0 = phase.floatValue
        for (i in 0 until rings) {
            val p = ((p0 + i / rings.toFloat()) % 1f)
            val frac = lerp(innerFraction, 1f, FastOutSlowInEasing.transform(p))
            val ringSize = size.minDimension * frac
            val morphProgress = (sin((p0 * 2f + i) * PI.toFloat()) + 1f) / 2f
            path.reset()
            waveMorphs[i % waveMorphs.size].toPath(morphProgress, path)
            m.reset()
            m.translate((size.width - ringSize) / 2f, (size.height - ringSize) / 2f)
            m.scale(ringSize, ringSize)
            path.transform(m)
            val alpha = (1f - p) * if (reduce) 0.25f else 0.55f
            rotate(degrees = p0 * 40f + i * 37f) {
                drawPath(path, color.copy(alpha = alpha * 0.35f))
                drawPath(path, color.copy(alpha = alpha), style = Stroke(width = 2.dp.toPx() * (1.5f - p)))
            }
        }
    }
}

/** Fond de l'écran d'appel : photo floutée si disponible, sinon dégradé de couleurs dynamiques. */
@Composable
fun CallBackground(photoUri: String?, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "bg")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "shift")
    Box(modifier.fillMaxSize().background(s.surfaceContainerLowest)) {
        if (photoUri != null && Build.VERSION.SDK_INT >= 31) {
            AsyncImage(photoUri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(70.dp))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(0.35f), Color.Black.copy(0.65f)))))
        } else {
            val k = if (reduce) 0.5f else shift
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(
                0f to s.primaryContainer.copy(alpha = 0.9f),
                lerp(0.35f, 0.65f, k) to s.surfaceContainerLow,
                1f to s.tertiaryContainer.copy(alpha = 0.7f),
            )))
        }
    }
}

/** Durée d'appel en compteur animé : chaque chiffre glisse quand il change. */
@Composable
fun AnimatedTimer(seconds: Long, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val text = CallLogGrouping.formatClock(seconds)
    val reduce = LocalReduceMotion.current
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            if (reduce || !ch.isDigit()) {
                Text(ch.toString(), style = style, color = color)
            } else {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        (slideInVertically(spring(dampingRatio = 0.6f, stiffness = 400f)) { -it } + fadeIn()) togetherWith
                            (slideOutVertically(spring(stiffness = 400f)) { it } + fadeOut())
                    },
                    label = "digit$i",
                ) { c -> Text(c.toString(), style = style, color = color) }
            }
        }
    }
}

/**
 * Forme de fin d'appel : l'écran se replie en pilule ([p] 0 → 0.7) puis la pilule file vers la caméra
 * et disparaît (0.7 → 1).
 */
class EndCallShape(private val p: Float, private val density: Density) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val shrink = (p / 0.7f).coerceIn(0f, 1f)
        val fly = ((p - 0.7f) / 0.3f).coerceIn(0f, 1f)
        val e = FastOutSlowInEasing.transform(shrink)
        val pillW = with(this.density) { 120.dp.toPx() }
        val pillH = with(this.density) { 36.dp.toPx() }
        val topInset = with(this.density) { 14.dp.toPx() }
        val w = lerp(size.width, pillW, e)
        val h = lerp(size.height, pillH, e)
        val left = (size.width - w) / 2f
        val top = lerp(0f, topInset, e) - fly * (pillH + topInset)
        val r = lerp(with(this.density) { 28.dp.toPx() }, h / 2f, e)
        return Outline.Rounded(RoundRect(left, top, left + w, top + h, CornerRadius(r)))
    }
}

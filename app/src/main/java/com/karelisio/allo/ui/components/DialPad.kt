package com.karelisio.allo.ui.components

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.allo.core.T9
import com.karelisio.allo.ui.theme.AlloType
import com.karelisio.allo.ui.theme.LocalReduceMotion

/**
 * Touche « vivante » : rebondit au ressort et passe du cercle à la fleur sous le doigt.
 */
@Composable
fun DialKey(
    digit: Char,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    size: Dp = 76.dp,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
) {
    val reduce = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val morph by animateFloatAsState(
        if (pressed && !reduce) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = 520f), label = "morph",
    )
    val scale by animateFloatAsState(
        if (pressed && !reduce) 0.88f else 1f,
        spring(dampingRatio = 0.35f, stiffness = 650f), label = "scale",
    )
    val shape = remember(morph) { MorphShape(AlloShapes.keyMorph, morph.coerceIn(0f, 1f), rotation = morph * 22f) }
    val pressedColor = MaterialTheme.colorScheme.primaryContainer
    val bg = androidx.compose.ui.graphics.lerp(container, pressedColor, morph.coerceIn(0f, 1f))
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg)
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics { contentDescription = digit.toString() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digit.toString(), style = AlloType.dialDigit, color = content)
            val letters = T9.letters[digit].orEmpty()
            when {
                digit == '1' -> Icon(Icons.Rounded.Voicemail, null, Modifier.size(14.dp), tint = content.copy(alpha = 0.7f))
                letters.isNotEmpty() -> Text(letters, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.7f))
                else -> Box(Modifier.height(14.dp))
            }
        }
    }
}

val DIAL_ROWS = listOf("123", "456", "789", "*0#")

@Composable
fun DialPad(
    onKey: (Char) -> Unit,
    modifier: Modifier = Modifier,
    onLongKey: (Char) -> Boolean = { false },
    keySize: Dp = 76.dp,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DIAL_ROWS.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { d ->
                    DialKey(
                        digit = d,
                        onClick = { onKey(d) },
                        onLongClick = if (d == '0' || d == '1' || d == '*' || d == '#') ({ if (!onLongKey(d)) onKey(d) }) else null,
                        size = keySize,
                        container = container,
                        content = content,
                    )
                }
            }
        }
    }
}

/** Sons DTMF locaux du clavier (optionnels). */
@Composable
fun rememberDtmfPlayer(enabled: Boolean): (Char) -> Unit {
    val gen = remember(enabled) { if (enabled) runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 70) }.getOrNull() else null }
    DisposableEffect(gen) { onDispose { gen?.release() } }
    return remember(gen) {
        { c: Char ->
            val tone = when (c) {
                in '0'..'9' -> ToneGenerator.TONE_DTMF_0 + (c - '0')
                '*' -> ToneGenerator.TONE_DTMF_S
                '#' -> ToneGenerator.TONE_DTMF_P
                else -> -1
            }
            if (tone >= 0) gen?.startTone(tone, 120)
        }
    }
}

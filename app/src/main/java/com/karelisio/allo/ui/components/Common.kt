package com.karelisio.allo.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.karelisio.allo.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay

/** Pastille de la SIM (1 / 2). */
@Composable
fun SimBadge(slot: Int?, modifier: Modifier = Modifier) {
    if (slot == null) return
    Box(
        modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape).padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text("${slot + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** Retours haptiques subtils. */
class Haptics(private val view: android.view.View) {
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    fun key() = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    fun confirm() = view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
    fun reject() = view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    fun long() = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}

@Composable
fun rememberHaptics(): Haptics {
    val v = LocalView.current
    return remember(v) { Haptics(v) }
}

/** Apparition en cascade d'un élément de liste (index → délai). */
@Composable
fun Cascade(index: Int, modifier: Modifier = Modifier, baseDelay: Long = 28, content: @Composable () -> Unit) {
    val reduce = LocalReduceMotion.current
    var visible by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) {
        if (!reduce) { delay((index.coerceAtMost(12)) * baseDelay); visible = true }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
            slideInVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) { it / 3 },
    ) { content() }
}

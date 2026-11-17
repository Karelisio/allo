package com.karelisio.allo.ui.components

import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Transition partagée de l'avatar (journal / favoris → fiche contact). */
@Composable
fun Modifier.sharedAvatar(key: String?): Modifier {
    if (key == null) return this
    val s = LocalSharedTransitionScope.current ?: return this
    val a = LocalNavAnimatedScope.current ?: return this
    return with(s) { this@sharedAvatar.sharedElement(rememberSharedContentState("avatar-$key"), a) }
}

@Composable
fun Modifier.sharedBoundsKey(key: String): Modifier {
    val s = LocalSharedTransitionScope.current ?: return this
    val a = LocalNavAnimatedScope.current ?: return this
    return with(s) { this@sharedBoundsKey.sharedBounds(rememberSharedContentState(key), a) }
}

/** Clé partagée du nom (liste → fiche contact), animée comme un bloc de texte. */
@Composable
fun Modifier.sharedName(key: String?): Modifier {
    if (key == null) return this
    val s = LocalSharedTransitionScope.current ?: return this
    val a = LocalNavAnimatedScope.current ?: return this
    return with(s) { this@sharedName.sharedBounds(rememberSharedContentState("name-$key"), a, resizeMode = androidx.compose.animation.SharedTransitionScope.ResizeMode.scaleToBounds()) }
}

/** Léger rebond au ressort quand on appuie (à partager avec le clickable via [interaction]). */
@Composable
fun Modifier.pressBounce(interaction: androidx.compose.foundation.interaction.InteractionSource, scale: Float = 0.97f): Modifier {
    if (com.karelisio.allo.ui.theme.LocalReduceMotion.current) return this
    val pressed by interaction.collectIsPressedAsState()
    val v by androidx.compose.animation.core.animateFloatAsState(if (pressed) scale else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    return this.graphicsLayer { scaleX = v; scaleY = v }
}

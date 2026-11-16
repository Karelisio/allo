package com.karelisio.allo.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

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

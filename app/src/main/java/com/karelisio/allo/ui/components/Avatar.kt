package com.karelisio.allo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

data class AvatarColors(val container: Color, val content: Color)

@Composable
fun avatarColors(key: String): AvatarColors {
    val s = MaterialTheme.colorScheme
    val palette = listOf(
        AvatarColors(s.primaryContainer, s.onPrimaryContainer),
        AvatarColors(s.secondaryContainer, s.onSecondaryContainer),
        AvatarColors(s.tertiaryContainer, s.onTertiaryContainer),
        AvatarColors(s.primary, s.onPrimary),
        AvatarColors(s.tertiary, s.onTertiary),
        AvatarColors(s.secondary, s.onSecondary),
    )
    return palette[((key.hashCode() / 7) and 0x7fffffff) % palette.size]
}

fun initialsOf(name: String?): String {
    if (name.isNullOrBlank()) return ""
    val words = name.trim().split(Regex("\\s+")).filter { it.firstOrNull()?.isLetter() == true }
    return when {
        words.isEmpty() -> ""
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/**
 * Avatar : photo du contact ou initiales sur une forme MaterialShapes colorée,
 * forme et couleur dérivées du nom (stables d'un écran à l'autre).
 */
@Composable
fun Avatar(
    name: String?,
    photoUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    key: String = name ?: "?",
    shape: Shape = remember(key) { AlloShapes.forKey(key) },
) {
    val colors = avatarColors(key)
    Box(
        modifier.size(size).clip(shape).background(colors.container),
        contentAlignment = Alignment.Center,
    ) {
        val initials = initialsOf(name)
        if (initials.isNotEmpty()) {
            Text(initials, color = colors.content, fontSize = (size.value * 0.38f).sp, style = MaterialTheme.typography.titleMedium)
        } else {
            Icon(Icons.Rounded.Person, null, tint = colors.content, modifier = Modifier.size(size * 0.55f))
        }
        if (photoUri != null) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(shape),
            )
        }
    }
}

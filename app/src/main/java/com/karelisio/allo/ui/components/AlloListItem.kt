package com.karelisio.allo.ui.components

import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Ligne de liste unique de l'appli (fond transparent par défaut).
 * Centralise l'API ListItem de Material 3, dont la signature évolue avec la version Expressive.
 */
@Suppress("DEPRECATION")
@Composable
fun AlloListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent),
) {
    ListItem(
        headlineContent = headlineContent,
        modifier = modifier,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = colors,
    )
}

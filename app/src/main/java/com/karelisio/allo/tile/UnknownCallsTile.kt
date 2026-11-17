package com.karelisio.allo.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.karelisio.allo.Graph
import com.karelisio.allo.core.BlockAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tuile des réglages rapides : les numéros inconnus sonnent en silence (avec étiquette) ou normalement. */
class UnknownCallsTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        Graph.scope.launch {
            Graph.settings.update { s ->
                val next = if (s.blockConfig.unknown == BlockAction.ALLOW) BlockAction.SILENCE else BlockAction.ALLOW
                s.copy(blockConfig = s.blockConfig.copy(unknown = next))
            }
            refresh()
        }
    }

    private fun refresh() {
        Graph.scope.launch {
            val action = Graph.settings.current().blockConfig.unknown
            withContext(Dispatchers.Main) {
                val t = qsTile ?: return@withContext
                t.state = if (action == BlockAction.ALLOW) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
                t.label = "Inconnus"
                t.subtitle = when (action) { BlockAction.ALLOW -> "Sonnent"; BlockAction.SILENCE -> "En silence"; BlockAction.BLOCK -> "Bloqués" }
                t.updateTile()
            }
        }
    }
}

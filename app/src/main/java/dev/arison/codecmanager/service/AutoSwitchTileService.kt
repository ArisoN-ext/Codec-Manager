package dev.arison.codecmanager.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.arison.codecmanager.CodecApp
import dev.arison.codecmanager.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutoSwitchTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartListening() {
        val app = application as? CodecApp ?: return
        scope.launch {
            val enabled = app.container.settings.settings.first().autoSwitch
            updateTile(enabled)
        }
    }

    override fun onClick() {
        val app = application as? CodecApp ?: return
        scope.launch {
            val current = app.container.settings.settings.first().autoSwitch
            val next = !current
            app.container.settings.setAutoSwitch(next)
            if (next) {
                CodecForegroundService.start(this@AutoSwitchTileService)
            } else {
                CodecForegroundService.stop(this@AutoSwitchTileService)
            }
            updateTile(next)
        }
    }

    private suspend fun updateTile(enabled: Boolean) {
        withContext(Dispatchers.Main) {
            val tile = qsTile ?: return@withContext
            tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_label)
            tile.subtitle = getString(if (enabled) R.string.tile_on else R.string.tile_off)
            tile.updateTile()
        }
    }
}

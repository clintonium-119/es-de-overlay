package com.clintonium.es_de_overlay

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings toggle: on starts the service (capture consent is asked on the first capture), off stops it. */
class CaptureTile : TileService() {
    // Android can't be asked whether the tile is added, so settings reads what these callbacks saw.
    override fun onTileAdded() {
        Prefs(this).tileAdded = true
    }

    override fun onTileRemoved() {
        Prefs(this).tileAdded = false
    }

    override fun onStartListening() {
        Prefs(this).tileAdded = true
        show(HelperService.running)
    }

    override fun onClick() {
        if (HelperService.running) {
            stopService(Intent(this, HelperService::class.java))
            show(false)
        } else {
            // Android can refuse a start from the background; settings can always start it.
            runCatching { startForegroundService(Intent(this, HelperService::class.java)) }
                .onSuccess { show(true) }
                .onFailure { startActivityAndCollapse(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    private fun show(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}

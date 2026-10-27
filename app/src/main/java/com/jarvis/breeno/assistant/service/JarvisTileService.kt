package com.jarvis.breeno.assistant.service
import android.service.quicksettings.TileService
class JarvisTileService:TileService(){override fun onClick(){super.onClick(); qsTile?.apply{state=android.service.quicksettings.Tile.STATE_ACTIVE;updateTile()}}}

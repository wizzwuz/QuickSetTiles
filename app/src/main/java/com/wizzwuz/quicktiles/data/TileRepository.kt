package com.wizzwuz.quicktiles.data

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.service.quicksettings.TileService
import com.wizzwuz.quicktiles.model.TileItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class TileRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("quick_tiles_prefs", Context.MODE_PRIVATE)

    private val _tilesFlow = MutableStateFlow<List<TileItem>>(emptyList())
    val tilesFlow: StateFlow<List<TileItem>> = _tilesFlow.asStateFlow()

    init {
        loadTiles()
    }

    @Synchronized
    fun loadTiles(): List<TileItem> {
        val raw = prefs.getString(KEY_TILES, null) ?: "[]"
        val list = mutableListOf<TileItem>()
        try {
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(TileItem.fromJson(obj))
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        _tilesFlow.value = list
        return list
    }

    @Synchronized
    private fun persistTiles(list: List<TileItem>) {
        val jsonArray = JSONArray()
        for (item in list) {
            jsonArray.put(item.toJson())
        }
        prefs.edit().putString(KEY_TILES, jsonArray.toString()).apply()
        _tilesFlow.value = list
    }

    fun getActiveTiles(): List<TileItem> {
        return _tilesFlow.value.filter { it.isActive }
    }

    fun getInactiveTiles(): List<TileItem> {
        return _tilesFlow.value.filter { !it.isActive }
    }

    fun getTileBySlot(slot: Int): TileItem? {
        return _tilesFlow.value.find { it.isActive && it.slotNumber == slot }
    }

    fun getTileById(id: String): TileItem? {
        return _tilesFlow.value.find { it.id == id }
    }

    @Synchronized
    fun saveTile(tile: TileItem) {
        val current = _tilesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == tile.id }

        val finalTile = if (tile.isActive) {
            val assignedSlot = if (tile.slotNumber in 1..MAX_SLOTS) {
                tile.slotNumber
            } else {
                findNextAvailableSlot(current.filter { it.id != tile.id })
            }
            tile.copy(slotNumber = assignedSlot)
        } else {
            tile.copy(slotNumber = -1)
        }

        if (index >= 0) {
            current[index] = finalTile
        } else {
            current.add(finalTile)
        }

        persistTiles(current)

        if (finalTile.slotNumber in 1..MAX_SLOTS) {
            requestTileUpdate(context, finalTile.slotNumber)
        }
    }

    @Synchronized
    fun inactivateTile(tileId: String) {
        val current = _tilesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == tileId }
        if (index >= 0) {
            val oldSlot = current[index].slotNumber
            current[index] = current[index].copy(isActive = false, slotNumber = -1)
            persistTiles(current)
            if (oldSlot in 1..MAX_SLOTS) {
                requestTileUpdate(context, oldSlot)
            }
        }
    }

    @Synchronized
    fun activateTile(tileId: String) {
        val current = _tilesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == tileId }
        if (index >= 0) {
            val freeSlot = findNextAvailableSlot(current)
            current[index] = current[index].copy(isActive = true, slotNumber = freeSlot)
            persistTiles(current)
            if (freeSlot in 1..MAX_SLOTS) {
                requestTileUpdate(context, freeSlot)
            }
        }
    }

    @Synchronized
    fun deleteTile(tileId: String) {
        val current = _tilesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == tileId }
        if (index >= 0) {
            val oldSlot = current[index].slotNumber
            current.removeAt(index)
            persistTiles(current)
            if (oldSlot in 1..MAX_SLOTS) {
                requestTileUpdate(context, oldSlot)
            }
        }
    }

    private fun findNextAvailableSlot(existingTiles: List<TileItem>): Int {
        val usedSlots = existingTiles.filter { it.isActive }.map { it.slotNumber }.toSet()
        for (slot in 1..MAX_SLOTS) {
            if (!usedSlots.contains(slot)) {
                return slot
            }
        }
        return -1 // No free slots
    }

    companion object {
        const val MAX_SLOTS = 15
        private const val KEY_TILES = "saved_tiles_list"

        @Volatile
        private var INSTANCE: TileRepository? = null

        fun getInstance(context: Context): TileRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TileRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun requestTileUpdate(context: Context, slot: Int) {
            try {
                val serviceClass = "com.wizzwuz.quicktiles.service.QuickTileService$slot"
                val component = ComponentName(context, serviceClass)
                TileService.requestListeningState(context, component)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}

package com.wizzwuz.quicktiles.data

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
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

    private val _privateActivityLaunchableFlow = MutableStateFlow(
        prefs.getBoolean(KEY_PRIVATE_ACTIVITY_LAUNCHABLE, false)
    )
    val privateActivityLaunchableFlow: StateFlow<Boolean> = _privateActivityLaunchableFlow.asStateFlow()

    fun isPrivateActivityLaunchable(): Boolean = _privateActivityLaunchableFlow.value

    fun setPrivateActivityLaunchable(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVATE_ACTIVITY_LAUNCHABLE, enabled).apply()
        _privateActivityLaunchableFlow.value = enabled
    }

    init {
        loadTiles()
        syncComponentStates(context)
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
        val oldSlot = if (index >= 0) current[index].slotNumber else -1

        val finalTile = if (tile.isActive) {
            val assignedSlot = if (tile.slotNumber in 1..MAX_SLOTS) {
                tile.slotNumber
            } else {
                findNextAvailableSlot(current.filter { it.id != tile.id })
            }
            if (assignedSlot in 1..MAX_SLOTS) {
                tile.copy(isActive = true, slotNumber = assignedSlot)
            } else {
                tile.copy(isActive = false, slotNumber = -1)
            }
        } else {
            tile.copy(slotNumber = -1)
        }

        if (index >= 0) {
            current[index] = finalTile
        } else {
            current.add(finalTile)
        }

        persistTiles(current)

        // Disable old slot if slot changed or tile became inactive
        if (oldSlot in 1..MAX_SLOTS && oldSlot != finalTile.slotNumber) {
            setComponentEnabled(context, oldSlot, false)
            requestTileUpdate(context, oldSlot)
        }

        // Enable new slot if active
        if (finalTile.isActive && finalTile.slotNumber in 1..MAX_SLOTS) {
            setComponentEnabled(context, finalTile.slotNumber, true)
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
                setComponentEnabled(context, oldSlot, false)
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
                setComponentEnabled(context, freeSlot, true)
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
                setComponentEnabled(context, oldSlot, false)
                requestTileUpdate(context, oldSlot)
            }
        }
    }

    @Synchronized
    fun deleteTiles(tileIds: Set<String>) {
        if (tileIds.isEmpty()) return
        val current = _tilesFlow.value.toMutableList()
        val oldSlots = mutableListOf<Int>()
        val it = current.iterator()
        while (it.hasNext()) {
            val tile = it.next()
            if (tile.id in tileIds) {
                if (tile.slotNumber in 1..MAX_SLOTS) {
                    oldSlots.add(tile.slotNumber)
                }
                it.remove()
            }
        }
        persistTiles(current)
        for (slot in oldSlots) {
            setComponentEnabled(context, slot, false)
            requestTileUpdate(context, slot)
        }
    }

    @Synchronized
    fun restoreAllTiles(tiles: List<TileItem>) {
        persistTiles(tiles)
        syncComponentStates(context)
        for (tile in tiles) {
            if (tile.isActive && tile.slotNumber in 1..MAX_SLOTS) {
                requestTileUpdate(context, tile.slotNumber)
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

    fun syncComponentStates(context: Context) {
        val activeSlots = _tilesFlow.value.filter { it.isActive }.map { it.slotNumber }.toSet()
        val pm = context.packageManager
        for (slot in 1..MAX_SLOTS) {
            val component = ComponentName(context, "com.wizzwuz.quicktiles.service.QuickTileService$slot")
            val shouldBeEnabled = activeSlots.contains(slot)
            val currentState = pm.getComponentEnabledSetting(component)
            val targetState = if (shouldBeEnabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            if (currentState != targetState) {
                try {
                    pm.setComponentEnabledSetting(component, targetState, PackageManager.DONT_KILL_APP)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

    companion object {
        const val MAX_SLOTS = 50
        private const val KEY_TILES = "saved_tiles_list"
        const val KEY_PRIVATE_ACTIVITY_LAUNCHABLE = "private_activity_launchable"

        @Volatile
        private var INSTANCE: TileRepository? = null

        fun getInstance(context: Context): TileRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TileRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun setComponentEnabled(context: Context, slot: Int, enabled: Boolean) {
            try {
                val serviceClass = "com.wizzwuz.quicktiles.service.QuickTileService$slot"
                val component = ComponentName(context, serviceClass)
                val targetState = if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                context.packageManager.setComponentEnabledSetting(
                    component,
                    targetState,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Throwable) {
                e.printStackTrace()
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

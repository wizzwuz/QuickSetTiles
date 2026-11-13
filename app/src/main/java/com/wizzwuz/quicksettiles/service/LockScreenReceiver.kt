package com.wizzwuz.quicksettiles.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wizzwuz.quicksettiles.data.TileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Collections

class LockScreenReceiver : BroadcastReceiver() {

    companion object {
        private val hiddenSlots = Collections.synchronizedSet(mutableSetOf<Int>())
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun onScreenOff(context: Context) {
            val repo = TileRepository.getInstance(context)
            val tiles = repo.tilesFlow.value
            val toHide = tiles.filter {
                it.isActive && it.isAddedToQs && !it.allowOnLockScreen && it.hideFromLockScreen && it.slotNumber in 1..TileRepository.MAX_SLOTS
            }

            for (tile in toHide) {
                hiddenSlots.add(tile.slotNumber)
                // Disabling component completely omits the tile from the lock screen Quick Settings panel
                TileRepository.setComponentEnabled(context, tile.slotNumber, false)
            }
        }

        fun onUserPresent(context: Context) {
            val repo = TileRepository.getInstance(context)
            val toRestore = synchronized(hiddenSlots) {
                val copy = HashSet(hiddenSlots)
                hiddenSlots.clear()
                copy
            }

            val tiles = repo.tilesFlow.value
            val allToRestore = tiles.filter {
                it.isActive && it.isAddedToQs && !it.allowOnLockScreen && it.hideFromLockScreen && it.slotNumber in 1..TileRepository.MAX_SLOTS
            }.map { it.slotNumber }.toSet() + toRestore

            for (slot in allToRestore) {
                TileRepository.setComponentEnabled(context, slot, true)
                TileRepository.requestTileUpdate(context, slot)
            }

            BaseTileService.refreshAllActiveTiles(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SCREEN_OFF -> onScreenOff(context)
            Intent.ACTION_USER_PRESENT -> onUserPresent(context)
        }
    }
}

package com.wizzwuz.quicktiles.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object TileEditChannel {
    private val _editSlotFlow = MutableSharedFlow<Int>(replay = 1)
    val editSlotFlow: SharedFlow<Int> = _editSlotFlow.asSharedFlow()

    fun requestEditSlot(slot: Int) {
        _editSlotFlow.tryEmit(slot)
    }

    fun clear() {
        _editSlotFlow.resetReplayCache()
    }
}

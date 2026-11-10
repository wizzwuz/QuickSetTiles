package com.wizzwuz.quicksettiles.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LockScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_OFF -> {
                BaseTileService.refreshAllActiveTiles(context)
            }
        }
    }
}

package com.wizzwuz.quicksettiles

import android.app.Application
import com.wizzwuz.quicksettiles.data.TileRepository
import com.wizzwuz.quicksettiles.ui.theme.ThemeManager
import com.wizzwuz.quicksettiles.util.AppHelper
import com.wizzwuz.quicksettiles.util.IconProcessor
import com.wizzwuz.quicksettiles.util.LogManager
import com.wizzwuz.quicksettiles.util.ProcessManager
import com.wizzwuz.quicksettiles.util.ShizukuHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuickTilesApp : Application() {
    companion object {
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize process manager to track UI and tile tasks and terminate background linger
        ProcessManager.init(this)
        // Initialize theme preference eagerly
        ThemeManager.init(this)
        // Initialize log manager for error recording to log.txt
        LogManager.init(this)
        // Initialize repository eagerly
        TileRepository.getInstance(this)
        // Initialize Shizuku binder and listeners eagerly
        ShizukuHelper.init()
        // Ensure WRITE_SECURE_SETTINGS is granted if Shizuku / Root is present
        applicationScope.launch {
            com.wizzwuz.quicksettiles.util.QsTileManager.ensurePermission(this@QuickTilesApp)
        }
        // Preload preset icons asynchronously so preset picker opens instantly
        applicationScope.launch(Dispatchers.IO) {
            IconProcessor.getPresetIcons(this@QuickTilesApp)
        }
        // Listen for screen lock and unlock events to refresh tile lock screen visibility
        try {
            val lockFilter = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_USER_PRESENT)
                addAction(android.content.Intent.ACTION_SCREEN_OFF)
            }
            registerReceiver(object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context, intent: android.content.Intent?) {
                    com.wizzwuz.quicksettiles.service.BaseTileService.refreshAllActiveTiles(context)
                }
            }, lockFilter)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AppHelper.clearCaches()
        IconProcessor.clearCache()
    }
}

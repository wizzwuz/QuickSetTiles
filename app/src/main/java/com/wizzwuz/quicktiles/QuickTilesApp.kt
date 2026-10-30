package com.wizzwuz.quicktiles

import android.app.Application
import com.wizzwuz.quicktiles.data.TileRepository
import com.wizzwuz.quicktiles.ui.theme.ThemeManager
import com.wizzwuz.quicktiles.util.AppHelper
import com.wizzwuz.quicktiles.util.IconProcessor
import com.wizzwuz.quicktiles.util.LogManager
import com.wizzwuz.quicktiles.util.ProcessManager
import com.wizzwuz.quicktiles.util.ShizukuHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AppHelper.clearCaches()
        IconProcessor.clearCache()
    }
}

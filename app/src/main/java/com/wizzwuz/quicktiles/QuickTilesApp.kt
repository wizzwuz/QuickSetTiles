package com.wizzwuz.quicktiles

import android.app.Application
import com.wizzwuz.quicktiles.data.TileRepository
import com.wizzwuz.quicktiles.ui.theme.ThemeManager
import com.wizzwuz.quicktiles.util.AppHelper
import com.wizzwuz.quicktiles.util.LogManager
import com.wizzwuz.quicktiles.util.ShizukuHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QuickTilesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize theme preference eagerly
        ThemeManager.init(this)
        // Initialize log manager for error recording to log.txt
        LogManager.init(this)
        // Initialize repository eagerly
        TileRepository.getInstance(this)
        // Initialize Shizuku binder and listeners eagerly
        ShizukuHelper.init()
        // Pre-warm apps and shortcuts cache asynchronously on IO thread for instant UI response
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppHelper.getInstalledApps(this@QuickTilesApp)
                AppHelper.getAppsWithShortcuts(this@QuickTilesApp)
            } catch (_: Throwable) {
                // Ignore any startup background cache warmup failure
            }
        }
    }
}

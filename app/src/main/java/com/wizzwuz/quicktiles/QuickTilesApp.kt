package com.wizzwuz.quicktiles

import android.app.Application
import com.wizzwuz.quicktiles.data.TileRepository
import com.wizzwuz.quicktiles.util.LogManager
import com.wizzwuz.quicktiles.util.ShizukuHelper

class QuickTilesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize log manager for error recording to log.txt
        LogManager.init(this)
        // Initialize repository eagerly
        TileRepository.getInstance(this)
        // Initialize Shizuku binder and listeners eagerly
        ShizukuHelper.init()
    }
}

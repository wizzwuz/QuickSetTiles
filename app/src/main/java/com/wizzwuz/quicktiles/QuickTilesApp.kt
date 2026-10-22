package com.wizzwuz.quicktiles

import android.app.Application
import com.wizzwuz.quicktiles.data.TileRepository

class QuickTilesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize repository eagerly
        TileRepository.getInstance(this)
    }
}

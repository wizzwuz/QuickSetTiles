package com.wizzwuz.quicktiles.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.wizzwuz.quicktiles.ui.theme.QuickTilesTheme
import com.wizzwuz.quicktiles.ui.theme.ThemeManager
import com.wizzwuz.quicktiles.util.ShizukuHelper
import com.wizzwuz.quicktiles.util.TileEditChannel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.init(this)
        enableEdgeToEdge()
        handleTileIntent(intent)

        setContent {
            val themeMode by ThemeManager.themeFlow.collectAsState()
            QuickTilesTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTileIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        ShizukuHelper.updateStatus()
    }

    private fun handleTileIntent(intent: Intent?) {
        if (intent?.action == TileService.ACTION_QS_TILE_PREFERENCES) {
            val component = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME)
            }
            val slot = component?.className?.substringAfter("QuickTileService")?.toIntOrNull()
            if (slot != null) {
                TileEditChannel.requestEditSlot(slot)
            }
        }
    }
}

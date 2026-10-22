package com.wizzwuz.quicktiles.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.wizzwuz.quicktiles.ui.theme.QuickTilesTheme
import com.wizzwuz.quicktiles.util.ShizukuHelper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuickTilesTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ShizukuHelper.updateStatus()
        if (ShizukuHelper.isShizukuRunning() && !ShizukuHelper.hasShizukuPermission()) {
            ShizukuHelper.requestShizukuPermission()
        }
    }
}

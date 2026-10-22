package com.wizzwuz.quicktiles.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.wizzwuz.quicktiles.R
import com.wizzwuz.quicktiles.data.TileRepository
import com.wizzwuz.quicktiles.model.TileItem
import com.wizzwuz.quicktiles.model.TileType
import com.wizzwuz.quicktiles.ui.MainActivity
import com.wizzwuz.quicktiles.util.IconProcessor
import com.wizzwuz.quicktiles.util.ShizukuHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class BaseTileService(private val slotNumber: Int) : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val repo = TileRepository.getInstance(this)
        val item = repo.getTileBySlot(slotNumber)

        if (item != null && item.isActive) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = item.name.ifBlank { "Tile $slotNumber" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = item.type.name
            }
            try {
                tile.icon = IconProcessor.createTileIcon(this, item.iconConfig)
            } catch (e: Throwable) {
                tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_default)
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Tile $slotNumber"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Unassigned"
            }
            tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_default)
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val repo = TileRepository.getInstance(this)
        val item = repo.getTileBySlot(slotNumber)

        if (item == null || !item.isActive) {
            // Open Quick Tiles app to configure this slot
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchIntent(intent)
            return
        }

        executeTileAction(item)
    }

    private fun executeTileAction(item: TileItem) {
        when (item.type) {
            TileType.APPS -> {
                val launchIntent = packageManager.getLaunchIntentForPackage(item.targetData)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    launchIntent(launchIntent)
                } else {
                    showToast("App not installed: ${item.targetData}")
                }
            }
            TileType.WEBSITE -> {
                try {
                    var url = item.targetData.trim()
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    launchIntent(browserIntent)
                } catch (e: Throwable) {
                    showToast("Failed to open URL: ${e.message}")
                }
            }
            TileType.SHORTCUT -> {
                try {
                    val intent = Intent.parseUri(item.targetData, Intent.URI_INTENT_SCHEME).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    launchIntent(intent)
                } catch (e: Throwable) {
                    showToast("Failed to launch shortcut: ${e.message}")
                }
            }
            TileType.ACTIVITY -> {
                try {
                    val comp = ComponentName.unflattenFromString(item.targetData)
                    if (comp != null) {
                        val intent = Intent().apply {
                            component = comp
                            action = Intent.ACTION_MAIN
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        launchIntent(intent)
                    } else {
                        showToast("Invalid activity: ${item.targetData}")
                    }
                } catch (e: Throwable) {
                    showToast("Error launching activity: ${e.message}")
                }
            }
            TileType.INTENT -> {
                try {
                    val intent = if (item.targetData.startsWith("intent:")) {
                        Intent.parseUri(item.targetData, Intent.URI_INTENT_SCHEME)
                    } else {
                        Intent(item.targetData)
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    launchIntent(intent)
                } catch (e: Throwable) {
                    showToast("Failed to execute intent: ${e.message}")
                }
            }
            TileType.SHELL -> {
                serviceScope.launch {
                    val result = ShizukuHelper.executeCommand(item.targetData)
                    val msg = if (result.exitCode == 0) {
                        val snippet = result.output.take(80)
                        "Shell (${result.executionMode}) Success: ${if (snippet.isNotBlank()) snippet else "OK"}"
                    } else {
                        "Shell (${result.executionMode}) Exit ${result.exitCode}: ${result.output.take(80)}"
                    }
                    showToast(msg)
                }
            }
            TileType.FILES -> {
                try {
                    val uri = Uri.parse(item.targetData)
                    val mimeType = contentResolver.getType(uri) ?: "*/*"
                    val fileIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    launchIntent(fileIntent)
                } catch (e: Throwable) {
                    showToast("Failed to open file: ${e.message}")
                }
            }
            TileType.FOLDERS -> {
                try {
                    val treeUri = Uri.parse(item.targetData)
                    val folderIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(treeUri, "vnd.android.document/root")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    launchIntent(folderIntent)
                } catch (e: Throwable) {
                    // Fallback to DocumentsUI or general view
                    try {
                        val treeUri = Uri.parse(item.targetData)
                        val fallbackIntent = Intent(Intent.ACTION_VIEW, treeUri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        launchIntent(fallbackIntent)
                    } catch (err: Throwable) {
                        showToast("Failed to open folder: ${err.message}")
                    }
                }
            }
        }
    }

    private fun launchIntent(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                slotNumber,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }
}

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
import com.wizzwuz.quicktiles.util.AppHelper
import com.wizzwuz.quicktiles.util.IconProcessor
import com.wizzwuz.quicktiles.util.PopupToastHelper
import com.wizzwuz.quicktiles.util.ProcessManager
import com.wizzwuz.quicktiles.util.ShizukuHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class BaseTileService(private val slotNumber: Int) : TileService() {

    companion object {
        private val activeInstances = java.util.concurrent.ConcurrentHashMap<Int, BaseTileService>()

        fun refreshSlot(slot: Int) {
            activeInstances[slot]?.let { service ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    service.updateTileState()
                }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        activeInstances[slotNumber] = this
        ProcessManager.onTileListeningStarted()
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        activeInstances.remove(slotNumber)
        ProcessManager.onTileListeningStopped()
    }

    override fun onDestroy() {
        super.onDestroy()
        activeInstances.remove(slotNumber)
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val repo = TileRepository.getInstance(this)
        val item = repo.getTileBySlot(slotNumber)

        if (item != null && item.isActive) {
            if (item.multiShellSteps.isNotEmpty()) {
                val stepIndex = item.currentMultiShellIndex.coerceAtLeast(0) % item.multiShellSteps.size
                val step = item.multiShellSteps[stepIndex]
                tile.state = if (step.visualState == com.wizzwuz.quicktiles.model.TileVisualState.ENABLED) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                val displayName = step.name.ifBlank { item.name }
                tile.label = displayName.ifBlank { "Tile $slotNumber" }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "${stepIndex + 1}/${item.multiShellSteps.size}"
                }
                try {
                    tile.icon = IconProcessor.createTileIcon(this, step.iconConfig)
                } catch (e: Throwable) {
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_default)
                }
            } else {
                tile.state = when (item.visualState) {
                    com.wizzwuz.quicktiles.model.TileVisualState.ENABLED -> Tile.STATE_ACTIVE
                    com.wizzwuz.quicktiles.model.TileVisualState.DISABLED -> Tile.STATE_INACTIVE
                    com.wizzwuz.quicktiles.model.TileVisualState.TOGGLE -> if (item.currentToggleOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                }
                val displayName = when (item.visualState) {
                    com.wizzwuz.quicktiles.model.TileVisualState.TOGGLE -> {
                        if (item.currentToggleOn) item.onTileName.ifBlank { item.name } else item.offTileName.ifBlank { item.name }
                    }
                    com.wizzwuz.quicktiles.model.TileVisualState.ENABLED -> item.name
                    com.wizzwuz.quicktiles.model.TileVisualState.DISABLED -> item.offTileName.ifBlank { item.name }
                }
                tile.label = displayName.ifBlank { item.name.ifBlank { "Tile $slotNumber" } }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = when (item.visualState) {
                        com.wizzwuz.quicktiles.model.TileVisualState.TOGGLE -> if (item.currentToggleOn) "Enabled" else "Disabled"
                        com.wizzwuz.quicktiles.model.TileVisualState.ENABLED -> "Enabled"
                        com.wizzwuz.quicktiles.model.TileVisualState.DISABLED -> "Disabled"
                    }
                }
                val effectiveIcon = when (item.visualState) {
                    com.wizzwuz.quicktiles.model.TileVisualState.TOGGLE -> if (item.currentToggleOn) (item.onIconConfig ?: item.iconConfig) else (item.offIconConfig ?: item.iconConfig)
                    com.wizzwuz.quicktiles.model.TileVisualState.ENABLED -> item.iconConfig
                    com.wizzwuz.quicktiles.model.TileVisualState.DISABLED -> item.offIconConfig ?: item.iconConfig
                }
                try {
                    tile.icon = IconProcessor.createTileIcon(this, effectiveIcon)
                } catch (e: Throwable) {
                    tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_default)
                }
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
        ProcessManager.onTileTaskStarted()
        var asyncTask = false
        try {
            if (item.visualState == com.wizzwuz.quicktiles.model.TileVisualState.TOGGLE && item.type != TileType.SHELL) {
                val repo = TileRepository.getInstance(this)
                val updated = item.copy(currentToggleOn = !item.currentToggleOn)
                repo.saveTile(updated)
                mainHandler.post { updateTileState() }
            }

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
                            asyncTask = true
                            serviceScope.launch {
                                try {
                                    if (item.collapseOnTap) {
                                        mainHandler.post { collapseShade() }
                                    }
                                    AppHelper.launchActivityPrivileged(
                                        this@BaseTileService,
                                        comp.packageName,
                                        comp.className
                                    )
                                } finally {
                                    ProcessManager.onTileTaskFinished()
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        // Silent failure
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
                    asyncTask = true
                    serviceScope.launch {
                        try {
                            val repo = TileRepository.getInstance(this@BaseTileService)
                            val cmdToRun: String
                            val shouldCollapse: Boolean
                            if (item.multiShellSteps.isNotEmpty()) {
                                val stepIndex = item.currentMultiShellIndex.coerceAtLeast(0) % item.multiShellSteps.size
                                val step = item.multiShellSteps[stepIndex]
                                cmdToRun = step.command
                                shouldCollapse = step.collapsePanel
                                val nextIndex = (stepIndex + 1) % item.multiShellSteps.size
                                val updatedItem = item.copy(currentMultiShellIndex = nextIndex)
                                repo.saveTile(updatedItem)
                                mainHandler.post { updateTileState() }
                            } else {
                                shouldCollapse = item.collapseOnTap
                                val nextToggleState: Boolean
                                when (item.visualState) {
                                    com.wizzwuz.quicktiles.model.TileVisualState.TOGGLE -> {
                                        if (item.currentToggleOn) {
                                            // Currently ON, toggle to OFF! Execute offCommand
                                            cmdToRun = if (item.offCommand.isNotBlank()) item.offCommand else item.targetData
                                            nextToggleState = false
                                        } else {
                                            // Currently OFF, toggle to ON! Execute onCommand
                                            cmdToRun = if (item.onCommand.isNotBlank()) item.onCommand else item.targetData
                                            nextToggleState = true
                                        }
                                        val updatedItem = item.copy(currentToggleOn = nextToggleState)
                                        repo.saveTile(updatedItem)
                                        mainHandler.post { updateTileState() }
                                    }
                                    com.wizzwuz.quicktiles.model.TileVisualState.ENABLED -> {
                                        cmdToRun = if (item.onCommand.isNotBlank()) item.onCommand else item.targetData
                                        nextToggleState = true
                                    }
                                    com.wizzwuz.quicktiles.model.TileVisualState.DISABLED -> {
                                        cmdToRun = if (item.offCommand.isNotBlank()) item.offCommand else item.targetData
                                        nextToggleState = false
                                    }
                                }
                            }

                            if (shouldCollapse) {
                                mainHandler.post { collapseShade() }
                            }

                            val result = ShizukuHelper.executeCommand(cmdToRun)

                            if (item.showPopups && result.output.isNotBlank()) {
                                val lines = result.output.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                for (line in lines) {
                                    PopupToastHelper.showToast(applicationContext, line)
                                    if (lines.size > 1) {
                                        kotlinx.coroutines.delay(1200)
                                    }
                                }
                            }

                            if (result.exitCode != 0) {
                                com.wizzwuz.quicktiles.util.LogManager.logError(
                                    "TileExecution",
                                    "Tile '${item.name}' (Slot $slotNumber) Shell (${result.executionMode}) failed with exit code ${result.exitCode}: ${result.output}"
                                )
                            }
                        } finally {
                            ProcessManager.onTileTaskFinished()
                        }
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
            TileType.COLLECTION -> {
                try {
                    if (item.collapseOnTap) {
                        mainHandler.post { collapseShade() }
                    }
                    val intent = Intent(this, com.wizzwuz.quicktiles.ui.CollectionPopupActivity::class.java).apply {
                        putExtra("collectionId", item.id)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    launchIntent(intent)
                } catch (e: Throwable) {
                    showToast("Failed to open collection: ${e.message}")
                }
        }
        } finally {
            if (!asyncTask) {
                ProcessManager.onTileTaskFinished()
            }
        }
    }

    private fun collapseShade() {
        serviceScope.launch {
            try {
                if (ShizukuHelper.isShizukuRunning() || ShizukuHelper.isRootBinaryPresent()) {
                    val res = ShizukuHelper.executeCommand("cmd statusbar collapse")
                    if (res.exitCode == 0) return@launch
                }
            } catch (e: Throwable) {
                // fallback
            }
            mainHandler.post {
                fallbackCollapseShade()
            }
        }
    }

    private fun fallbackCollapseShade() {
        try {
            val intent = Intent(this, com.wizzwuz.quicktiles.ui.InternalPluginActivity::class.java).apply {
                action = "collapse_panel"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    slotNumber + 1000,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: Throwable) {
            // Ignore collapse error
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

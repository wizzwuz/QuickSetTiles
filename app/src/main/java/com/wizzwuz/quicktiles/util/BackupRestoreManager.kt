package com.wizzwuz.quicktiles.util

import android.content.Context
import android.util.Base64
import com.wizzwuz.quicktiles.data.TileRepository
import com.wizzwuz.quicktiles.model.IconSourceType
import com.wizzwuz.quicktiles.model.TileItem
import com.wizzwuz.quicktiles.ui.theme.AppThemeMode
import com.wizzwuz.quicktiles.ui.theme.ThemeManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class RestoreResult(
    val tileCount: Int,
    val hasTheme: Boolean,
    val hasPrivateLaunchable: Boolean
)

object BackupRestoreManager {

    private const val BACKUP_VERSION = 1

    fun createBackup(context: Context): String {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("timestamp", System.currentTimeMillis())

        // 1. Settings
        val settingsObj = JSONObject()
        settingsObj.put("theme", ThemeManager.getTheme().name)
        settingsObj.put("privateActivityLaunchable", TileRepository.getInstance(context).isPrivateActivityLaunchable())
        root.put("settings", settingsObj)

        // 2. Tiles
        val repo = TileRepository.getInstance(context)
        val tiles = repo.loadTiles()
        val tilesArray = JSONArray()
        val customIconFiles = mutableSetOf<File>()

        for (tile in tiles) {
            tilesArray.put(tile.toJson())
            // Collect any custom file icons
            listOfNotNull(tile.iconConfig, tile.onIconConfig, tile.offIconConfig).forEach { cfg ->
                if (cfg.sourceType == IconSourceType.CUSTOM_FILE) {
                    val file = File(cfg.sourceData)
                    if (file.exists() && file.isFile) {
                        customIconFiles.add(file)
                    }
                }
            }
        }
        root.put("tiles", tilesArray)

        // 3. Embedded Custom Icons (Base64)
        val iconsObj = JSONObject()
        for (file in customIconFiles) {
            try {
                val bytes = file.readBytes()
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                iconsObj.put(file.name, b64)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        root.put("icons", iconsObj)

        return root.toString(2)
    }

    fun restoreBackup(context: Context, jsonString: String): Result<RestoreResult> {
        return try {
            val root = JSONObject(jsonString)
            val version = root.optInt("version", 1)
            if (version > BACKUP_VERSION) {
                return Result.failure(Exception("Unsupported backup version: $version"))
            }

            val iconsDir = File(context.filesDir, "icons").apply { if (!exists()) mkdirs() }

            // 1. Restore Custom Icons
            if (root.has("icons")) {
                val iconsObj = root.getJSONObject("icons")
                val keys = iconsObj.keys()
                while (keys.hasNext()) {
                    val fileName = keys.next()
                    val b64 = iconsObj.getString(fileName)
                    try {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        val targetFile = File(iconsDir, fileName)
                        targetFile.writeBytes(bytes)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            }

            // 2. Restore Settings
            var hasTheme = false
            var hasPrivate = false
            if (root.has("settings")) {
                val settingsObj = root.getJSONObject("settings")
                if (settingsObj.has("theme")) {
                    val themeName = settingsObj.getString("theme")
                    try {
                        val mode = AppThemeMode.valueOf(themeName)
                        ThemeManager.setTheme(context, mode)
                        hasTheme = true
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
                if (settingsObj.has("privateActivityLaunchable")) {
                    val privateLaunchable = settingsObj.getBoolean("privateActivityLaunchable")
                    TileRepository.getInstance(context).setPrivateActivityLaunchable(privateLaunchable)
                    hasPrivate = true
                }
            }

            // 3. Restore Tiles
            val repo = TileRepository.getInstance(context)
            val restoredTiles = mutableListOf<TileItem>()
            if (root.has("tiles")) {
                val tilesArray = root.getJSONArray("tiles")
                for (i in 0 until tilesArray.length()) {
                    val tileObj = tilesArray.getJSONObject(i)
                    val tile = TileItem.fromJson(tileObj)

                    // Fix up custom icon file paths to point to this device's filesDir
                    fun remapConfig(cfg: com.wizzwuz.quicktiles.model.IconConfig): com.wizzwuz.quicktiles.model.IconConfig {
                        return if (cfg.sourceType == IconSourceType.CUSTOM_FILE) {
                            val fileName = cfg.sourceData.substringAfterLast('/')
                            val localFile = File(iconsDir, fileName)
                            if (localFile.exists()) {
                                cfg.copy(sourceData = localFile.absolutePath)
                            } else {
                                cfg
                            }
                        } else {
                            cfg
                        }
                    }

                    val remappedIcon = remapConfig(tile.iconConfig)
                    val remappedOnIcon = tile.onIconConfig?.let { remapConfig(it) }
                    val remappedOffIcon = tile.offIconConfig?.let { remapConfig(it) }

                    restoredTiles.add(
                        tile.copy(
                            iconConfig = remappedIcon,
                            onIconConfig = remappedOnIcon,
                            offIconConfig = remappedOffIcon
                        )
                    )
                }
            }

            repo.restoreAllTiles(restoredTiles)

            Result.success(
                RestoreResult(
                    tileCount = restoredTiles.size,
                    hasTheme = hasTheme,
                    hasPrivateLaunchable = hasPrivate
                )
            )
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}

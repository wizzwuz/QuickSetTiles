package com.wizzwuz.quicksettiles.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.wizzwuz.quicksettiles.data.TileRepository
import com.wizzwuz.quicksettiles.model.IconConfig
import com.wizzwuz.quicksettiles.model.IconSourceType
import com.wizzwuz.quicksettiles.model.TileItem
import com.wizzwuz.quicksettiles.ui.theme.AppThemeMode
import com.wizzwuz.quicksettiles.ui.theme.ThemeManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class RestoreResult(
    val tileCount: Int,
    val hasTheme: Boolean,
    val hasPrivateLaunchable: Boolean,
    val hasGridView: Boolean = false,
    val restoredIconsCount: Int = 0
)

data class BackupPreview(
    val tileCount: Int,
    val timestamp: Long,
    val hasTheme: Boolean,
    val hasGridView: Boolean,
    val iconCount: Int
)

object BackupRestoreManager {

    private const val BACKUP_VERSION = 1
    private const val BACKUP_JSON_ENTRY = "backup.json"
    private const val ICONS_DIR_ENTRY = "icons/"

    /**
     * Builds the root JSONObject representing the current tiles, settings, and gathers all custom icons.
     */
    fun createBackupJson(context: Context): Pair<JSONObject, Set<File>> {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("timestamp", System.currentTimeMillis())

        // 1. Settings
        val repo = TileRepository.getInstance(context)
        val settingsObj = JSONObject()
        settingsObj.put("theme", ThemeManager.getTheme().name)
        settingsObj.put("privateActivityLaunchable", repo.isPrivateActivityLaunchable())
        settingsObj.put("isGridView", repo.isGridViewEnabled())
        settingsObj.put("isGridDynamic", repo.isGridDynamic())
        settingsObj.put("gridColumns", repo.getGridColumns())
        settingsObj.put("gridRows", repo.getGridRows())
        root.put("settings", settingsObj)

        // 2. Tiles & Custom Icons
        val tiles = repo.loadTiles()
        val tilesArray = JSONArray()
        val customIconFiles = mutableSetOf<File>()

        fun collectIcon(cfg: IconConfig?) {
            if (cfg != null && cfg.sourceType == IconSourceType.CUSTOM_FILE) {
                val file = File(cfg.sourceData)
                if (file.exists() && file.isFile) {
                    customIconFiles.add(file)
                }
            }
        }

        for (tile in tiles) {
            tilesArray.put(tile.toJson())
            collectIcon(tile.iconConfig)
            collectIcon(tile.onIconConfig)
            collectIcon(tile.offIconConfig)
            tile.multiShellSteps.forEach { step ->
                collectIcon(step.iconConfig)
            }
        }
        root.put("tiles", tilesArray)

        // Also collect all files present in internal icons directory
        val iconsDir = File(context.filesDir, "icons")
        if (iconsDir.exists() && iconsDir.isDirectory) {
            iconsDir.listFiles()?.forEach { file ->
                if (file.isFile && file.length() > 0) {
                    customIconFiles.add(file)
                }
            }
        }

        return root to customIconFiles
    }

    /**
     * Creates a .bak archive containing backup.json and raw custom icon files.
     */
    fun createBackup(context: Context, outputStream: OutputStream) {
        val (root, customIconFiles) = createBackupJson(context)
        ZipOutputStream(outputStream.buffered()).use { zos ->
            // 1. Write backup.json entry
            val jsonBytes = root.toString(2).toByteArray(Charsets.UTF_8)
            val jsonEntry = ZipEntry(BACKUP_JSON_ENTRY)
            zos.putNextEntry(jsonEntry)
            zos.write(jsonBytes)
            zos.closeEntry()

            // 2. Write custom icons into icons/ folder
            for (file in customIconFiles) {
                try {
                    val iconEntry = ZipEntry("$ICONS_DIR_ENTRY${file.name}")
                    zos.putNextEntry(iconEntry)
                    file.inputStream().use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
            zos.finish()
            zos.flush()
        }
    }

    /**
     * Legacy backup string with Base64 embedded icons (for backwards compatibility / testing).
     */
    fun createBackup(context: Context): String {
        val (root, customIconFiles) = createBackupJson(context)
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

    /**
     * Peeks at a backup file to show preview details (tile count, custom icons count) without modifying state.
     */
    fun peekBackup(context: Context, uri: Uri): Result<BackupPreview> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                peekBackup(stream)
            } ?: Result.failure(Exception("Cannot open input stream from URI"))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun peekBackup(inputStream: InputStream): Result<BackupPreview> {
        return try {
            val buffered = if (inputStream is BufferedInputStream) inputStream else BufferedInputStream(inputStream)
            buffered.mark(16)
            val header = ByteArray(4)
            val read = buffered.read(header)
            buffered.reset()

            val isZip = read >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()

            if (isZip) {
                var jsonString: String? = null
                var iconCount = 0
                ZipInputStream(buffered).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == BACKUP_JSON_ENTRY) {
                            jsonString = zis.bufferedReader(Charsets.UTF_8).readText()
                        } else if (entry.name.startsWith(ICONS_DIR_ENTRY) && !entry.isDirectory) {
                            iconCount++
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                if (jsonString == null) {
                    return Result.failure(Exception("Invalid .bak archive: missing $BACKUP_JSON_ENTRY"))
                }
                val root = JSONObject(jsonString!!)
                val tilesArray = root.optJSONArray("tiles")
                val tileCount = tilesArray?.length() ?: 0
                val settings = root.optJSONObject("settings")
                val hasTheme = settings?.has("theme") == true
                val hasGridView = settings?.has("isGridView") == true
                val timestamp = root.optLong("timestamp", System.currentTimeMillis())

                Result.success(
                    BackupPreview(
                        tileCount = tileCount,
                        timestamp = timestamp,
                        hasTheme = hasTheme,
                        hasGridView = hasGridView,
                        iconCount = iconCount
                    )
                )
            } else {
                // Plain JSON legacy backup
                val jsonString = buffered.bufferedReader(Charsets.UTF_8).readText()
                val root = JSONObject(jsonString)
                val tilesArray = root.optJSONArray("tiles")
                val tileCount = tilesArray?.length() ?: 0
                val iconsObj = root.optJSONObject("icons")
                val iconCount = iconsObj?.length() ?: 0
                val settings = root.optJSONObject("settings")
                val hasTheme = settings?.has("theme") == true
                val hasGridView = settings?.has("isGridView") == true
                val timestamp = root.optLong("timestamp", System.currentTimeMillis())

                Result.success(
                    BackupPreview(
                        tileCount = tileCount,
                        timestamp = timestamp,
                        hasTheme = hasTheme,
                        hasGridView = hasGridView,
                        iconCount = iconCount
                    )
                )
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Restores backup from a Uri. Supports both .bak (ZIP container) and legacy .json files.
     */
    fun restoreBackup(context: Context, uri: Uri): Result<RestoreResult> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                restoreBackup(context, stream)
            } ?: Result.failure(Exception("Cannot open input stream from URI"))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Restores backup from an InputStream. Supports both .bak (ZIP container) and legacy .json files.
     */
    fun restoreBackup(context: Context, inputStream: InputStream): Result<RestoreResult> {
        return try {
            val buffered = if (inputStream is BufferedInputStream) inputStream else BufferedInputStream(inputStream)
            buffered.mark(16)
            val header = ByteArray(4)
            val read = buffered.read(header)
            buffered.reset()

            val isZip = read >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            val iconsDir = File(context.filesDir, "icons").apply { if (!exists()) mkdirs() }
            var restoredIconsCount = 0

            val jsonString = if (isZip) {
                var json: String? = null
                ZipInputStream(buffered).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == BACKUP_JSON_ENTRY) {
                            json = zis.bufferedReader(Charsets.UTF_8).readText()
                        } else if (entry.name.startsWith(ICONS_DIR_ENTRY) && !entry.isDirectory) {
                            val fileName = entry.name.substringAfterLast('/')
                            if (fileName.isNotEmpty()) {
                                val targetFile = File(iconsDir, fileName)
                                targetFile.outputStream().use { out ->
                                    zis.copyTo(out)
                                }
                                restoredIconsCount++
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                json ?: return Result.failure(Exception("Invalid .bak archive: missing $BACKUP_JSON_ENTRY"))
            } else {
                val rawJson = buffered.bufferedReader(Charsets.UTF_8).readText()
                // Extract any Base64 icons from legacy JSON
                try {
                    val root = JSONObject(rawJson)
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
                                restoredIconsCount++
                            } catch (e: Throwable) {
                                e.printStackTrace()
                            }
                        }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                rawJson
            }

            restoreFromJson(context, jsonString, restoredIconsCount)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Legacy restore from JSON string directly.
     */
    fun restoreBackup(context: Context, jsonString: String): Result<RestoreResult> {
        return try {
            val iconsDir = File(context.filesDir, "icons").apply { if (!exists()) mkdirs() }
            var restoredIconsCount = 0
            val root = JSONObject(jsonString)
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
                        restoredIconsCount++
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            }
            restoreFromJson(context, jsonString, restoredIconsCount)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun restoreFromJson(context: Context, jsonString: String, restoredIconsCount: Int): Result<RestoreResult> {
        return try {
            val root = JSONObject(jsonString)
            val version = root.optInt("version", 1)
            if (version > BACKUP_VERSION) {
                return Result.failure(Exception("Unsupported backup version: $version"))
            }

            val iconsDir = File(context.filesDir, "icons").apply { if (!exists()) mkdirs() }

            // 1. Restore Settings
            val repo = TileRepository.getInstance(context)
            var hasTheme = false
            var hasPrivate = false
            var hasGridView = false
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
                    repo.setPrivateActivityLaunchable(privateLaunchable)
                    hasPrivate = true
                }
                if (settingsObj.has("isGridView")) {
                    val isGrid = settingsObj.getBoolean("isGridView")
                    repo.setGridViewEnabled(isGrid)
                    hasGridView = true
                }
                if (settingsObj.has("isGridDynamic")) {
                    val isDynamic = settingsObj.getBoolean("isGridDynamic")
                    repo.setGridDynamic(isDynamic)
                }
                if (settingsObj.has("gridColumns")) {
                    val columns = settingsObj.getInt("gridColumns")
                    repo.setGridColumns(columns)
                }
                if (settingsObj.has("gridRows")) {
                    val rows = settingsObj.getInt("gridRows")
                    repo.setGridRows(rows)
                }
            }

            // 2. Restore Tiles with custom icon path remapping
            val restoredTiles = mutableListOf<TileItem>()
            if (root.has("tiles")) {
                val tilesArray = root.getJSONArray("tiles")

                fun remapConfig(cfg: IconConfig): IconConfig {
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

                for (i in 0 until tilesArray.length()) {
                    val tileObj = tilesArray.getJSONObject(i)
                    val tile = TileItem.fromJson(tileObj)

                    val remappedIcon = remapConfig(tile.iconConfig)
                    val remappedOnIcon = tile.onIconConfig?.let { remapConfig(it) }
                    val remappedOffIcon = tile.offIconConfig?.let { remapConfig(it) }
                    val remappedSteps = tile.multiShellSteps.map { step ->
                        step.copy(iconConfig = remapConfig(step.iconConfig))
                    }

                    restoredTiles.add(
                        tile.copy(
                            iconConfig = remappedIcon,
                            onIconConfig = remappedOnIcon,
                            offIconConfig = remappedOffIcon,
                            multiShellSteps = remappedSteps
                        )
                    )
                }
            }

            repo.restoreAllTiles(restoredTiles)

            Result.success(
                RestoreResult(
                    tileCount = restoredTiles.size,
                    hasTheme = hasTheme,
                    hasPrivateLaunchable = hasPrivate,
                    hasGridView = hasGridView,
                    restoredIconsCount = restoredIconsCount
                )
            )
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}

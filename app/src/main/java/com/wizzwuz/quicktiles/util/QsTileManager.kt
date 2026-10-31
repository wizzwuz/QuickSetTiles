package com.wizzwuz.quicktiles.util

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import com.wizzwuz.quicktiles.data.TileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object QsTileManager {

    private const val SECURE_KEY = "sysui_qs_tiles"
    private const val PKG = "com.wizzwuz.quicktiles"

    private fun getTileSpecShort(slot: Int): String =
        "custom($PKG/.service.QuickTileService$slot)"

    private fun getTileSpecFull(slot: Int): String =
        "custom($PKG/com.wizzwuz.quicktiles.service.QuickTileService$slot)"

    /**
     * Attempts to grant WRITE_SECURE_SETTINGS via Shizuku or Root if not already granted.
     */
    suspend fun ensurePermission(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        withContext(Dispatchers.IO) {
            try {
                if (ShizukuHelper.isShizukuRunning() || ShizukuHelper.isRootBinaryPresent()) {
                    ShizukuHelper.executeCommand("pm grant $PKG android.permission.WRITE_SECURE_SETTINGS")
                }
            } catch (e: Throwable) {
                // Ignore
            }
        }
    }

    /**
     * Reads current sysui_qs_tiles string.
     */
    suspend fun getQsTilesString(context: Context): String? {
        try {
            val res = Settings.Secure.getString(context.contentResolver, SECURE_KEY)
            if (res != null) return res
        } catch (e: Throwable) {
            // Not granted natively
        }

        return withContext(Dispatchers.IO) {
            try {
                val cmdRes = ShizukuHelper.executeCommand("settings get secure $SECURE_KEY")
                if (cmdRes.exitCode == 0 && cmdRes.output.isNotBlank() && !cmdRes.output.contains("null")) {
                    cmdRes.output.trim()
                } else {
                    null
                }
            } catch (e: Throwable) {
                null
            }
        }
    }

    /**
     * Writes new sysui_qs_tiles string to Settings.Secure.
     */
    suspend fun putQsTilesString(context: Context, newString: String): Boolean {
        try {
            if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
                val success = Settings.Secure.putString(context.contentResolver, SECURE_KEY, newString)
                if (success) return true
            }
        } catch (e: Throwable) {
            // Fallback to shell
        }

        return withContext(Dispatchers.IO) {
            try {
                val cmdRes = ShizukuHelper.executeCommand("settings put secure $SECURE_KEY \"$newString\"")
                cmdRes.exitCode == 0
            } catch (e: Throwable) {
                false
            }
        }
    }

    /**
     * Adds the tile to the VERY LAST position of all Quick Settings tiles.
     */
    suspend fun addTileToLast(
        context: Context,
        slot: Int,
        tileName: String,
        iconBitmap: Bitmap?
    ): Boolean {
        // Ensure component is enabled in PackageManager first
        TileRepository.setComponentEnabled(context, slot, true)
        TileRepository.requestTileUpdate(context, slot)

        ensurePermission(context)

        val specShort = getTileSpecShort(slot)
        val specFull = getTileSpecFull(slot)

        val currentStr = getQsTilesString(context)
        if (currentStr != null) {
            val list = currentStr.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filter { it != specShort && it != specFull && !it.contains("QuickTileService$slot") }
                .toMutableList()

            // Append to the VERY LAST position of all quick settings tiles
            list.add(specShort)
            val newString = list.joinToString(",")

            val written = putQsTilesString(context, newString)
            if (written) {
                TileRepository.requestTileUpdate(context, slot)
                return true
            }
        }

        // Fallback to StatusBarManager if secure settings cannot be written directly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && iconBitmap != null) {
            return try {
                val sbm = context.getSystemService(StatusBarManager::class.java)
                val comp = ComponentName(context, "com.wizzwuz.quicktiles.service.QuickTileService$slot")
                sbm.requestAddTileService(
                    comp,
                    tileName,
                    Icon.createWithBitmap(iconBitmap),
                    context.mainExecutor
                ) {}
                true
            } catch (e: Throwable) {
                false
            }
        }

        return false
    }

    /**
     * Removes the tile from sysui_qs_tiles and disables its component.
     */
    suspend fun removeTile(context: Context, slot: Int): Boolean {
        val specShort = getTileSpecShort(slot)
        val specFull = getTileSpecFull(slot)

        val currentStr = getQsTilesString(context)
        if (currentStr != null) {
            val list = currentStr.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filter { it != specShort && it != specFull && !it.contains("QuickTileService$slot") }

            val newString = list.joinToString(",")
            putQsTilesString(context, newString)
        }

        TileRepository.setComponentEnabled(context, slot, false)
        TileRepository.requestTileUpdate(context, slot)
        return true
    }
}

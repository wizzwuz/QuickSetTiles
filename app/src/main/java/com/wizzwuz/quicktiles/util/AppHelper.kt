package com.wizzwuz.quicktiles.util

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class AppItem(
    val label: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val icon: Drawable
)

data class ActivityItem(
    val fullName: String,
    val simpleName: String,
    val isExported: Boolean
)

data class ShortcutProviderItem(
    val appLabel: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable
)

object AppHelper {

    suspend fun getInstalledApps(context: Context): List<AppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val list = mutableListOf<AppItem>()

        for (app in apps) {
            val label = try { pm.getApplicationLabel(app).toString() } catch (e: Throwable) { app.packageName }
            val icon = try { pm.getApplicationIcon(app) } catch (e: Throwable) { pm.defaultActivityIcon }
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            list.add(AppItem(label = label, packageName = app.packageName, isSystemApp = isSystem, icon = icon))
        }

        list.sortBy { it.label.lowercase() }
        list
    }

    suspend fun getAppActivities(context: Context, packageName: String): List<ActivityItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val list = mutableListOf<ActivityItem>()
        try {
            val pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            val activities = pkgInfo.activities ?: return@withContext emptyList()
            for (act in activities) {
                val simpleName = act.name.substringAfterLast('.')
                list.add(
                    ActivityItem(
                        fullName = act.name,
                        simpleName = simpleName,
                        isExported = act.exported
                    )
                )
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        list.sortBy { it.simpleName.lowercase() }
        list
    }

    suspend fun launchActivityPrivileged(
        context: Context,
        packageName: String,
        activityName: String,
        isExported: Boolean? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        // If explicitly exported or unknown, try standard Intent first
        if (isExported != false) {
            try {
                val intent = Intent().apply {
                    component = ComponentName(packageName, activityName)
                    action = Intent.ACTION_MAIN
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return@withContext Result.success("Launched activity directly")
            } catch (e: SecurityException) {
                // Not exported / Permission denied - fall through to privileged shell
            } catch (e: Throwable) {
                if (isExported == true) {
                    return@withContext Result.failure(e)
                }
            }
        }

        // If Shizuku is running and permission not yet granted, prompt for permission immediately
        if (ShizukuHelper.isShizukuRunning() && !ShizukuHelper.hasShizukuPermission()) {
            withContext(Dispatchers.Main) {
                ShizukuHelper.requestShizukuPermission()
            }
            return@withContext Result.failure(
                SecurityException("Shizuku permission required. Please grant permission in Shizuku / Shevery to launch private activities.")
            )
        }

        val componentStr = "$packageName/$activityName"
        val escapedComponent = componentStr.replace("$", "\\$")

        // Strategy 1: If Root is present, root can launch any private/unexported activity directly
        if (ShizukuHelper.isRootBinaryPresent()) {
            val rootCmd = "am start -n '$escapedComponent'"
            val rootRes = ShizukuHelper.executeCommand(rootCmd, mode = ShellMode.ROOT)
            val hasRootDenial = rootRes.output.contains("SecurityException") ||
                    rootRes.output.contains("Permission Denial") ||
                    rootRes.output.contains("Error: Activity class")
            if (rootRes.exitCode == 0 && !hasRootDenial) {
                return@withContext Result.success("Launched via Root (su)")
            }
        }

        // Strategy 2: Assistant Method (Official ActivityLauncherShizukuPlugin mechanism)
        if (ShizukuHelper.isShizukuRunning() && ShizukuHelper.hasShizukuPermission()) {
            val assistantRes = launchViaAssistantMethod(context, componentStr)
            if (assistantRes.isSuccess) {
                return@withContext assistantRes
            }
        }

        // Strategy 3: Direct am start via Shizuku with proper quoting
        val shizukuCmd = "am start -n '$escapedComponent'"
        val result = ShizukuHelper.executePrivilegedCommand(shizukuCmd)
        val hasDenial = result.output.contains("SecurityException") ||
                result.output.contains("Permission Denial") ||
                result.output.contains("not exported") ||
                result.output.contains("requires permission") ||
                result.output.contains("Error:")

        if (result.executionMode != "None" && result.exitCode == 0 && !hasDenial) {
            Result.success("Launched via ${result.executionMode}")
        } else {
            // Strategy 4: Fallback to am start with user 0 flag
            val userCmd = "am start --user 0 -n '$escapedComponent'"
            val userRes = ShizukuHelper.executeCommand(userCmd, mode = ShellMode.AUTO)
            val userHasDenial = userRes.output.contains("SecurityException") ||
                    userRes.output.contains("Permission Denial") ||
                    userRes.output.contains("not exported") ||
                    userRes.output.contains("requires permission") ||
                    userRes.output.contains("Error:")
            if (userRes.exitCode == 0 && !userHasDenial) {
                Result.success("Launched via Privileged Shell")
            } else {
                val errDetails = if (result.output.isNotBlank()) result.output else "Process exited with code ${result.exitCode}"
                LogManager.logError("ActivityLaunch", "Failed to launch $componentStr via ${result.executionMode}: $errDetails")
                Result.failure(Exception("Privileged launch failed (${result.executionMode}): $errDetails"))
            }
        }
    }

    private suspend fun launchViaAssistantMethod(
        context: Context,
        componentStr: String
    ): Result<String> {
        try {
            // Ensure WRITE_SECURE_SETTINGS is granted to our app
            ShizukuHelper.executePrivilegedCommand("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")

            // Read original assistant setting
            val origRes = ShizukuHelper.executePrivilegedCommand("settings get secure assistant")
            val originalAssistant = origRes.output.trim()

            try {
                // Set assistant to target activity
                ShizukuHelper.executePrivilegedCommand("settings put secure assistant '$componentStr'")
                try {
                    Settings.Secure.putString(context.contentResolver, "assistant", componentStr)
                } catch (e: Throwable) {
                    // Ignore
                }

                // Trigger assistant launch via SearchManager reflection
                val sm = context.getSystemService(Context.SEARCH_SERVICE) as? SearchManager
                if (sm != null) {
                    try {
                        val m = sm.javaClass.getDeclaredMethod("launchAssist", Bundle::class.java)
                        m.isAccessible = true
                        m.invoke(sm, Bundle())
                    } catch (e: Throwable) {
                        try {
                            val m = sm.javaClass.getDeclaredMethod("startAssist", Bundle::class.java)
                            m.isAccessible = true
                            m.invoke(sm, Bundle())
                        } catch (e2: Throwable) {
                            // Handled by keyevent below
                        }
                    }
                }

                // Also inject KEYCODE_ASSIST (219) via privileged shell
                ShizukuHelper.executePrivilegedCommand("input keyevent 219")
                delay(350)

                return Result.success("Launched private activity via Assistant Method")
            } finally {
                // Restore original assistant
                if (originalAssistant.isNotBlank() && originalAssistant != "null") {
                    ShizukuHelper.executePrivilegedCommand("settings put secure assistant '$originalAssistant'")
                    try {
                        Settings.Secure.putString(context.contentResolver, "assistant", originalAssistant)
                    } catch (e: Throwable) {
                        // Ignore
                    }
                } else {
                    ShizukuHelper.executePrivilegedCommand("settings delete secure assistant")
                }
            }
        } catch (e: Throwable) {
            return Result.failure(e)
        }
    }

    suspend fun testLaunchActivity(
        context: Context,
        packageName: String,
        activityName: String,
        isExported: Boolean? = null
    ): Result<String> {
        return launchActivityPrivileged(context, packageName, activityName, isExported)
    }

    suspend fun getShortcutProviders(context: Context): List<ShortcutProviderItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val shortcutIntent = Intent(Intent.ACTION_CREATE_SHORTCUT)
        val resolveInfos = pm.queryIntentActivities(shortcutIntent, 0)
        val list = mutableListOf<ShortcutProviderItem>()

        for (info in resolveInfos) {
            val appLabel = info.loadLabel(pm).toString()
            val pkg = info.activityInfo.packageName
            val act = info.activityInfo.name
            val icon = info.loadIcon(pm)
            list.add(ShortcutProviderItem(appLabel, pkg, act, icon))
        }

        list.sortBy { it.appLabel.lowercase() }
        list
    }
}

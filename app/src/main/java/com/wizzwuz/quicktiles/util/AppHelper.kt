package com.wizzwuz.quicktiles.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
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

    fun testLaunchActivity(context: Context, packageName: String, activityName: String): Result<Unit> {
        return try {
            val intent = Intent().apply {
                component = ComponentName(packageName, activityName)
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
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

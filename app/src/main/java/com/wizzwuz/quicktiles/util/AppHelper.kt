package com.wizzwuz.quicktiles.util

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap

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

/**
 * Represents a single shortcut within an app (static manifest shortcuts).
 */
data class AppShortcutItem(
    val id: String,
    val label: String,
    val longLabel: String? = null,
    val iconBitmap: Bitmap? = null,
    val intentUri: String,
    val packageName: String
)

/**
 * Represents an app that has shortcuts, along with all its shortcuts.
 */
data class AppWithShortcuts(
    val appLabel: String,
    val packageName: String,
    val appIcon: Drawable,
    val shortcuts: List<AppShortcutItem>
)

object AppHelper {

    private val activitiesCache = ConcurrentHashMap<String, List<ActivityItem>>()
    private val shortcutsCache = ConcurrentHashMap<String, List<AppWithShortcuts>>()


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
        val cached = activitiesCache[packageName]
        if (cached != null) return@withContext cached

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
        activitiesCache[packageName] = list
        list
    }

    suspend fun preloadAllActivities(
        context: Context,
        apps: List<AppItem>,
        onAppLoaded: (packageName: String, activities: List<ActivityItem>) -> Unit
    ) = withContext(Dispatchers.IO) {
        coroutineScope {
            apps.map { app ->
                async {
                    val acts = getAppActivities(context, app.packageName)
                    withContext(Dispatchers.Main) {
                        onAppLoaded(app.packageName, acts)
                    }
                }
            }.awaitAll()
        }
    }

    suspend fun launchActivityPrivileged(
        context: Context,
        packageName: String,
        activityName: String,
        isExported: Boolean? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val repo = com.wizzwuz.quicktiles.data.TileRepository.getInstance(context)
        val isPrivateLaunchable = repo.isPrivateActivityLaunchable()

        // 1. If explicitly exported or unknown, try standard Intent first
        if (isExported != false) {
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(packageName, activityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return@withContext Result.success("Launched activity directly")
            } catch (e: SecurityException) {
                // Not exported / Permission denied - fall through to privileged launch
            } catch (e: Throwable) {
                if (isExported == true) {
                    return@withContext Result.failure(e)
                }
            }
        }

        // If the activity is private / unexported, check if private activity launching is enabled in settings
        if (!isPrivateLaunchable) {
            return@withContext Result.failure(
                IllegalStateException("Private activity launch is disabled in Settings. Enable 'Private Activity Launchable' in Settings to launch private activities.")
            )
        }

        val componentStr = "$packageName/$activityName"

        // 2. Check if Activity Launcher Shizuku Plugin is installed
        try {
            val targetIntent = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(packageName, activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pluginIntent = Intent("activitylauncher.intent.action.LAUNCH_ACTIVITY").apply {
                setPackage("net.activitylauncher.plugin.shizuku")
                putExtra("intent", targetIntent.toUri(Intent.URI_INTENT_SCHEME))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.queryIntentActivities(pluginIntent, 0).isNotEmpty()) {
                context.startActivity(pluginIntent)
                return@withContext Result.success("Launched via Activity Launcher Shizuku Plugin")
            }
        } catch (e: Throwable) {
            // plugin failed, continue to other strategies
        }

        // 3. Strategy: Superuser Root (su)
        // Root has UID 0 and can start ANY activity regardless of exported=false
        if (ShizukuHelper.isRootBinaryPresent()) {
            val escapedForShell = componentStr.replace("$", "\\$")
            val rootRes = ShizukuHelper.executeCommand("am start -a android.intent.action.MAIN -n $escapedForShell", mode = ShellMode.ROOT)
            val hasRootDenial = rootRes.output.contains("SecurityException") ||
                    rootRes.output.contains("Permission Denial") ||
                    rootRes.output.contains("Error: Activity class")
            if (rootRes.exitCode == 0 && !hasRootDenial) {
                return@withContext Result.success("Launched via Root (su)")
            }

            // Fallback without action
            val rootRes2 = ShizukuHelper.executeCommand("am start -n $escapedForShell", mode = ShellMode.ROOT)
            if (rootRes2.exitCode == 0 && !rootRes2.output.contains("Error: Activity class")) {
                return@withContext Result.success("Launched via Root (su)")
            }
        }

        // 4. Strategy: Shizuku Direct Raw am process (bypasses shell, preserves inner class names)
        if (ShizukuHelper.isShizukuRunning() && ShizukuHelper.hasShizukuPermission()) {
            // Try raw command with ACTION_MAIN
            val rawRes1 = ShizukuHelper.executePrivilegedRaw(
                arrayOf("am", "start", "-a", "android.intent.action.MAIN", "-n", componentStr)
            )
            val hasDenial1 = rawRes1.output.contains("SecurityException") ||
                    rawRes1.output.contains("Permission Denial") ||
                    rawRes1.output.contains("not exported") ||
                    rawRes1.output.contains("requires permission") ||
                    rawRes1.output.contains("Error:")

            if (rawRes1.exitCode == 0 && !hasDenial1) {
                return@withContext Result.success("Launched via Shizuku")
            }

            // Try raw command with --user 0
            val rawRes2 = ShizukuHelper.executePrivilegedRaw(
                arrayOf("am", "start", "--user", "0", "-a", "android.intent.action.MAIN", "-n", componentStr)
            )
            val hasDenial2 = rawRes2.output.contains("SecurityException") ||
                    rawRes2.output.contains("Permission Denial") ||
                    rawRes2.output.contains("not exported") ||
                    rawRes2.output.contains("requires permission") ||
                    rawRes2.output.contains("Error:")

            if (rawRes2.exitCode == 0 && !hasDenial2) {
                return@withContext Result.success("Launched via Shizuku (--user 0)")
            }

            // Try simple -n componentStr
            val rawRes3 = ShizukuHelper.executePrivilegedRaw(
                arrayOf("am", "start", "-n", componentStr)
            )
            val hasDenial3 = rawRes3.output.contains("SecurityException") ||
                    rawRes3.output.contains("Permission Denial") ||
                    rawRes3.output.contains("not exported") ||
                    rawRes3.output.contains("requires permission") ||
                    rawRes3.output.contains("Error:")

            if (rawRes3.exitCode == 0 && !hasDenial3) {
                return@withContext Result.success("Launched via Shizuku")
            }

            // Try cmd activity start (direct system service command)
            val rawRes4 = ShizukuHelper.executePrivilegedRaw(
                arrayOf("cmd", "activity", "start", "-a", "android.intent.action.MAIN", "-n", componentStr)
            )
            val hasDenial4 = rawRes4.output.contains("SecurityException") ||
                    rawRes4.output.contains("Permission Denial") ||
                    rawRes4.output.contains("not exported") ||
                    rawRes4.output.contains("requires permission") ||
                    rawRes4.output.contains("Error:")

            if (rawRes4.exitCode == 0 && !hasDenial4) {
                return@withContext Result.success("Launched via Shizuku (cmd activity)")
            }

            val rawRes5 = ShizukuHelper.executePrivilegedRaw(
                arrayOf("cmd", "activity", "start", "-n", componentStr)
            )
            val hasDenial5 = rawRes5.output.contains("SecurityException") ||
                    rawRes5.output.contains("Permission Denial") ||
                    rawRes5.output.contains("not exported") ||
                    rawRes5.output.contains("requires permission") ||
                    rawRes5.output.contains("Error:")

            if (rawRes5.exitCode == 0 && !hasDenial5) {
                return@withContext Result.success("Launched via Shizuku (cmd activity)")
            }

            // 5. Strategy: Assistant Method
            val assistantRes = launchViaAssistantMethod(context, componentStr)
            if (assistantRes.isSuccess) {
                return@withContext assistantRes
            }

            // If we reached here, Shizuku is running and permission granted, but Android/MIUI returned Permission Denial
            val errOutput = if (rawRes1.output.isNotBlank()) rawRes1.output else rawRes2.output
            val denialExplanation = if (errOutput.contains("not exported") || errOutput.contains("Permission Denial")) {
                "Android security policy blocked UID 2000 for this private activity.\nTip: Use Superuser (su) root, enable 'USB debugging (Security settings)' in Developer Options, or install Activity Launcher Shizuku Plugin."
            } else {
                errOutput
            }
            LogManager.logError("ActivityLaunch", "Failed to launch $componentStr via Shizuku: $denialExplanation")
            return@withContext Result.failure(Exception(denialExplanation))
        }

        // If Shizuku is running but permission not granted
        if (ShizukuHelper.isShizukuRunning() && !ShizukuHelper.hasShizukuPermission()) {
            withContext(Dispatchers.Main) {
                ShizukuHelper.requestShizukuPermission()
            }
            return@withContext Result.failure(
                SecurityException("Shizuku permission not granted. Please allow QuickSetTiles in Shizuku / Shevery.")
            )
        }

        // If neither Root nor Shizuku is running
        return@withContext Result.failure(
            IllegalStateException("Launching private activities requires Shizuku or Superuser root. Please start Shizuku / Shevery.")
        )
    }

    private suspend fun launchViaAssistantMethod(
        context: Context,
        componentStr: String
    ): Result<String> {
        try {
            val origRes = ShizukuHelper.executePrivilegedCommand("settings get secure assistant")
            val originalAssistant = origRes.output.trim()

            val origVisRes = ShizukuHelper.executePrivilegedCommand("settings get secure voice_interaction_service")
            val originalVis = origVisRes.output.trim()

            try {
                if (originalVis.isNotBlank() && originalVis != "null") {
                    ShizukuHelper.executePrivilegedCommand("settings put secure voice_interaction_service \"\"")
                }

                // Set assistant to target activity
                val setRes = ShizukuHelper.executePrivilegedCommand("settings put secure assistant $componentStr")
                if (setRes.exitCode != 0) {
                    return Result.failure(Exception("Failed to set assistant: ${setRes.output}"))
                }

                // Trigger assist launch via SearchManager reflection
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

                // Inject KEYCODE_ASSIST (219) via privileged shell (NO implicit am start -a ASSIST)
                ShizukuHelper.executePrivilegedCommand("input keyevent 219")
                delay(600)

                return Result.success("Launched private activity via Assistant Method")
            } finally {
                // Restore original assistant & voice_interaction_service
                if (originalAssistant.isNotBlank() && originalAssistant != "null") {
                    ShizukuHelper.executePrivilegedCommand("settings put secure assistant $originalAssistant")
                } else {
                    ShizukuHelper.executePrivilegedCommand("settings delete secure assistant")
                }
                if (originalVis.isNotBlank() && originalVis != "null") {
                    ShizukuHelper.executePrivilegedCommand("settings put secure voice_interaction_service $originalVis")
                } else {
                    ShizukuHelper.executePrivilegedCommand("settings delete secure voice_interaction_service")
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

    /**
     * Extracts all apps that have static launcher shortcuts defined via
     * <meta-data android:name="android.app.shortcuts"> in their launcher activities.
     * Parses the shortcuts XML resource to extract label, long label, icon, and intent.
     */
    /**
     * Extracts all apps that have shortcuts defined in their manifest
     * via <meta-data android:name="android.app.shortcuts">.
     * Parses the shortcuts XML resource to extract label, long label, icon, and intent.
     */
    suspend fun getAppsWithShortcuts(context: Context): List<AppWithShortcuts> = withContext(Dispatchers.IO) {
        val cacheKey = "all"
        shortcutsCache[cacheKey]?.let { return@withContext it }

        val pm = context.packageManager

        // 1. Gather candidate components from launcher activities (includes aliases)
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val launcherActivities = try {
            pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        } catch (e: Throwable) {
            try { pm.queryIntentActivities(launcherIntent, 0) } catch (e: Throwable) { emptyList() }
        }

        // Map of packageName -> Set of ComponentName candidates
        val packageComponents = mutableMapOf<String, MutableSet<ComponentName>>()
        for (resolveInfo in launcherActivities) {
            val actInfo = resolveInfo.activityInfo ?: continue
            val pkg = actInfo.packageName ?: continue
            packageComponents.getOrPut(pkg) { mutableSetOf() }.add(ComponentName(pkg, actInfo.name))
        }

        // 2. Also query all installed packages to ensure any app declaring shortcuts
        // on non-launcher activities is not missed
        val installedPackages = try {
            pm.getInstalledPackages(PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA)
        } catch (e: Throwable) {
            emptyList()
        }
        for (pkgInfo in installedPackages) {
            val pkg = pkgInfo.packageName ?: continue
            val activities = pkgInfo.activities ?: continue
            for (act in activities) {
                if (act.metaData?.containsKey("android.app.shortcuts") == true) {
                    packageComponents.getOrPut(pkg) { mutableSetOf() }.add(ComponentName(pkg, act.name))
                }
            }
        }

        val result = mutableListOf<AppWithShortcuts>()

        for ((pkg, components) in packageComponents) {
            val appLabel = try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Throwable) {
                pkg
            }

            val appIcon = try {
                pm.getApplicationIcon(pkg)
            } catch (e: Throwable) {
                pm.defaultActivityIcon
            }

            val appIconBitmap = try {
                IconProcessor.drawableToBitmap(appIcon, 96, 96)
            } catch (e: Throwable) {
                null
            }

            val appShortcuts = mutableListOf<AppShortcutItem>()
            val seenShortcutIds = mutableSetOf<String>()

            for (comp in components) {
                val actInfo = try {
                    pm.getActivityInfo(comp, PackageManager.GET_META_DATA)
                } catch (e: Throwable) {
                    null
                } ?: continue

                val metaData = actInfo.metaData ?: continue
                val shortcutsResId = try {
                    when (val raw = metaData.get("android.app.shortcuts")) {
                        is Int -> raw
                        is Number -> raw.toInt()
                        else -> metaData.getInt("android.app.shortcuts", 0)
                    }
                } catch (e: Throwable) {
                    0
                }

                if (shortcutsResId == 0) continue

                try {
                    val appRes = pm.getResourcesForApplication(pkg)
                    val parser = appRes.getXml(shortcutsResId)

                    var shortcutId = ""
                    var shortcutLabel = ""
                    var shortcutLongLabel: String? = null
                    var shortcutIconResId = 0
                    var insideShortcut = false
                    val intents = mutableListOf<Intent>()

                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        val tagName = parser.name ?: ""

                        when {
                            eventType == XmlPullParser.START_TAG && tagName == "shortcut" -> {
                                insideShortcut = true
                                shortcutId = ""
                                shortcutLabel = ""
                                shortcutLongLabel = null
                                shortcutIconResId = 0
                                intents.clear()

                                for (i in 0 until parser.attributeCount) {
                                    val attrName = parser.getAttributeName(i).substringAfterLast(":")
                                    val attrValue = parser.getAttributeValue(i)
                                    when (attrName) {
                                        "shortcutId" -> shortcutId = attrValue ?: ""
                                        "shortcutShortLabel" -> {
                                            val resId = parser.getAttributeResourceValue(i, 0)
                                            shortcutLabel = if (resId != 0) {
                                                try { appRes.getString(resId) } catch (e: Throwable) { attrValue ?: "" }
                                            } else { attrValue ?: "" }
                                        }
                                        "shortcutLongLabel" -> {
                                            val resId = parser.getAttributeResourceValue(i, 0)
                                            shortcutLongLabel = if (resId != 0) {
                                                try { appRes.getString(resId) } catch (e: Throwable) { attrValue }
                                            } else { attrValue }
                                        }
                                        "icon" -> {
                                            shortcutIconResId = parser.getAttributeResourceValue(i, 0)
                                            if (shortcutIconResId == 0 && attrValue?.startsWith("@") == true) {
                                                try { shortcutIconResId = attrValue.substring(1).toInt() } catch (e: Throwable) {}
                                            }
                                        }
                                    }
                                }
                            }

                            eventType == XmlPullParser.START_TAG && tagName == "intent" && insideShortcut -> {
                                var action = "android.intent.action.MAIN"
                                var intentPkg = pkg
                                var intentClass = ""
                                var intentData: String? = null

                                for (i in 0 until parser.attributeCount) {
                                    val attrName = parser.getAttributeName(i).substringAfterLast(":")
                                    val attrValue = parser.getAttributeValue(i) ?: continue
                                    when (attrName) {
                                        "action" -> action = attrValue
                                        "targetPackage" -> intentPkg = attrValue
                                        "targetClass" -> intentClass = attrValue
                                        "data" -> intentData = attrValue
                                    }
                                }

                                if (!action.contains(".")) {
                                    action = "android.intent.action.$action"
                                }

                                try {
                                    val intent = Intent(action).apply {
                                        if (intentClass.isNotBlank()) {
                                            component = ComponentName(intentPkg, intentClass)
                                        } else {
                                            setPackage(intentPkg)
                                        }
                                        if (!intentData.isNullOrBlank()) {
                                            data = Uri.parse(intentData)
                                        }
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    intents.add(intent)
                                } catch (e: Throwable) { /* skip malformed intent */ }
                            }

                            eventType == XmlPullParser.END_TAG && tagName == "shortcut" && insideShortcut -> {
                                insideShortcut = false

                                val effectiveLabel = when {
                                    shortcutLabel.isNotBlank() -> shortcutLabel
                                    !shortcutLongLabel.isNullOrBlank() -> shortcutLongLabel
                                    shortcutId.isNotBlank() -> shortcutId.replace("_", " ").replace("-", " ")
                                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                    else -> ""
                                }

                                if (effectiveLabel.isNotBlank()) {
                                    val targetIntent = intents.firstOrNull() ?: try {
                                        pm.getLaunchIntentForPackage(pkg)
                                    } catch (e: Throwable) { null }

                                    val intentUri = try {
                                        targetIntent?.toUri(Intent.URI_INTENT_SCHEME) ?: ""
                                    } catch (e: Throwable) { "" }

                                    val uniqueKey = shortcutId.ifBlank { effectiveLabel }
                                    if (intentUri.isNotBlank() && uniqueKey !in seenShortcutIds) {
                                        seenShortcutIds.add(uniqueKey)

                                        val iconBitmap: Bitmap? = if (shortcutIconResId != 0) {
                                            try {
                                                val drawable = appRes.getDrawable(shortcutIconResId, null)
                                                IconProcessor.drawableToBitmap(drawable, 96, 96)
                                            } catch (e: Throwable) { null }
                                        } else null

                                        val finalBitmap = iconBitmap ?: appIconBitmap

                                        appShortcuts.add(
                                            AppShortcutItem(
                                                id = uniqueKey,
                                                label = effectiveLabel,
                                                longLabel = shortcutLongLabel,
                                                iconBitmap = finalBitmap,
                                                intentUri = intentUri,
                                                packageName = pkg
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        eventType = parser.next()
                    }
                    parser.close()
                } catch (e: Throwable) {
                    // Skip unparseable XML
                }
            }

            if (appShortcuts.isNotEmpty()) {
                result.add(AppWithShortcuts(appLabel, pkg, appIcon, appShortcuts))
            }
        }

        result.sortBy { it.appLabel.lowercase() }
        shortcutsCache[cacheKey] = result
        result
    }
}

package com.wizzwuz.quicksettiles.ui.split

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.wizzwuz.quicksettiles.util.LogManager

/**
 * High-performance transparent trampoline activity to launch dual applications in split-screen mode
 * without requiring any Android Accessibility permission.
 *
 * Optimizations:
 * 1. Multi-tier intent resolution: resolves any launcher, info, or exported activity so all apps work.
 * 2. Smart flag allocation: avoids FLAG_ACTIVITY_MULTIPLE_TASK on singleTask/singleInstance apps to prevent launch aborts.
 * 3. Zero-animation transitions: disables window scaling animations to eliminate lag and visual stutter.
 * 4. Snappy 150ms pipeline: synchronizes both split viewports smoothly and instantly.
 */
class LaunchSplitActivity : Activity() {

    companion object {
        const val EXTRA_SPLIT_DATA = "split_data"
        const val EXTRA_SPLIT_TOP = "com.wizzwuz.quicksettiles.split.top"
        const val EXTRA_SPLIT_BOTTOM = "com.wizzwuz.quicksettiles.split.bottom"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)
        try {
            window.attributes.windowAnimations = 0
        } catch (_: Throwable) {}

        val splitData = intent.getStringExtra(EXTRA_SPLIT_DATA)
        val splitTop = intent.getStringExtra(EXTRA_SPLIT_TOP)
        val splitBottom = intent.getStringExtra(EXTRA_SPLIT_BOTTOM)

        val topTarget: String
        val bottomTarget: String

        if (!splitTop.isNullOrBlank() && !splitBottom.isNullOrBlank()) {
            topTarget = splitTop.trim()
            bottomTarget = splitBottom.trim()
        } else if (!splitData.isNullOrBlank()) {
            val parts = splitData.split("|")
            if (parts.size >= 2) {
                topTarget = parts[0].trim()
                bottomTarget = parts[1].trim()
            } else {
                Toast.makeText(this, "QuickSetTiles: Invalid split apps configuration", Toast.LENGTH_SHORT).show()
                finishAndRemoveTask()
                return
            }
        } else {
            finishAndRemoveTask()
            return
        }

        val intent1 = createTargetIntent(topTarget)
        val intent2 = createTargetIntent(bottomTarget)

        if (intent1 == null || intent2 == null) {
            Toast.makeText(this, "QuickSetTiles: Could not resolve apps for split screen", Toast.LENGTH_SHORT).show()
            finishAndRemoveTask()
            return
        }

        // App 1 (Primary / Top): FLAG_ACTIVITY_NEW_TASK with zero animation
        intent1.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_NO_ANIMATION
        )

        // App 2 (Adjacent / Bottom): FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_LAUNCH_ADJACENT with zero animation
        intent2.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
            Intent.FLAG_ACTIVITY_NO_ANIMATION
        )

        // If pairing the exact same app with itself (e.g. Chrome + Chrome), multiple tasks are required
        if (topTarget == bottomTarget) {
            intent2.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }

        // Configure zero-latency custom animation options with windowing mode hints
        val topOptions: Bundle? = try {
            val opt = ActivityOptions.makeCustomAnimation(this, 0, 0)
            try {
                val method = opt.javaClass.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                method.invoke(opt, 3) // WINDOWING_MODE_SPLIT_SCREEN_PRIMARY
            } catch (_: Throwable) {}
            opt.toBundle()
        } catch (_: Throwable) {
            null
        }

        val bottomOptions: Bundle? = try {
            val opt = ActivityOptions.makeCustomAnimation(this, 0, 0)
            try {
                val method = opt.javaClass.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                method.invoke(opt, 4) // WINDOWING_MODE_SPLIT_SCREEN_SECONDARY
            } catch (_: Throwable) {}
            opt.toBundle()
        } catch (_: Throwable) {
            null
        }

        try {
            // 1. Immediately launch primary app
            if (topOptions != null) {
                try {
                    startActivity(intent1, topOptions)
                } catch (_: Throwable) {
                    startActivity(intent1)
                }
            } else {
                startActivity(intent1)
            }
            overridePendingTransition(0, 0)
        } catch (e: Throwable) {
            LogManager.logError("LaunchSplitActivity", "Failed to start intent1", e)
        }

        // 2. Launch secondary app adjacent after snappy 150ms delay to allow task registration
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (bottomOptions != null) {
                    try {
                        startActivity(intent2, bottomOptions)
                    } catch (_: Throwable) {
                        startActivity(intent2)
                    }
                } else {
                    startActivity(intent2)
                }
                overridePendingTransition(0, 0)
            } catch (e: Throwable) {
                LogManager.logError("LaunchSplitActivity", "Failed to start intent2", e)
            } finally {
                finishAndRemoveTask()
                overridePendingTransition(0, 0)
            }
        }, 150)
    }

    private fun createTargetIntent(target: String): Intent? {
        if (target.startsWith("#Intent") || target.startsWith("intent:")) {
            try {
                val parsed = Intent.parseUri(target, 0)
                if (parsed != null) return parsed
            } catch (e: Throwable) {
                LogManager.logError("LaunchSplitActivity", "Failed to parse URI: $target", e)
            }
        }

        // 1. Standard package launch intent
        val launchIntent = packageManager.getLaunchIntentForPackage(target)
        if (launchIntent != null) {
            return launchIntent
        }

        // 2. Explicit query for MAIN + LAUNCHER
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                `package` = target
            }
            val matches = packageManager.queryIntentActivities(mainIntent, 0)
            if (matches.isNotEmpty()) {
                val actInfo = matches[0].activityInfo
                return Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName(actInfo.packageName, actInfo.name)
                }
            }
        } catch (_: Throwable) {}

        // 3. Explicit query for MAIN + INFO (settings, TV, system utilities)
        try {
            val infoIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_INFO)
                `package` = target
            }
            val matches = packageManager.queryIntentActivities(infoIntent, 0)
            if (matches.isNotEmpty()) {
                val actInfo = matches[0].activityInfo
                return Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_INFO)
                    component = ComponentName(actInfo.packageName, actInfo.name)
                }
            }
        } catch (_: Throwable) {}

        // 4. Fallback to any exported activity in the package
        try {
            val anyIntent = Intent().apply { `package` = target }
            val matches = packageManager.queryIntentActivities(anyIntent, 0)
            val exported = matches.firstOrNull { it.activityInfo != null && it.activityInfo.exported }
            if (exported != null) {
                return Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(exported.activityInfo.packageName, exported.activityInfo.name)
                }
            }
        } catch (_: Throwable) {}

        return null
    }
}

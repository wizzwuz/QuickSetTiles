package com.wizzwuz.quicksettiles.ui.split

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.wizzwuz.quicksettiles.util.LogManager

/**
 * Dedicated transparent trampoline activity to launch dual applications in split-screen mode
 * without requiring any Android Accessibility permission.
 *
 * Implements the exact proven multi-window mechanism from Shortcut Maker:
 * 1. Configures primary app with FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_SINGLE_TOP.
 * 2. Configures secondary app with FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_LAUNCH_ADJACENT | FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_MULTIPLE_TASK.
 * 3. Immediately launches primary app while activity is in the foreground.
 * 4. After 350ms, launches secondary app into the adjacent split container and exits cleanly.
 */
class LaunchSplitActivity : Activity() {

    companion object {
        const val EXTRA_SPLIT_DATA = "split_data"
        const val EXTRA_SPLIT_TOP = "shortcutmaker.split.top"
        const val EXTRA_SPLIT_BOTTOM = "shortcutmaker.split.bottom"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)

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

        // Top Intent flags (from Shortcut Maker smali: 0x10000000 | 0x20000 | 0x20000000)
        intent1.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        )

        // Bottom Intent flags (from Shortcut Maker smali: 0x10000000 | 0x1000 | 0x20000 | 0x20000000 | 0x8000000)
        intent2.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        )

        // Optional launch options for modern Android (windowing mode hint 4 = SPLIT_SCREEN_SECONDARY)
        val bottomOptions: Bundle? = try {
            val opt = ActivityOptions.makeBasic()
            try {
                val method = opt.javaClass.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                method.invoke(opt, 4) // 4 = WINDOWING_MODE_SPLIT_SCREEN_SECONDARY
            } catch (_: Throwable) {}
            opt.toBundle()
        } catch (_: Throwable) {
            null
        }

        try {
            // 1. Immediately launch primary app while LaunchSplitActivity is focused in the foreground
            startActivity(intent1)
        } catch (e: Throwable) {
            LogManager.logError("LaunchSplitActivity", "Failed to start intent1", e)
        }

        // 2. Launch secondary app adjacent after 350ms (0x15e in Shortcut Maker)
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (bottomOptions != null) {
                    startActivity(intent2, bottomOptions)
                } else {
                    startActivity(intent2)
                }
            } catch (e: Throwable) {
                LogManager.logError("LaunchSplitActivity", "Failed to start intent2", e)
            } finally {
                finishAndRemoveTask()
                overridePendingTransition(0, 0)
            }
        }, 350)
    }

    private fun createTargetIntent(target: String): Intent? {
        return if (target.startsWith("#Intent") || target.startsWith("intent:")) {
            try {
                Intent.parseUri(target, 0)
            } catch (e: Throwable) {
                null
            }
        } else {
            packageManager.getLaunchIntentForPackage(target)?.apply {
                setPackage(target)
            }
        }
    }
}

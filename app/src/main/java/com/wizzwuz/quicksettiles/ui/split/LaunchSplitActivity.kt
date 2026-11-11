package com.wizzwuz.quicksettiles.ui.split

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.wizzwuz.quicksettiles.util.LogManager
import com.wizzwuz.quicksettiles.util.ShizukuHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Transparent trampoline activity that launches two apps in split-screen mode.
 *
 * Flow:
 * 1. Launch app 1 in a new task.
 * 2. If Shizuku/root available, try to enter split-screen mode.
 * 3. Launch app 2 with FLAG_ACTIVITY_LAUNCH_ADJACENT.
 */
class LaunchSplitActivity : Activity() {

    companion object {
        const val EXTRA_SPLIT_DATA = "split_data"
        const val EXTRA_SPLIT_TOP = "shortcutmaker.split.top"
        const val EXTRA_SPLIT_BOTTOM = "shortcutmaker.split.bottom"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        intent1.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
            Intent.FLAG_ACTIVITY_NO_ANIMATION
        )

        intent2.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
            Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
            Intent.FLAG_ACTIVITY_NO_ANIMATION
        )

        val mainHandler = Handler(Looper.getMainLooper())

        try {
            startActivity(intent1)
        } catch (e: Throwable) {
            LogManager.logError("LaunchSplitActivity", "App 1 launch error", e)
            Toast.makeText(this, "QuickSetTiles: Failed to launch first app", Toast.LENGTH_SHORT).show()
            finishAndRemoveTask()
            return
        }

        // Best-effort: enter split-screen mode on devices where privileged command is available.
        if ((ShizukuHelper.isShizukuRunning() && ShizukuHelper.hasShizukuPermission()) || ShizukuHelper.isRootBinaryPresent()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    delay(250)
                    ShizukuHelper.executePrivilegedCommand("cmd statusbar toggle-split-screen")
                } catch (e: Throwable) {
                    LogManager.logError("LaunchSplitActivity", "toggle-split-screen error", e)
                }
            }
        }

        mainHandler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            try {
                startActivity(intent2)
            } catch (e: Throwable) {
                LogManager.logError("LaunchSplitActivity", "App 2 launch error", e)
                Toast.makeText(this, "QuickSetTiles: Failed to launch second app", Toast.LENGTH_SHORT).show()
            }

            mainHandler.postDelayed({
                try {
                    finishAndRemoveTask()
                    overridePendingTransition(0, 0)
                } catch (_: Throwable) {
                }
            }, 300)
        }, 700)
    }

    private fun createTargetIntent(target: String): Intent? {
        return if (target.startsWith("#Intent") || target.startsWith("intent:")) {
            try {
                Intent.parseUri(target, Intent.URI_INTENT_SCHEME)
            } catch (e: Throwable) {
                LogManager.logError("LaunchSplitActivity", "Intent parse error: $target", e)
                null
            }
        } else {
            packageManager.getLaunchIntentForPackage(target)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
        }
    }
}

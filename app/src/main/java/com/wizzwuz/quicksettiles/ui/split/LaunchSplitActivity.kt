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
 * Dedicated transparent trampoline activity to launch dual applications in split-screen mode
 * without requiring any Android Accessibility permission.
 *
 * Utilizes the proven multi-window home-clearing and adjacent docking technique:
 * 1. Clears launcher state by bringing Home forward.
 * 2. Launches App 1 with FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_MULTIPLE_TASK.
 * 3. Optionally triggers toggle-split-screen if Shizuku / root is available.
 * 4. Launches App 2 with FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_LAUNCH_ADJACENT | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_MULTIPLE_TASK | FLAG_ACTIVITY_REORDER_TO_FRONT.
 * 5. Cleanly terminates and removes its task.
 */
class LaunchSplitActivity : Activity() {

    companion object {
        const val EXTRA_SPLIT_DATA = "split_data"
        const val EXTRA_SPLIT_TOP = "com.wizzwuz.quicksettiles.split.top"
        const val EXTRA_SPLIT_BOTTOM = "com.wizzwuz.quicksettiles.split.bottom"
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

        // Apply multi-window launch flags
        // App 1 (Primary / Top): FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_MULTIPLE_TASK
        intent1.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        )

        // App 2 (Adjacent / Bottom): FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_LAUNCH_ADJACENT | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_MULTIPLE_TASK | FLAG_ACTIVITY_REORDER_TO_FRONT
        intent2.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )

        val mainHandler = Handler(Looper.getMainLooper())

        // 1. Send Home intent first to ensure launcher state is clean and ready for adjacent window docking
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(homeIntent)
        } catch (e: Throwable) {
            LogManager.logError("LaunchSplitActivity", "Home intent error", e)
        }

        // 2. Launch App 1 after short delay (450ms)
        mainHandler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            try {
                startActivity(intent1)
            } catch (e: Throwable) {
                LogManager.logError("LaunchSplitActivity", "App 1 launch error", e)
            }

            // If Shizuku or root is available, trigger toggle-split-screen to enforce multi-window mode
            if ((ShizukuHelper.isShizukuRunning() && ShizukuHelper.hasShizukuPermission()) || ShizukuHelper.isRootBinaryPresent()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        delay(200)
                        ShizukuHelper.executePrivilegedCommand("cmd statusbar toggle-split-screen")
                    } catch (e: Throwable) {
                        LogManager.logError("LaunchSplitActivity", "toggle-split-screen error", e)
                    }
                }
            }

            // 3. Launch App 2 adjacent after 300ms
            mainHandler.postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed
                try {
                    startActivity(intent2)
                } catch (e: Throwable) {
                    LogManager.logError("LaunchSplitActivity", "App 2 launch error", e)
                }

                // 4. Cleanly finish and remove task
                mainHandler.postDelayed({
                    try {
                        finishAndRemoveTask()
                        overridePendingTransition(0, 0)
                    } catch (e: Throwable) {}
                }, 250)
            }, 300)
        }, 450)
    }

    private fun createTargetIntent(target: String): Intent? {
        return if (target.startsWith("#Intent") || target.startsWith("intent:")) {
            try {
                Intent.parseUri(target, Intent.URI_INTENT_SCHEME)
            } catch (e: Throwable) {
                null
            }
        } else {
            packageManager.getLaunchIntentForPackage(target)
        }
    }
}

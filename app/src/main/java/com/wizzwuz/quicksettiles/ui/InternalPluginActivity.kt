package com.wizzwuz.quicksettiles.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.wizzwuz.quicksettiles.util.AppHelper
import com.wizzwuz.quicksettiles.util.ShizukuHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Inbuilt compatibility activity for ActivityLauncher and external apps.
 * Implements the action: activitylauncher.intent.action.LAUNCH_ACTIVITY
 * Drops the need for any external Shizuku plugin APK.
 */
class InternalPluginActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val ACTION_LAUNCH_ACTIVITY = "activitylauncher.intent.action.LAUNCH_ACTIVITY"
        const val EXTRA_INTENT = "intent"
        const val EXTRA_INTENT_ALT = "extra_intent"
        const val ACTION_SHOW_POPUP = "com.wizzwuz.quicksettiles.action.SHOW_POPUP"
        const val EXTRA_MESSAGE = "extra_message"
    }

    private fun cleanFinish() {
        if (!isFinishing && !isDestroyed) {
            finishAndRemoveTask()
            overridePendingTransition(0, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent.action
        if (action == ACTION_LAUNCH_ACTIVITY) {
            val repo = com.wizzwuz.quicksettiles.data.TileRepository.getInstance(this)
            val caller = callingPackage ?: (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try { launchedFromPackage } catch (e: Throwable) { null }
            } else null)

            val isInternal = caller == null || caller == packageName
            if (!isInternal && !repo.isPrivateActivityLaunchable()) {
                Toast.makeText(this, "QuickSetTiles: External launching of private activities is disabled in settings.", Toast.LENGTH_LONG).show()
                cleanFinish()
                return
            }
            handleLaunchIntent()
        } else if (action == ACTION_SHOW_POPUP) {
            val msg = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
            if (msg.isNotBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
            cleanFinish()
        } else if (action == "launch_split_apps") {
            val splitData = intent.getStringExtra("split_data") ?: ""
            val parts = splitData.split("|")
            val launchIntent = Intent(this, com.wizzwuz.quicksettiles.ui.split.LaunchSplitActivity::class.java).apply {
                putExtra(com.wizzwuz.quicksettiles.ui.split.LaunchSplitActivity.EXTRA_SPLIT_DATA, splitData)
                if (parts.size >= 2) {
                    val uri1 = packageManager.getLaunchIntentForPackage(parts[0].trim())?.toUri(0) ?: parts[0].trim()
                    val uri2 = packageManager.getLaunchIntentForPackage(parts[1].trim())?.toUri(0) ?: parts[1].trim()
                    putExtra(com.wizzwuz.quicksettiles.ui.split.LaunchSplitActivity.EXTRA_SPLIT_TOP, uri1)
                    putExtra(com.wizzwuz.quicksettiles.ui.split.LaunchSplitActivity.EXTRA_SPLIT_BOTTOM, uri2)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(launchIntent)
            cleanFinish()
        } else {
            cleanFinish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_SHOW_POPUP) {
            val msg = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
            if (msg.isNotBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
            cleanFinish()
        }
    }

    private fun handleLaunchIntent() {
        val intentUri = intent.getStringExtra(EXTRA_INTENT)
            ?: intent.getStringExtra(EXTRA_INTENT_ALT)

        if (intentUri.isNullOrBlank()) {
            Toast.makeText(this, "QuickSetTiles: No target intent specified", Toast.LENGTH_SHORT).show()
            cleanFinish()
            return
        }

        try {
            val targetIntent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
            val comp = targetIntent.component

            if (comp != null) {
                scope.launch {
                    val result = AppHelper.launchActivityPrivileged(
                        context = this@InternalPluginActivity,
                        packageName = comp.packageName,
                        activityName = comp.className,
                        isExported = null
                    )

                    result.onSuccess { msg ->
                        Toast.makeText(applicationContext, "QuickSetTiles: $msg", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(applicationContext, "QuickSetTiles Error: ${err.message}", Toast.LENGTH_LONG).show()
                        // If Shizuku permission is needed and Shizuku is running, prompt for permission
                        if (ShizukuHelper.isShizukuRunning() && !ShizukuHelper.hasShizukuPermission()) {
                            ShizukuHelper.requestShizukuPermission()
                        }
                    }
                    cleanFinish()
                }
            } else {
                // Try direct startActivity if no component
                try {
                    targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(targetIntent)
                } catch (e: Throwable) {
                    Toast.makeText(this, "QuickSetTiles Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                cleanFinish()
            }
        } catch (e: Throwable) {
            Toast.makeText(this, "QuickSetTiles Parse Error: ${e.message}", Toast.LENGTH_SHORT).show()
            cleanFinish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

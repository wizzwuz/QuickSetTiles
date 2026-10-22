package com.wizzwuz.quicktiles.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.wizzwuz.quicktiles.util.AppHelper
import com.wizzwuz.quicktiles.util.ShizukuHelper
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent.action
        if (action == ACTION_LAUNCH_ACTIVITY) {
            handleLaunchIntent()
        } else {
            finish()
        }
    }

    private fun handleLaunchIntent() {
        val intentUri = intent.getStringExtra(EXTRA_INTENT)
            ?: intent.getStringExtra(EXTRA_INTENT_ALT)

        if (intentUri.isNullOrBlank()) {
            Toast.makeText(this, "QuickSetTiles: No target intent specified", Toast.LENGTH_SHORT).show()
            finish()
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
                    finish()
                }
            } else {
                // Try direct startActivity if no component
                try {
                    targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(targetIntent)
                } catch (e: Throwable) {
                    Toast.makeText(this, "QuickSetTiles Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
        } catch (e: Throwable) {
            Toast.makeText(this, "QuickSetTiles Parse Error: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

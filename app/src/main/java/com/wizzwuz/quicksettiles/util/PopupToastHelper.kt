package com.wizzwuz.quicksettiles.util

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.wizzwuz.quicksettiles.ui.InternalPluginActivity

object PopupToastHelper {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun showToast(context: Context, message: String) {
        if (message.isBlank()) return

        // 1. Immediate main-thread toast
        mainHandler.post {
            try {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
            } catch (e: Throwable) {
                // Ignore
            }
        }

        // 2. Trigger via translucent InternalPluginActivity to bypass Android 12+ / MIUI background toast blocks
        try {
            val intent = Intent(context, InternalPluginActivity::class.java).apply {
                action = InternalPluginActivity.ACTION_SHOW_POPUP
                putExtra(InternalPluginActivity.EXTRA_MESSAGE, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            // Ignore
        }
    }
}

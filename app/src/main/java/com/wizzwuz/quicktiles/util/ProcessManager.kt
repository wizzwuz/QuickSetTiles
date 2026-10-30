package com.wizzwuz.quicktiles.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.util.concurrent.atomic.AtomicInteger

object ProcessManager {
    private val activeActivities = AtomicInteger(0)
    private val runningTasks = AtomicInteger(0)
    private val activeTileListeners = AtomicInteger(0)

    private val handler = Handler(Looper.getMainLooper())
    private var exitRunnable: Runnable? = null

    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                cancelPendingExit()
                activeActivities.incrementAndGet()
            }

            override fun onActivityResumed(activity: Activity) {
                cancelPendingExit()
            }

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                val remaining = activeActivities.decrementAndGet()
                if (remaining <= 0) {
                    activeActivities.set(0)
                    scheduleExitCheck(delayMs = 2500)
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                AppHelper.clearCaches()
                IconProcessor.clearCache()
                if (activeActivities.get() <= 0) {
                    scheduleExitCheck(delayMs = 1500)
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }

    fun onTileListeningStarted() {
        cancelPendingExit()
        activeTileListeners.incrementAndGet()
    }

    fun onTileListeningStopped() {
        val remaining = activeTileListeners.decrementAndGet()
        if (remaining <= 0) {
            activeTileListeners.set(0)
            scheduleExitCheck(delayMs = 3000)
        }
    }

    fun onTileTaskStarted() {
        cancelPendingExit()
        runningTasks.incrementAndGet()
    }

    fun onTileTaskFinished() {
        val remaining = runningTasks.decrementAndGet()
        if (remaining <= 0) {
            runningTasks.set(0)
            scheduleExitCheck(delayMs = 1500)
        }
    }

    private fun cancelPendingExit() {
        exitRunnable?.let { handler.removeCallbacks(it) }
        exitRunnable = null
    }

    fun scheduleExitCheck(delayMs: Long = 1500) {
        cancelPendingExit()
        val runnable = Runnable {
            if (activeActivities.get() == 0 && runningTasks.get() == 0) {
                AppHelper.clearCaches()
                IconProcessor.clearCache()
                Process.killProcess(Process.myPid())
            }
        }
        exitRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }
}

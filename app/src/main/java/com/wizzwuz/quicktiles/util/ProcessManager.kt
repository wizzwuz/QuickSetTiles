package com.wizzwuz.quicktiles.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.util.concurrent.atomic.AtomicInteger

object ProcessManager {
    // Number of visible/interactive activities
    private val startedActivities = AtomicInteger(0)
    // Number of activities that exist (between onCreate and onDestroy)
    private val aliveActivities = AtomicInteger(0)
    private val runningTasks = AtomicInteger(0)
    private val activeTileListeners = AtomicInteger(0)

    private val handler = Handler(Looper.getMainLooper())
    private var exitRunnable: Runnable? = null

    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                cancelPendingExit()
                aliveActivities.incrementAndGet()
            }

            override fun onActivityStarted(activity: Activity) {
                cancelPendingExit()
                startedActivities.incrementAndGet()
            }

            override fun onActivityResumed(activity: Activity) {
                cancelPendingExit()
            }

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                val remaining = startedActivities.decrementAndGet()
                if (remaining <= 0) {
                    startedActivities.set(0)
                }
                // When UI goes to background or user is in a file/folder/icon picker,
                // aggressively trim heavy memory caches WITHOUT killing the process
                AppHelper.clearCaches()
                IconProcessor.clearCache()
            }

            override fun onActivityDestroyed(activity: Activity) {
                val remaining = aliveActivities.decrementAndGet()
                if (remaining <= 0) {
                    aliveActivities.set(0)
                }
                AppHelper.clearCaches()
                IconProcessor.clearCache()
                // Only if ALL activities are destroyed and this activity was finishing
                if (aliveActivities.get() <= 0 && activity.isFinishing) {
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
            if (aliveActivities.get() <= 0) {
                scheduleExitCheck(delayMs = 3000)
            }
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
            if (aliveActivities.get() <= 0) {
                scheduleExitCheck(delayMs = 1500)
            }
        }
    }

    private fun cancelPendingExit() {
        exitRunnable?.let { handler.removeCallbacks(it) }
        exitRunnable = null
    }

    fun scheduleExitCheck(delayMs: Long = 1500) {
        cancelPendingExit()
        val runnable = Runnable {
            // NEVER kill the process if any activity is still alive (e.g. backgrounded or waiting for file picker)
            if (aliveActivities.get() <= 0 && runningTasks.get() == 0 && activeTileListeners.get() == 0) {
                AppHelper.clearCaches()
                IconProcessor.clearCache()
                Process.killProcess(Process.myPid())
            }
        }
        exitRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }
}

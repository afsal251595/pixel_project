package com.scientific.pixelgrid

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Application class tracking foreground lifecycle state.
 * Ensures touch tracking is disabled when our app is open,
 * so app interaction does not pollute scientific touch_log.txt.
 */
class PixelGridApplication : Application(), Application.ActivityLifecycleCallbacks {

    companion object {
        @Volatile
        var isAppInForeground: Boolean = false
            private set
    }

    private var resumedActivitiesCount = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivitiesCount++
        isAppInForeground = resumedActivitiesCount > 0
    }

    override fun onActivityPaused(activity: Activity) {
        resumedActivitiesCount = maxOf(0, resumedActivitiesCount - 1)
        isAppInForeground = resumedActivitiesCount > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

package com.scientific.pixelgrid.model

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

object DisplayMetricsHelper {

    /**
     * Obtains the 100% true physical screen dimensions (including status bar and navigation bar).
     */
    fun getPhysicalMetrics(context: Context): DisplayMetrics {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = wm.currentWindowMetrics
            metrics.widthPixels = windowMetrics.bounds.width()
            metrics.heightPixels = windowMetrics.bounds.height()
            metrics.xdpi = context.resources.displayMetrics.xdpi
            metrics.ydpi = context.resources.displayMetrics.ydpi
            metrics.densityDpi = context.resources.displayMetrics.densityDpi
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
        }

        // Fallback for devices where xdpi/ydpi is reported as 0 or wildly unrealistic
        if (metrics.xdpi < 100f || metrics.xdpi > 1000f) {
            metrics.xdpi = metrics.densityDpi.toFloat()
        }
        if (metrics.ydpi < 100f || metrics.ydpi > 1000f) {
            metrics.ydpi = metrics.densityDpi.toFloat()
        }

        return metrics
    }
}

package com.scientific.pixelgrid.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import com.scientific.pixelgrid.model.GridCalculator
import com.scientific.pixelgrid.storage.TouchFileLogger

/**
 * 24/7 Global Touch Interceptor using Android AccessibilityService.
 * Runs continuously across ALL apps without blocking or interfering with phone usage.
 * No PC or ADB setup required.
 */
class GlobalTouchAccessibilityService : AccessibilityService() {

    private val TAG = "GlobalTouchService"
    private lateinit var logger: TouchFileLogger
    private lateinit var gridCalculator: GridCalculator
    private var lastRecordedTime: Long = 0L

    companion object {
        var isServiceActive = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceActive = true
        logger = TouchFileLogger(this)

        val metrics = resources.displayMetrics
        gridCalculator = GridCalculator(
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            xdpi = metrics.xdpi,
            ydpi = metrics.ydpi
        )
        Log.d(TAG, "GlobalTouchAccessibilityService connected and actively tracking.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Deduplicate rapid duplicate events within 80ms
        val now = System.currentTimeMillis()
        if (now - lastRecordedTime < 80) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val node = event.source
                if (node != null) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    if (!bounds.isEmpty) {
                        val touchX = bounds.exactCenterX()
                        val touchY = bounds.exactCenterY()
                        val cell = gridCalculator.getCellNumber(touchX, touchY)
                        logger.recordTouch(cell)
                        lastRecordedTime = now
                        Log.d(TAG, "Global Touch logged: Cell #$cell at ($touchX, $touchY)")
                    }
                    try {
                        node.recycle()
                    } catch (e: Exception) {
                        // ignore recycle errors
                    }
                }
            }
        }
    }

    /**
     * Intercepts raw motion events on Android 12+ (API 31+) if supported.
     */
    fun onCustomMotionEvent(x: Float, y: Float) {
        val cell = gridCalculator.getCellNumber(x, y)
        logger.recordTouch(cell)
    }

    override fun onInterrupt() {
        Log.w(TAG, "GlobalTouchAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        isServiceActive = false
        super.onDestroy()
    }
}

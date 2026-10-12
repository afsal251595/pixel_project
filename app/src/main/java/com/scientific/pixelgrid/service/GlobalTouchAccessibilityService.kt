package com.scientific.pixelgrid.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import com.scientific.pixelgrid.model.DisplayMetricsHelper
import com.scientific.pixelgrid.model.GridCalculator
import com.scientific.pixelgrid.storage.TouchFileLogger

/**
 * 24/7 Global Touch Interceptor using Android AccessibilityService.
 * Fixes phantom repeated logs by removing TYPE_VIEW_FOCUSED and large background containers.
 * Uses exact physical motion coordinates where supported.
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
        logger = TouchFileLogger.getInstance(this)

        val metrics = DisplayMetricsHelper.getPhysicalMetrics(this)
        gridCalculator = GridCalculator(
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            xdpi = metrics.xdpi,
            ydpi = metrics.ydpi,
            targetCellSizeMm = 5.0f
        )

        val info = serviceInfo ?: AccessibilityServiceInfo()
        // ONLY listen to explicit click events to prevent phantom repeated logs from focus changes
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 50
        info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_SEND_MOTION_EVENTS
        }

        serviceInfo = info
        Log.d(TAG, "GlobalTouchAccessibilityService connected with physical metrics: ${gridCalculator.getSummary()}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Ignore everything except explicit user clicks
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return

        val now = System.currentTimeMillis()
        if (now - lastRecordedTime < 250) return // 250ms debounce

        val node = event.source ?: return
        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            // Filter out empty rects or entire screen container views (which cause repeated fake logs)
            val isEntireScreen = bounds.width() > (gridCalculator.screenWidthPx * 0.75f) &&
                    bounds.height() > (gridCalculator.screenHeightPx * 0.75f)

            if (!bounds.isEmpty && bounds.width() > 0 && bounds.height() > 0 && !isEntireScreen) {
                val touchX = bounds.exactCenterX()
                val touchY = bounds.exactCenterY()
                val cell = gridCalculator.getCellNumber(touchX, touchY)
                logger.recordTouch(cell)
                lastRecordedTime = now
                Log.d(TAG, "Valid Click Logged: Cell #$cell at ($touchX, $touchY)")
            }
        } finally {
            try { node.recycle() } catch (e: Exception) {}
        }
    }

    override fun onMotionEvent(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            if (now - lastRecordedTime >= 200) {
                lastRecordedTime = now
                val cell = gridCalculator.getCellNumber(event.rawX, event.rawY)
                logger.recordTouch(cell)
                Log.d(TAG, "Exact Raw MotionEvent: Cell #$cell at (${event.rawX}, ${event.rawY})")
            }
        }
        super.onMotionEvent(event)
    }

    override fun onInterrupt() {
        Log.w(TAG, "GlobalTouchAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        isServiceActive = false
        super.onDestroy()
    }
}

package com.scientific.pixelgrid.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.scientific.pixelgrid.model.DisplayMetricsHelper
import com.scientific.pixelgrid.model.GridCalculator
import com.scientific.pixelgrid.storage.TouchFileLogger

/**
 * 24/7 Crash-Proof Global Touch Interceptor using Android AccessibilityService.
 * Intercepts touches across home screen, apps, and keyboard.
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
            targetCellWidthMm = 7.0f,
            targetCellHeightMm = 10.0f
        )

        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_SELECTED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_TOUCH_INTERACTION_START or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 20
            info.flags = info.flags or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

            serviceInfo = info
        } catch (t: Throwable) {
            Log.e(TAG, "Error setting serviceInfo", t)
        }

        Log.d(TAG, "GlobalTouchAccessibilityService connected successfully.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val now = System.currentTimeMillis()
            if (now - lastRecordedTime < 100) return // 100ms debounce

            var node: AccessibilityNodeInfo? = null
            try {
                node = event.source
            } catch (t: Throwable) {
                // Ignore IPC retrieval error
            }

            if (node == null && event.recordCount > 0) {
                try {
                    node = event.getRecord(0)?.source
                } catch (t: Throwable) {
                    // Ignore record retrieval error
                }
            }

            if (node != null) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)

                // If bounds are valid, log the cell
                if (!bounds.isEmpty && bounds.width() > 0 && bounds.height() > 0) {
                    val touchX = bounds.exactCenterX()
                    val touchY = bounds.exactCenterY()
                    val cell = gridCalculator.getCellNumber(touchX, touchY)

                    logger.recordTouch(cell)
                    lastRecordedTime = now
                    Log.d(TAG, "Touch Logged: Cell #$cell at ($touchX, $touchY) on event ${event.eventType}")
                }
            }
        } catch (t: Throwable) {
            // Never allow an unhandled Throwable to crash the Accessibility Service
            Log.e(TAG, "Safe catch in onAccessibilityEvent", t)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "GlobalTouchAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        isServiceActive = false
        super.onDestroy()
    }
}

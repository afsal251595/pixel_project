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
 * 24/7 Global Touch Interceptor using Android AccessibilityService.
 * Intercepts user touches across home screen, apps, and keyboard.
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
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_SELECTED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 20
        info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

        serviceInfo = info
        Log.d(TAG, "GlobalTouchAccessibilityService connected with physical metrics: ${gridCalculator.getSummary()}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val now = System.currentTimeMillis()
        if (now - lastRecordedTime < 120) return // 120ms debounce

        // 1. Try direct event.source
        var targetNode: AccessibilityNodeInfo? = event.source

        // 2. Fallback to event records if source is null
        if (targetNode == null && event.recordCount > 0) {
            targetNode = event.getRecord(0)?.source
        }

        if (targetNode != null) {
            try {
                val bounds = Rect()
                targetNode.getBoundsInScreen(bounds)

                // Ignore full-screen root containers to avoid fake center repeats
                val isRootContainer = bounds.width() >= (gridCalculator.screenWidthPx * 0.9f) &&
                        bounds.height() >= (gridCalculator.screenHeightPx * 0.9f)

                if (!bounds.isEmpty && bounds.width() > 0 && bounds.height() > 0 && !isRootContainer) {
                    val touchX = bounds.exactCenterX()
                    val touchY = bounds.exactCenterY()
                    val cell = gridCalculator.getCellNumber(touchX, touchY)
                    logger.recordTouch(cell)
                    lastRecordedTime = now
                    Log.d(TAG, "Global Touch: Cell #$cell at ($touchX, $touchY)")
                }
            } finally {
                try { targetNode.recycle() } catch (e: Exception) {}
            }
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

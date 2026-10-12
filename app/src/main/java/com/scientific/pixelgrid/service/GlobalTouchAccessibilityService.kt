package com.scientific.pixelgrid.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.scientific.pixelgrid.PixelGridApplication
import com.scientific.pixelgrid.model.DisplayMetricsHelper
import com.scientific.pixelgrid.model.GridCalculator
import com.scientific.pixelgrid.storage.TouchFileLogger

/**
 * 24/7 Crash-Proof Global Touch Interceptor using Android AccessibilityService
 * combined with a non-intrusive 1x1 TYPE_ACCESSIBILITY_OVERLAY pass-through listener.
 * Captures exact physical touch coordinates (X, Y) across all external apps, keyboard,
 * and home screen launcher without interrupting or lagging user interactions.
 * Automatically pauses logging when our app is in the foreground.
 */
class GlobalTouchAccessibilityService : AccessibilityService() {

    private val TAG = "GlobalTouchService"
    private lateinit var logger: TouchFileLogger
    private lateinit var gridCalculator: GridCalculator
    private var lastRecordedTime: Long = 0L

    private var windowManager: WindowManager? = null
    private var touchOverlayView: View? = null

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

        setupTouchOverlay()
        Log.d(TAG, "GlobalTouchAccessibilityService connected successfully.")
    }

    private fun setupTouchOverlay() {
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            // 1x1 pixel non-intrusive overlay positioned at (0, 0)
            // TYPE_ACCESSIBILITY_OVERLAY sits at the highest system Z-order layer (2400+)
            // FLAG_NOT_FOCUSABLE keeps keyboard and focus undisturbed
            // FLAG_NOT_TOUCH_MODAL passes outside touches straight down to underlying apps
            // FLAG_WATCH_OUTSIDE_TOUCH notifies with ACTION_OUTSIDE whenever screen is tapped
            val params = WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }

            val view = View(this).apply {
                setBackgroundColor(Color.TRANSPARENT)
                setOnTouchListener { _, event ->
                    // Do not record touches when our app is open in foreground
                    if (PixelGridApplication.isAppInForeground) {
                        return@setOnTouchListener false
                    }

                    val action = event.actionMasked
                    if (action == MotionEvent.ACTION_OUTSIDE || action == MotionEvent.ACTION_DOWN) {
                        val touchX = if (event.rawX != 0f) event.rawX else event.x
                        val touchY = if (event.rawY != 0f) event.rawY else event.y

                        if (touchX > 0f || touchY > 0f) {
                            val now = System.currentTimeMillis()
                            // 20ms debounce: ultra-fast, catches rapid successive taps with zero drops
                            if (now - lastRecordedTime >= 20) {
                                lastRecordedTime = now
                                val cell = gridCalculator.getCellNumber(touchX, touchY)
                                logger.recordTouch(cell)
                                Log.d(TAG, "Raw Overlay Touch Logged: Cell #$cell at ($touchX, $touchY)")
                            }
                        }
                    }
                    false // Crucial: never consume, allow standard input pipeline to proceed
                }
            }

            windowManager?.addView(view, params)
            touchOverlayView = view
            Log.d(TAG, "1x1 Accessibility touch overlay created successfully.")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create touch overlay", t)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Do not record touches when our app is open in foreground or from our package
        if (PixelGridApplication.isAppInForeground || event.packageName == packageName) {
            return
        }

        try {
            val now = System.currentTimeMillis()
            if (now - lastRecordedTime < 100) return // 100ms debounce (avoids duplicates with overlay)

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

                // Reject full-screen or massive container views (>60% of screen)
                // Container centers default to (screenWidth/2, screenHeight/2), which causes false middle cells (e.g. 75)
                val isMassiveContainer = bounds.width() > (gridCalculator.screenWidthPx * 0.6f) &&
                        bounds.height() > (gridCalculator.screenHeightPx * 0.6f)

                if (!bounds.isEmpty && bounds.width() > 0 && bounds.height() > 0 && !isMassiveContainer) {
                    val touchX = bounds.exactCenterX()
                    val touchY = bounds.exactCenterY()
                    val cell = gridCalculator.getCellNumber(touchX, touchY)

                    logger.recordTouch(cell)
                    lastRecordedTime = now
                    Log.d(TAG, "Accessibility Node Touch Logged: Cell #$cell at ($touchX, $touchY) on event ${event.eventType}")
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
        try {
            if (touchOverlayView != null && windowManager != null) {
                windowManager?.removeView(touchOverlayView)
                touchOverlayView = null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error removing overlay", t)
        }
        super.onDestroy()
    }
}

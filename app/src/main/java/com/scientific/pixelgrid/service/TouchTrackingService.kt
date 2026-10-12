package com.scientific.pixelgrid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.scientific.pixelgrid.MainActivity
import com.scientific.pixelgrid.R
import com.scientific.pixelgrid.model.GridCalculator
import com.scientific.pixelgrid.storage.TouchFileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Continuous 2-week Foreground Service.
 * Maintains persistent notification, runs input reader, and auto-flushes touch log.
 */
class TouchTrackingService : Service() {

    companion object {
        const val CHANNEL_ID = "touch_tracker_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
        var isRunning = false
            private set
    }

    private lateinit var logger: TouchFileLogger
    private lateinit var gridCalculator: GridCalculator
    private var inputReader: ShizukuInputReader? = null
    private var periodicFlushJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        logger = TouchFileLogger(this)

        val metrics = resources.displayMetrics
        gridCalculator = GridCalculator(
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            xdpi = metrics.xdpi,
            ydpi = metrics.ydpi
        )

        inputReader = ShizukuInputReader(this, gridCalculator, logger)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            stopSelf()
            return START_NOT_STICKY
        }

        startTracking()
        return START_STICKY // Automatically restart if killed by OS
    }

    private fun startTracking() {
        isRunning = true
        startForeground(NOTIFICATION_ID, buildNotification("Running • 0 touches recorded"))

        // Start pass-through hardware touch listener
        inputReader?.startListening()

        // Start periodic 15-second disk flush and notification update
        periodicFlushJob = scope.launch {
            while (isActive) {
                delay(15_000)
                logger.flushBuffer()
                val total = logger.getTotalCount()
                val last = logger.getLastCell()
                val statusText = if (last > 0) {
                    "Recorded: $total touches (Last cell: $last)"
                } else {
                    "Recorded: $total touches"
                }
                updateNotification(statusText)
            }
        }
    }

    private fun stopTracking() {
        isRunning = false
        inputReader?.stopListening()
        periodicFlushJob?.cancel()
        periodicFlushJob = null
        logger.flushBuffer()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Touch Grid Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifies active touch grid logging session"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TouchTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Scientific Touch Grid Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    override fun onDestroy() {
        stopTracking()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

package com.scientific.pixelgrid.service

import android.content.Context
import android.util.Log
import com.scientific.pixelgrid.model.GridCalculator
import com.scientific.pixelgrid.storage.TouchFileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Background reader that intercepts hardware touches via ADB / Shizuku
 * without blocking or interfering with normal user phone usage.
 */
class ShizukuInputReader(
    private val context: Context,
    private val gridCalculator: GridCalculator,
    private val logger: TouchFileLogger
) {
    private val TAG = "ShizukuInputReader"
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Touch coordinate tracking
    private var currentX: Float = -1f
    private var currentY: Float = -1f
    private var isTouching = false

    // Hardware coordinate max scaling (some digitizers report 0..4095 instead of screen px)
    private var maxX = gridCalculator.screenWidthPx.toFloat()
    private var maxY = gridCalculator.screenHeightPx.toFloat()

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun startListening() {
        if (job?.isActive == true) return

        job = scope.launch {
            try {
                // If Shizuku is granted, we execute getevent
                val command = arrayOf("sh", "-c", "getevent -lt")
                val process = if (isShizukuAvailable()) {
                    Shizuku.newProcess(command, null, null)
                } else {
                    // Fallback to local process if root or direct adb shell
                    Runtime.getRuntime().exec(command)
                }

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?

                Log.d(TAG, "Touch interceptor started successfully.")

                while (isActive && reader.readLine().also { line = it } != null) {
                    parseLine(line ?: "")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background input reader note: ${e.message}")
            }
        }
    }

    private fun parseLine(line: String) {
        // Sample getevent output:
        // /dev/input/event2: EV_ABS       ABS_MT_POSITION_X    0000025c
        // /dev/input/event2: EV_ABS       ABS_MT_POSITION_Y    000005a1
        // /dev/input/event2: EV_KEY       BTN_TOUCH            DOWN
        // /dev/input/event2: EV_SYN       SYN_REPORT           00000000

        try {
            if (line.contains("ABS_MT_POSITION_X")) {
                val hex = line.substringAfterLast(" ").trim()
                val rawX = hex.toLong(16).toFloat()
                currentX = (rawX / maxX) * gridCalculator.screenWidthPx
            } else if (line.contains("ABS_MT_POSITION_Y")) {
                val hex = line.substringAfterLast(" ").trim()
                val rawY = hex.toLong(16).toFloat()
                currentY = (rawY / maxY) * gridCalculator.screenHeightPx
            } else if (line.contains("BTN_TOUCH") && line.contains("DOWN")) {
                isTouching = true
            } else if (line.contains("BTN_TOUCH") && line.contains("UP")) {
                isTouching = false
            } else if (line.contains("SYN_REPORT")) {
                if (isTouching && currentX >= 0 && currentY >= 0) {
                    val cell = gridCalculator.getCellNumber(currentX, currentY)
                    logger.recordTouch(cell)
                    // Reset to avoid duplicate spam during single touch hold
                    isTouching = false
                }
            }
        } catch (e: Exception) {
            // Ignore parse hiccups
        }
    }

    fun stopListening() {
        job?.cancel()
        job = null
    }
}

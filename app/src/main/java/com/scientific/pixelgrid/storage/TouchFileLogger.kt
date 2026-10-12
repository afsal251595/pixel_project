package com.scientific.pixelgrid.storage

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe Singleton persistent logger for scientific touch positions.
 * Saves touches immediately in format: 75, 72, 14, ...
 */
class TouchFileLogger private constructor(private val appContext: Context) {

    companion object {
        @Volatile
        private var instance: TouchFileLogger? = null

        fun getInstance(context: Context): TouchFileLogger {
            return instance ?: synchronized(this) {
                instance ?: TouchFileLogger(context.applicationContext).also { instance = it }
            }
        }
    }

    private val logFileName = "touch_log.txt"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val totalTouchCount = AtomicLong(0)
    private var lastTouchedCell: Int = -1

    private val _recentPreviewFlow = MutableStateFlow<String>("No touches recorded yet")
    val recentPreviewFlow: StateFlow<String> = _recentPreviewFlow

    init {
        // Read existing count from file on startup
        scope.launch {
            val logFile = getLogFile()
            if (logFile.exists()) {
                val text = logFile.readText().trim()
                if (text.isNotEmpty()) {
                    val tokens = text.split(",").filter { it.isNotBlank() }
                    totalTouchCount.set(tokens.size.toLong())
                    _recentPreviewFlow.value = tokens.takeLast(25).joinToString(", ")
                }
            }
        }
    }

    fun getLogFile(): File {
        val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        return File(dir, logFileName)
    }

    /**
     * Record a new touch cell ID and write immediately to touch_log.txt.
     */
    fun recordTouch(cellId: Int) {
        if (cellId <= 0) return
        lastTouchedCell = cellId
        totalTouchCount.incrementAndGet()

        scope.launch {
            synchronized(this@TouchFileLogger) {
                try {
                    val file = getLogFile()
                    val existsWithContent = file.exists() && file.length() > 0

                    FileWriter(file, true).use { writer ->
                        if (existsWithContent) {
                            writer.write(", ")
                        }
                        writer.write(cellId.toString())
                        writer.flush()
                    }

                    // Update live preview in memory
                    val currentPreview = _recentPreviewFlow.value
                    if (currentPreview == "No touches recorded yet" || currentPreview.isEmpty()) {
                        _recentPreviewFlow.value = cellId.toString()
                    } else {
                        val items = (currentPreview.split(",").map { it.trim() } + cellId.toString()).takeLast(25)
                        _recentPreviewFlow.value = items.joinToString(", ")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getLastCell(): Int = lastTouchedCell

    fun getTotalCount(): Long = totalTouchCount.get()

    suspend fun getRecentPreview(limit: Int = 25): String = withContext(Dispatchers.IO) {
        val file = getLogFile()
        if (!file.exists() || file.length() == 0L) {
            return@withContext "No touches recorded yet"
        }
        try {
            val content = file.readText().trim()
            val tokens = content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return@withContext "Empty log"
            val subset = tokens.takeLast(limit)
            return@withContext subset.joinToString(", ")
        } catch (e: Exception) {
            return@withContext "Error reading log: ${e.localizedMessage}"
        }
    }

    suspend fun getFullLogText(): String = withContext(Dispatchers.IO) {
        val file = getLogFile()
        if (!file.exists() || file.length() == 0L) {
            return@withContext "No touches recorded yet"
        }
        try {
            return@withContext file.readText().trim()
        } catch (e: Exception) {
            return@withContext "Error reading file: ${e.localizedMessage}"
        }
    }

    fun flushBuffer() {
        // Immediate disk write mode is active; buffer is already flushed.
    }

    fun clearLog() {
        totalTouchCount.set(0)
        lastTouchedCell = -1
        _recentPreviewFlow.value = "No touches recorded yet"
        scope.launch {
            synchronized(this@TouchFileLogger) {
                val file = getLogFile()
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    fun createShareIntent(): Intent? {
        val file = getLogFile()
        if (!file.exists() || file.length() == 0L) return null

        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Scientific Touch Log - ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

package com.scientific.pixelgrid.storage

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance, zero-latency thread-safe Singleton persistent logger.
 * Writes touches immediately to touch_log.txt via a dedicated non-blocking channel.
 * Updates in-memory preview in microseconds without disk locks or lag.
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

    @Volatile
    private var hasFileContent: Boolean = false

    private val recentTouches = ArrayDeque<Int>(30)
    private val _recentPreviewFlow = MutableStateFlow<String>("No touches recorded yet")
    val recentPreviewFlow: StateFlow<String> = _recentPreviewFlow

    private val writeChannel = Channel<Int>(Channel.UNLIMITED)

    init {
        scope.launch {
            val logFile = getLogFile()
            if (logFile.exists() && logFile.length() > 0) {
                hasFileContent = true
                try {
                    val text = logFile.readText().trim()
                    if (text.isNotEmpty()) {
                        val tokens = text.split(",").mapNotNull { it.trim().toIntOrNull() }
                        totalTouchCount.set(tokens.size.toLong())
                        synchronized(recentTouches) {
                            recentTouches.clear()
                            recentTouches.addAll(tokens.takeLast(25))
                            _recentPreviewFlow.value = recentTouches.joinToString(", ")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Dedicated background writer: appends touches sequentially with zero dropped events
            for (cellId in writeChannel) {
                try {
                    val prefix = if (hasFileContent) ", " else {
                        hasFileContent = true
                        ""
                    }
                    logFile.appendText(prefix + cellId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getLogFile(): File {
        val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        return File(dir, logFileName)
    }

    /**
     * Record a new touch cell ID instantly.
     * Completes in microseconds on the calling thread; file write is queued asynchronously.
     */
    fun recordTouch(cellId: Int) {
        if (cellId <= 0) return
        lastTouchedCell = cellId
        totalTouchCount.incrementAndGet()

        // 1. Instant in-memory preview update (zero delay for UI)
        val preview: String
        synchronized(recentTouches) {
            if (recentTouches.size >= 25) {
                recentTouches.removeFirst()
            }
            recentTouches.addLast(cellId)
            preview = recentTouches.joinToString(", ")
        }
        _recentPreviewFlow.value = preview

        // 2. Queue for asynchronous disk writing (non-blocking, never drops)
        writeChannel.trySend(cellId)
    }

    fun getLastCell(): Int = lastTouchedCell

    fun getTotalCount(): Long = totalTouchCount.get()

    suspend fun getRecentPreview(limit: Int = 25): String {
        synchronized(recentTouches) {
            if (recentTouches.isNotEmpty()) {
                return recentTouches.takeLast(limit).joinToString(", ")
            }
        }
        return withContext(Dispatchers.IO) {
            val file = getLogFile()
            if (!file.exists() || file.length() == 0L) {
                return@withContext "No touches recorded yet"
            }
            try {
                val content = file.readText().trim()
                val tokens = content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (tokens.isEmpty()) return@withContext "Empty log"
                return@withContext tokens.takeLast(limit).joinToString(", ")
            } catch (e: Exception) {
                return@withContext "Error reading log: ${e.localizedMessage}"
            }
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
        // Queue operates in real-time; touches are streamed directly to disk.
    }

    fun clearLog() {
        totalTouchCount.set(0)
        lastTouchedCell = -1
        hasFileContent = false
        synchronized(recentTouches) {
            recentTouches.clear()
        }
        _recentPreviewFlow.value = "No touches recorded yet"
        scope.launch {
            val file = getLogFile()
            if (file.exists()) {
                file.delete()
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

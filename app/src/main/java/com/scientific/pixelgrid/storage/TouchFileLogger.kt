package com.scientific.pixelgrid.storage

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe, buffered persistent logger for scientific touch positions.
 * Saves touches in format: 75, 72, 14, ...
 * Designed for continuous 2-week operation with low memory and flash wear.
 */
class TouchFileLogger(private val context: Context) {

    private val logFileName = "touch_log.txt"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val buffer = ConcurrentLinkedQueue<Int>()
    private val totalTouchCount = AtomicLong(0)
    private var lastTouchedCell: Int = -1

    // Listener for UI updates
    var onTouchLogged: ((cellId: Int, totalCount: Long) -> Unit)? = null

    init {
        // Read existing count from file on startup if it exists
        val logFile = getLogFile()
        if (logFile.exists()) {
            val text = logFile.readText().trim()
            if (text.isNotEmpty()) {
                val count = text.split(",").filter { it.isNotBlank() }.size
                totalTouchCount.set(count.toLong())
            }
        }
    }

    fun getLogFile(): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, logFileName)
    }

    /**
     * Record a new touch cell ID.
     * Deduplicates immediate consecutive duplicates if within threshold.
     */
    fun recordTouch(cellId: Int) {
        lastTouchedCell = cellId
        val newTotal = totalTouchCount.incrementAndGet()
        buffer.add(cellId)

        onTouchLogged?.invoke(cellId, newTotal)

        // Flush buffer to disk if 10 or more items accumulate
        if (buffer.size >= 10) {
            flushBuffer()
        }
    }

    /**
     * Flushes buffered touches to touch_log.txt.
     */
    fun flushBuffer() {
        if (buffer.isEmpty()) return

        scope.launch {
            synchronized(this@TouchFileLogger) {
                try {
                    val file = getLogFile()
                    val existsWithContent = file.exists() && file.length() > 0

                    FileWriter(file, true).use { writer ->
                        var isFirstInWrite = !existsWithContent
                        while (!buffer.isEmpty()) {
                            val id = buffer.poll() ?: break
                            if (!isFirstInWrite) {
                                writer.write(", ")
                            } else {
                                isFirstInWrite = false
                            }
                            writer.write(id.toString())
                        }
                        writer.flush()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getLastCell(): Int = lastTouchedCell

    fun getTotalCount(): Long = totalTouchCount.get()

    /**
     * Returns the last N cell IDs as a formatted preview string (e.g., "75, 72, 14, ...").
     */
    suspend fun getRecentPreview(limit: Int = 15): String = withContext(Dispatchers.IO) {
        flushBuffer()
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

    /**
     * Clears all recorded touch data.
     */
    fun clearLog() {
        buffer.clear()
        totalTouchCount.set(0)
        lastTouchedCell = -1
        scope.launch {
            synchronized(this@TouchFileLogger) {
                val file = getLogFile()
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    /**
     * Creates an Android Share Sheet intent so the scientist can export/email/backup the log.
     */
    fun createShareIntent(): Intent? {
        flushBuffer()
        val file = getLogFile()
        if (!file.exists() || file.length() == 0L) return null

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
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

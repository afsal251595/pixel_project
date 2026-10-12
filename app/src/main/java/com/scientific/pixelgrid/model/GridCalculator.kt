package com.scientific.pixelgrid.model

import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Calculates physical 5mm x 5mm grid parameters on Android display metrics.
 * 
 * 1 inch = 25.4 mm
 * 5 mm in inches = 5.0 / 25.4 = ~0.19685 inches
 * Cell width (px)  = (5.0 / 25.4) * xdpi
 * Cell height (px) = (5.0 / 25.4) * ydpi
 */
class GridCalculator(
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val xdpi: Float,
    val ydpi: Float,
    val targetCellSizeMm: Float = 5.0f,
    var calibrationScale: Float = 1.0f // Fine-tuning calibration factor
) {
    companion object {
        const val MM_PER_INCH = 25.4f
    }

    // Effective DPI with calibration factor
    val effectiveXdpi: Float get() = max(1.0f, xdpi * calibrationScale)
    val effectiveYdpi: Float get() = max(1.0f, ydpi * calibrationScale)

    // Cell physical dimensions converted to pixels for 5mm x 5mm
    val cellWidthPx: Float
        get() = (targetCellSizeMm / MM_PER_INCH) * effectiveXdpi

    val cellHeightPx: Float
        get() = (targetCellSizeMm / MM_PER_INCH) * effectiveYdpi

    // Number of columns and rows required to cover the screen
    val columns: Int
        get() = max(1, ceil(screenWidthPx / cellWidthPx).toInt())

    val rows: Int
        get() = max(1, ceil(screenHeightPx / cellHeightPx).toInt())

    // Total cells in the grid (numbered 1 to totalCells)
    val totalCells: Int
        get() = columns * rows

    /**
     * Converts screen touch coordinate (x, y) to 1-indexed Cell ID (1 to N).
     * Numbered row-by-row from top-left (1) to bottom-right (N).
     */
    fun getCellNumber(x: Float, y: Float): Int {
        val clampedX = x.coerceIn(0f, (screenWidthPx - 1).toFloat())
        val clampedY = y.coerceIn(0f, (screenHeightPx - 1).toFloat())

        val col = min(columns - 1, floor(clampedX / cellWidthPx).toInt())
        val row = min(rows - 1, floor(clampedY / cellHeightPx).toInt())

        return (row * columns) + col + 1
    }

    /**
     * Returns the bounding box (RectF in pixels) of a given 1-indexed cell number.
     */
    fun getCellBounds(cellNumber: Int): RectF {
        if (cellNumber < 1 || cellNumber > totalCells) {
            return RectF(0f, 0f, 0f, 0f)
        }
        val zeroIndex = cellNumber - 1
        val row = zeroIndex / columns
        val col = zeroIndex % columns

        val left = col * cellWidthPx
        val top = row * cellHeightPx
        val right = min(screenWidthPx.toFloat(), left + cellWidthPx)
        val bottom = min(screenHeightPx.toFloat(), top + cellHeightPx)

        return RectF(left, top, right, bottom)
    }

    /**
     * Information summary for diagnostics and scientific records.
     */
    fun getSummary(): String {
        return "Screen: ${screenWidthPx}x${screenHeightPx}px | " +
                "DPI: ${effectiveXdpi.toInt()}x${effectiveYdpi.toInt()} | " +
                "Cell: 5mm x 5mm (~${cellWidthPx.toInt()}x${cellHeightPx.toInt()}px) | " +
                "Grid: ${columns} cols x ${rows} rows ($totalCells cells)"
    }
}

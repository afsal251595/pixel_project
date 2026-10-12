package com.scientific.pixelgrid.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.scientific.pixelgrid.model.GridCalculator
import com.scientific.pixelgrid.storage.TouchFileLogger
import kotlin.math.min

/**
 * Custom View that visually renders the 5mm x 5mm numbered grid cells (1 to N)
 * and provides interactive touch feedback.
 */
class GridOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var gridCalculator: GridCalculator? = null
    var logger: TouchFileLogger? = null
    var onCellTouched: ((cellId: Int) -> Unit)? = null

    // Drawing paints
    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334155")
        strokeWidth = 1.0f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textAlign = Paint.Align.CENTER
    }

    private val activeCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.FILL
        alpha = 100
    }

    private val activeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
    }

    private var currentlyTouchedCell: Int = -1

    fun setup(calculator: GridCalculator, fileLogger: TouchFileLogger) {
        this.gridCalculator = calculator
        this.logger = fileLogger
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val calc = gridCalculator ?: return

        val cols = calc.columns
        val rows = calc.rows
        val cellW = calc.cellWidthPx
        val cellH = calc.cellHeightPx

        // 1. Highlight touched cell if any
        if (currentlyTouchedCell in 1..calc.totalCells) {
            val bounds = calc.getCellBounds(currentlyTouchedCell)
            canvas.drawRect(bounds, activeCellPaint)
            canvas.drawRect(bounds, activeBorderPaint)
        }

        // 2. Draw vertical and horizontal grid lines
        for (c in 0..cols) {
            val x = c * cellW
            canvas.drawLine(x, 0f, x, height.toFloat(), gridLinePaint)
        }
        for (r in 0..rows) {
            val y = r * cellH
            canvas.drawLine(0f, y, width.toFloat(), y, gridLinePaint)
        }

        // 3. Draw sequential cell indices (1 to N) sized for 7mm x 10mm cells
        val fontSize = min(cellW, cellH) * 0.38f
        textPaint.textSize = fontSize

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cellId = (r * cols) + c + 1
                val centerX = (c * cellW) + (cellW / 2f)
                val centerY = (r * cellH) + (cellH / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)

                // Highlight text of active cell
                if (cellId == currentlyTouchedCell) {
                    textPaint.color = Color.parseColor("#00E5FF")
                    textPaint.textSize = fontSize * 1.3f
                    textPaint.isFakeBoldText = true
                } else {
                    textPaint.color = Color.parseColor("#64748B")
                    textPaint.textSize = fontSize
                    textPaint.isFakeBoldText = false
                }

                canvas.drawText(cellId.toString(), centerX, centerY, textPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val calc = gridCalculator ?: return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val cellId = calc.getCellNumber(event.x, event.y)
                if (cellId != currentlyTouchedCell) {
                    currentlyTouchedCell = cellId
                    onCellTouched?.invoke(cellId)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentlyTouchedCell = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

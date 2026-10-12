package com.scientific.pixelgrid

import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.scientific.pixelgrid.databinding.ActivityCalibrationBinding
import com.scientific.pixelgrid.model.GridCalculator
import com.scientific.pixelgrid.storage.TouchFileLogger

class CalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalibrationBinding
    private lateinit var logger: TouchFileLogger
    private lateinit var calculator: GridCalculator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableImmersiveMode()

        logger = TouchFileLogger.getInstance(this)
        val metrics = com.scientific.pixelgrid.model.DisplayMetricsHelper.getPhysicalMetrics(this)
        calculator = GridCalculator(
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            xdpi = metrics.xdpi,
            ydpi = metrics.ydpi
        )

        binding.tvGridInfo.text = calculator.getSummary()
        binding.gridOverlayView.setup(calculator, logger)

        binding.gridOverlayView.onCellTouched = { cellId ->
            binding.tvTouchFeedback.text = "Touched Cell: #$cellId | Logged to touch_log.txt"
        }

        binding.btnCloseCalibration.setOnClickListener {
            finish()
        }
    }

    private fun enableImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
    }
}

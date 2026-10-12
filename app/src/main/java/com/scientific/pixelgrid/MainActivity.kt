package com.scientific.pixelgrid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.scientific.pixelgrid.databinding.ActivityMainBinding
import com.scientific.pixelgrid.model.GridCalculator
import com.scientific.pixelgrid.service.TouchTrackingService
import com.scientific.pixelgrid.storage.TouchFileLogger
import com.scientific.pixelgrid.updater.AppUpdater
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var logger: TouchFileLogger
    private lateinit var calculator: GridCalculator
    private lateinit var updater: AppUpdater

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startTrackerService()
            } else {
                Toast.makeText(this, "Notification permission needed for 2-week service", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        logger = TouchFileLogger(this)
        updater = AppUpdater(this)

        initGridCalculator()
        setupListeners()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun initGridCalculator() {
        val metrics = resources.displayMetrics
        calculator = GridCalculator(
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            xdpi = metrics.xdpi,
            ydpi = metrics.ydpi
        )

        binding.tvGridDetails.text = buildString {
            append("Screen: ${calculator.screenWidthPx} x ${calculator.screenHeightPx} px\n")
            append("Hardware DPI: ${calculator.effectiveXdpi.toInt()} xdpi, ${calculator.effectiveYdpi.toInt()} ydpi\n")
            append("Cell Size: 5.0 cm x 5.0 cm (~${calculator.cellWidthPx.toInt()} x ${calculator.cellHeightPx.toInt()} px)\n")
            append("Grid Matrix: ${calculator.columns} Columns x ${calculator.rows} Rows\n")
            append("Total Cells: ${calculator.totalCells} (Numbered 1 to ${calculator.totalCells})")
        }
    }

    private fun setupListeners() {
        // Toggle Background Service
        binding.btnToggleService.setOnClickListener {
            if (TouchTrackingService.isRunning) {
                stopTrackerService()
            } else {
                checkPermissionsAndStart()
            }
        }

        // Whitelist Battery Optimization
        binding.btnBatteryWhitelist.setOnClickListener {
            requestBatteryOptimizationExemption()
        }

        // Open Calibration / Visual Test Screen
        binding.btnOpenCalibration.setOnClickListener {
            val intent = Intent(this, CalibrationActivity::class.java)
            startActivity(intent)
        }

        // Export Log
        binding.btnExportLog.setOnClickListener {
            val shareIntent = logger.createShareIntent()
            if (shareIntent != null) {
                startActivity(Intent.createChooser(shareIntent, "Export Touch Log"))
            } else {
                Toast.makeText(this, "No touch data to export yet", Toast.LENGTH_SHORT).show()
            }
        }

        // Clear Log
        binding.btnClearLog.setOnClickListener {
            logger.clearLog()
            Toast.makeText(this, "Touch log cleared", Toast.LENGTH_SHORT).show()
            updateUI()
        }

        // Remote Git OTA Updater
        binding.btnCheckUpdates.setOnClickListener {
            checkRemoteUpdates()
        }
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startTrackerService()
    }

    private fun startTrackerService() {
        val intent = Intent(this, TouchTrackingService::class.java).apply {
            action = TouchTrackingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "2-Week Background Tracker started", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun stopTrackerService() {
        val intent = Intent(this, TouchTrackingService::class.java).apply {
            action = TouchTrackingService.ACTION_STOP
        }
        startService(intent)
        Toast.makeText(this, "Background Tracker stopped", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Already exempt from battery restrictions", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkRemoteUpdates() {
        binding.btnCheckUpdates.isEnabled = false
        binding.tvUpdateStatus.text = "Checking GitHub Releases..."

        lifecycleScope.launch {
            val info = updater.checkForUpdates()
            binding.btnCheckUpdates.isEnabled = true

            if (info.hasUpdate) {
                binding.tvUpdateStatus.text = "New update available: v${info.latestVersion}"
                updater.startApkDownload(info.downloadUrl) {
                    Toast.makeText(this@MainActivity, "Downloading update from GitHub...", Toast.LENGTH_LONG).show()
                }
            } else {
                binding.tvUpdateStatus.text = "App is up to date (v${info.currentVersion})"
                Toast.makeText(this@MainActivity, "No new updates found on Git", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI() {
        val isRunning = TouchTrackingService.isRunning
        if (isRunning) {
            binding.tvServiceStatus.text = "Status: Active (Continuous 2-Week Logging)"
            binding.tvServiceStatus.setTextColor(Color.parseColor("#10B981"))
            binding.btnToggleService.text = "Stop Background Service"
            binding.btnToggleService.backgroundTintList = getColorStateList(R.color.accent_rose)
        } else {
            binding.tvServiceStatus.text = "Status: Stopped"
            binding.tvServiceStatus.setTextColor(Color.parseColor("#F43F5E"))
            binding.btnToggleService.text = "Start Background Service"
            binding.btnToggleService.backgroundTintList = getColorStateList(R.color.accent_green)
        }

        binding.tvTouchCounter.text = "Total Recorded Touches: ${logger.getTotalCount()}"

        lifecycleScope.launch {
            binding.tvLogPreview.text = logger.getRecentPreview(20)
        }
    }
}

package com.scientific.pixelgrid

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.scientific.pixelgrid.databinding.ActivityMainBinding
import com.scientific.pixelgrid.model.DisplayMetricsHelper
import com.scientific.pixelgrid.model.GridCalculator
import com.scientific.pixelgrid.service.GlobalTouchAccessibilityService
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
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        logger = TouchFileLogger.getInstance(this)
        updater = AppUpdater(this)

        initGridCalculator()
        setupListeners()

        // Auto-start background tracker service on launch
        checkPermissionsAndStart()

        // Real-time observation of touch log updates
        lifecycleScope.launch {
            logger.recentPreviewFlow.collect { preview ->
                binding.tvLogPreview.text = preview
            }
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun initGridCalculator() {
        val metrics = DisplayMetricsHelper.getPhysicalMetrics(this)
        calculator = GridCalculator(
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            xdpi = metrics.xdpi,
            ydpi = metrics.ydpi,
            targetCellSizeMm = 5.0f // Exact 5mm x 5mm cells
        )

        binding.tvGridDetails.text = buildString {
            append("Screen: ${calculator.screenWidthPx} x ${calculator.screenHeightPx} px\n")
            append("Hardware DPI: ${calculator.effectiveXdpi.toInt()} xdpi, ${calculator.effectiveYdpi.toInt()} ydpi\n")
            append("Cell Size: 5.0 mm x 5.0 mm (~${calculator.cellWidthPx.toInt()} x ${calculator.cellHeightPx.toInt()} px)\n")
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

        // Open Accessibility Settings for Always-On Global Tracking
        binding.btnOpenAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Enable 'Touch Grid Tracker' to capture touches across all apps", Toast.LENGTH_LONG).show()
        }

        // Open App Info for Android 13/14 Restricted Settings Unlock
        binding.btnUnlockRestricted.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            Toast.makeText(this, "Tap the 3 dots (⋮) in top-right -> 'Allow restricted settings'", Toast.LENGTH_LONG).show()
        }

        // Open Calibration / Visual Test Screen
        binding.btnOpenCalibration.setOnClickListener {
            val intent = Intent(this, CalibrationActivity::class.java)
            startActivity(intent)
        }

        // View Full Log in Popup Dialog
        binding.btnViewFullLog.setOnClickListener {
            lifecycleScope.launch {
                val fullText = logger.getFullLogText()
                val totalCount = logger.getTotalCount()

                val textView = TextView(this@MainActivity).apply {
                    text = fullText
                    setPadding(40, 20, 40, 20)
                    setTextIsSelectable(true)
                }
                val scrollView = ScrollView(this@MainActivity).apply {
                    addView(textView)
                }

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Complete touch_log.txt ($totalCount touches)")
                    .setView(scrollView)
                    .setPositiveButton("Copy All") { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("touch_log", fullText))
                        Toast.makeText(this@MainActivity, "Copied full log to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
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
        updateUI()
    }

    private fun stopTrackerService() {
        val intent = Intent(this, TouchTrackingService::class.java).apply {
            action = TouchTrackingService.ACTION_STOP
        }
        startService(intent)
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
            binding.tvServiceStatus.text = "Background Engine: Active (Recording touches)"
            binding.tvServiceStatus.setTextColor(Color.parseColor("#10B981"))
            binding.btnToggleService.text = "Pause Service"
            binding.btnToggleService.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent_rose)
        } else {
            binding.tvServiceStatus.text = "Background Engine: Paused"
            binding.tvServiceStatus.setTextColor(Color.parseColor("#F43F5E"))
            binding.btnToggleService.text = "Resume Service"
            binding.btnToggleService.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent_green)
        }

        // Accessibility status
        val isAccessibilityOn = GlobalTouchAccessibilityService.isServiceActive
        if (isAccessibilityOn) {
            binding.tvAccessibilityStatus.text = "Always-On System Tracking: ACTIVE across all apps"
            binding.tvAccessibilityStatus.setTextColor(Color.parseColor("#10B981"))
            binding.btnOpenAccessibility.text = "Accessibility Active"
        } else {
            binding.tvAccessibilityStatus.text = "Always-On Tracking: Not enabled yet"
            binding.tvAccessibilityStatus.setTextColor(Color.parseColor("#F59E0B"))
            binding.btnOpenAccessibility.text = "Enable Always-On Tracking"
        }

        lifecycleScope.launch {
            binding.tvLogPreview.text = logger.getRecentPreview(25)
        }
    }
}

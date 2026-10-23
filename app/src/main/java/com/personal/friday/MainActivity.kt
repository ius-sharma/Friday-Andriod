package com.personal.friday

import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var btnOpenSettings: ImageButton
    private lateinit var flOrbContainer: FrameLayout
    private lateinit var particleOrbView: ParticleOrbView
    private lateinit var particleView: NanotechParticleView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvCoreStatus: TextView
    private lateinit var tvOrbHint: TextView
    private lateinit var cardTelemetryPower: View
    private lateinit var tvBatteryPercent: TextView
    private lateinit var tvThermalStatus: TextView
    private lateinit var cardTelemetryMemory: View
    private lateinit var tvRamUsage: TextView
    private lateinit var tvRamState: TextView
    private lateinit var cardTelemetryProtocols: View
    private lateinit var tvActiveProtocolsSummary: TextView
    private lateinit var btnToggleService: Button

    private var isServiceRunning = false
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            checkPermissionsAndStart()
        }
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkPermissionsAndStart()
        }
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startFridayService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission denied, Boss.", Toast.LENGTH_SHORT).show()
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            com.personal.friday.widget.FridayHudWidgetProvider.triggerAsyncWeatherRefresh(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initTts()
        updateTelemetry()

        if (savedInstanceState == null) {
            startNanotechEntranceAnimation()
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(arrayOf(
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ))
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStateUI()
        updateTelemetry()
        particleOrbView.startLoop()
    }

    override fun onPause() {
        super.onPause()
        particleOrbView.stopLoop()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }

    private fun initViews() {
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        flOrbContainer = findViewById(R.id.flOrbContainer)
        particleOrbView = findViewById(R.id.particleOrbView)
        particleView = findViewById(R.id.particleView)
        tvTitle = findViewById(R.id.tvTitle)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        tvCoreStatus = findViewById(R.id.tvCoreStatus)
        tvOrbHint = findViewById(R.id.tvOrbHint)

        cardTelemetryPower = findViewById(R.id.cardTelemetryPower)
        tvBatteryPercent = findViewById(R.id.tvBatteryPercent)
        tvThermalStatus = findViewById(R.id.tvThermalStatus)

        cardTelemetryMemory = findViewById(R.id.cardTelemetryMemory)
        tvRamUsage = findViewById(R.id.tvRamUsage)
        tvRamState = findViewById(R.id.tvRamState)

        cardTelemetryProtocols = findViewById(R.id.cardTelemetryProtocols)
        tvActiveProtocolsSummary = findViewById(R.id.tvActiveProtocolsSummary)

        btnToggleService = findViewById(R.id.btnToggleService)

        btnOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            if (Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_right, R.anim.slide_out_left)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        particleOrbView.onOrbClickListener = {
            tvCoreStatus.text = "NEURAL LINK ACTIVE • LISTENING"
            tvCoreStatus.setTextColor(getColor(R.color.stark_cyan))
            if (isTtsReady) {
                tts?.speak("All systems nominal, Boss. Online and ready.", TextToSpeech.QUEUE_FLUSH, null, "orb_click")
            }
            flOrbContainer.postDelayed({
                tvCoreStatus.text = "ALL SYSTEMS NOMINAL • CORE AT 100%"
                tvCoreStatus.setTextColor(getColor(R.color.stark_gold))
            }, 3000)
        }

        btnToggleService.setOnClickListener {
            if (isServiceRunning && FridayOverlayService.hasScreenCapture) {
                stopFridayService()
            } else {
                checkPermissionsAndStart()
            }
        }
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isTtsReady = true
            }
        }
    }

    private fun updateTelemetry() {
        // 1. Battery & Thermals
        try {
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = registerReceiver(null, batteryFilter)
            if (batteryStatus != null) {
                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 0

                val tempTenths = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                val tempCelsius = tempTenths / 10.0f
                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val chargingText = if (isCharging) "Charging" else "Discharging"
                tvBatteryPercent.text = "$batteryPct%"
                tvThermalStatus.text = "Thermals: ${String.format("%.1f", tempCelsius)}°C • Status: $chargingText (Optimal)"
            }
        } catch (_: Exception) {}

        // 2. RAM / Neural Memory
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            if (am != null) {
                am.getMemoryInfo(memInfo)
                val totalGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
                val availGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
                val usedGb = totalGb - availGb
                tvRamUsage.text = String.format("%.1f GB / %.1f GB", usedGb, totalGb)
                val memPressure = if (memInfo.lowMemory) "High" else "Nominal"
                tvRamState.text = "Memory Pressure: $memPressure • Neural Matrix Ready"
            }
        } catch (_: Exception) {}

        // 3. Active Protocols Summary
        try {
            val prefs = getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
            val overwatch = if (prefs.getBoolean("overwatch_enabled", true)) "Active" else "Standby"
            val doomscroll = if (prefs.getBoolean("doomscroll_enabled", true)) "Armed" else "Standby"
            val wakeWord = if (prefs.getBoolean("wakeword_enabled", true)) "Offline KWS Active" else "Disabled"

            tvActiveProtocolsSummary.text = "• Screen Sentry: $overwatch\n• Doomscroll Protocol: $doomscroll\n• Voice Wake: $wakeWord"
        } catch (_: Exception) {}
    }

    private fun updateServiceStateUI() {
        isServiceRunning = FridayOverlayService.isRunning
        val hasCapture = FridayOverlayService.hasScreenCapture

        if (isServiceRunning) {
            if (hasCapture) {
                tvCoreStatus.text = "F.R.I.D.A.Y. ONLINE • SCREEN SENTRY ARMED"
                tvCoreStatus.setTextColor(getColor(R.color.stark_cyan))
                btnToggleService.text = "DEACTIVATE OVERLAY"
                btnToggleService.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#C62828"))
                btnToggleService.setTextColor(Color.WHITE)
            } else {
                tvCoreStatus.text = "OVERLAY ACTIVE • TAP TO ARM SENTRY"
                tvCoreStatus.setTextColor(getColor(R.color.stark_gold))
                btnToggleService.text = "ARM SCREEN SENTRY"
                btnToggleService.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF6D00"))
                btnToggleService.setTextColor(Color.BLACK)
            }
        } else {
            tvCoreStatus.text = "ALL SYSTEMS NOMINAL • CORE AT 100%"
            tvCoreStatus.setTextColor(getColor(R.color.stark_gold))
            btnToggleService.text = "INITIALIZE OVERLAY"
            btnToggleService.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF6D00"))
            btnToggleService.setTextColor(Color.BLACK)
        }
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
            return
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startFridayService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, FridayOverlayService::class.java).apply {
            action = FridayOverlayService.ACTION_START_PROJECTION
            putExtra(FridayOverlayService.EXTRA_RESULT_CODE, resultCode)
            putExtra(FridayOverlayService.EXTRA_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        flOrbContainer.postDelayed({
            updateServiceStateUI()
            try {
                com.personal.friday.widget.FridayHudWidgetProvider.updateAllWidgets(this)
            } catch (_: Exception) {}
        }, 300)
    }

    private fun stopFridayService() {
        val serviceIntent = Intent(this, FridayOverlayService::class.java)
        stopService(serviceIntent)
        flOrbContainer.postDelayed({
            updateServiceStateUI()
            try {
                com.personal.friday.widget.FridayHudWidgetProvider.updateAllWidgets(this)
            } catch (_: Exception) {}
        }, 300)
    }

    private fun startNanotechEntranceAnimation() {
        val viewsToStagger = listOf(
            tvTitle,
            tvSubtitle,
            btnOpenSettings,
            flOrbContainer,
            tvCoreStatus,
            tvOrbHint,
            cardTelemetryPower,
            cardTelemetryMemory,
            cardTelemetryProtocols,
            btnToggleService
        )

        viewsToStagger.forEach { v ->
            v.alpha = 0f
            v.translationY = 40f
        }

        flOrbContainer.post {
            val orbRect = Rect()
            flOrbContainer.getGlobalVisibleRect(orbRect)

            val particleRect = Rect()
            particleView.getGlobalVisibleRect(particleRect)

            val targetCenterX = (orbRect.centerX() - particleRect.left).toFloat()
            val targetCenterY = (orbRect.centerY() - particleRect.top).toFloat()
            val targetRadius = orbRect.width() * 0.38f

            particleView.startAssembleAnimation(targetCenterX, targetCenterY, targetRadius) {
                // Orb assembly complete - trigger pulse!
                particleOrbView.triggerPulse()

                // Cascade cards entrance
                viewsToStagger.forEachIndexed { index, view ->
                    view.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setStartDelay((index * 40).toLong())
                        .setDuration(300)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
        }
    }
}

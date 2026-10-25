package com.personal.friday

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var layoutHeader: View
    private lateinit var tvGreeting: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var layoutCoreBadge: View
    private lateinit var dotCoreStatus: View
    private lateinit var tvCoreBadge: TextView
    private lateinit var btnOpenSettings: ImageButton

    private lateinit var flOrbContainer: FrameLayout
    private lateinit var particleOrbView: ParticleOrbView
    private lateinit var particleView: NanotechParticleView
    private lateinit var tvCoreStatus: TextView
    private lateinit var tvOrbHint: TextView

    private lateinit var tvSectionSentinels: TextView
    private lateinit var layoutBentoRow1: View
    private lateinit var cardSentry: View
    private lateinit var tvSentryStatus: TextView
    private lateinit var cardDoomscroll: View
    private lateinit var tvDoomscrollStatus: TextView
    private lateinit var tvDoomscrollMetric: TextView
    private lateinit var pbDoomscroll: ProgressBar

    private lateinit var tvSectionVitals: TextView
    private lateinit var layoutBentoRow2: View
    private lateinit var cardTelemetryPower: View
    private lateinit var tvBatteryPercent: TextView
    private lateinit var pbBattery: ProgressBar
    private lateinit var tvThermalStatus: TextView
    private lateinit var cardTelemetryMemory: View
    private lateinit var tvRamUsage: TextView
    private lateinit var pbRam: ProgressBar
    private lateinit var tvRamState: TextView

    private lateinit var tvSectionActions: TextView
    private lateinit var layoutBentoRow3: View
    private lateinit var chipWhatsApp: View
    private lateinit var chipMusic: View
    private lateinit var chipAutoType: View
    private lateinit var chipSyncWidget: View

    private lateinit var layoutBottomDock: View
    private lateinit var btnToggleService: Button

    private var isServiceRunning = false
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val telemetryHandler = Handler(Looper.getMainLooper())
    private val telemetryRunnable = object : Runnable {
        override fun run() {
            updateServiceStateUI()
            updateTelemetry()
            telemetryHandler.postDelayed(this, 1500L)
        }
    }

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
            Toast.makeText(this, "Screen capture permission denied, Sir.", Toast.LENGTH_SHORT).show()
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
        updateDynamicGreeting()
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
        updateDynamicGreeting()
        updateServiceStateUI()
        updateTelemetry()
        particleOrbView.startLoop()
        telemetryHandler.removeCallbacks(telemetryRunnable)
        telemetryHandler.post(telemetryRunnable)
    }

    override fun onPause() {
        super.onPause()
        particleOrbView.stopLoop()
        telemetryHandler.removeCallbacks(telemetryRunnable)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateServiceStateUI()
        updateTelemetry()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }

    private fun initViews() {
        // Header
        layoutHeader = findViewById(R.id.layoutHeader)
        tvGreeting = findViewById(R.id.tvGreeting)
        tvTitle = findViewById(R.id.tvTitle)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        layoutCoreBadge = findViewById(R.id.layoutCoreBadge)
        dotCoreStatus = findViewById(R.id.dotCoreStatus)
        tvCoreBadge = findViewById(R.id.tvCoreBadge)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)

        // Hero Orb
        flOrbContainer = findViewById(R.id.flOrbContainer)
        particleOrbView = findViewById(R.id.particleOrbView)
        particleView = findViewById(R.id.particleView)
        tvCoreStatus = findViewById(R.id.tvCoreStatus)
        tvOrbHint = findViewById(R.id.tvOrbHint)

        // Bento Row 1: Sentinels
        tvSectionSentinels = findViewById(R.id.tvSectionSentinels)
        layoutBentoRow1 = findViewById(R.id.layoutBentoRow1)
        cardSentry = findViewById(R.id.cardSentry)
        tvSentryStatus = findViewById(R.id.tvSentryStatus)
        cardDoomscroll = findViewById(R.id.cardDoomscroll)
        tvDoomscrollStatus = findViewById(R.id.tvDoomscrollStatus)
        tvDoomscrollMetric = findViewById(R.id.tvDoomscrollMetric)
        pbDoomscroll = findViewById(R.id.pbDoomscroll)

        // Bento Row 2: Vitals
        tvSectionVitals = findViewById(R.id.tvSectionVitals)
        layoutBentoRow2 = findViewById(R.id.layoutBentoRow2)
        cardTelemetryPower = findViewById(R.id.cardTelemetryPower)
        tvBatteryPercent = findViewById(R.id.tvBatteryPercent)
        pbBattery = findViewById(R.id.pbBattery)
        tvThermalStatus = findViewById(R.id.tvThermalStatus)
        cardTelemetryMemory = findViewById(R.id.cardTelemetryMemory)
        tvRamUsage = findViewById(R.id.tvRamUsage)
        pbRam = findViewById(R.id.pbRam)
        tvRamState = findViewById(R.id.tvRamState)

        // Bento Row 3: Actions
        tvSectionActions = findViewById(R.id.tvSectionActions)
        layoutBentoRow3 = findViewById(R.id.layoutBentoRow3)
        chipWhatsApp = findViewById(R.id.chipWhatsApp)
        chipMusic = findViewById(R.id.chipMusic)
        chipAutoType = findViewById(R.id.chipAutoType)
        chipSyncWidget = findViewById(R.id.chipSyncWidget)

        // Bottom Dock
        layoutBottomDock = findViewById(R.id.layoutBottomDock)
        btnToggleService = findViewById(R.id.btnToggleService)

        // Settings Transition
        btnOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            if (Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_right, R.anim.slide_out_left)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        // Orb Click Neural Link
        particleOrbView.onOrbClickListener = {
            tvCoreStatus.text = "NEURAL LINK ACTIVE • LISTENING"
            tvCoreStatus.setTextColor(getColor(R.color.stark_cyan))
            if (isTtsReady) {
                tts?.speak("All systems nominal, Sir. Online and ready.", TextToSpeech.QUEUE_FLUSH, null, "orb_click")
            }
            flOrbContainer.postDelayed({
                updateServiceStateUI()
            }, 3000)
        }

        // Bento Card Clicks
        cardSentry.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        cardDoomscroll.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "Enable F.R.I.D.A.Y. Accessibility to arm Reel Sentry", Toast.LENGTH_LONG).show()
            } else {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }

        // Quick Action Chips Clicks
        chipWhatsApp.setOnClickListener {
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (launchIntent != null) {
                    startActivity(launchIntent)
                    if (isTtsReady) tts?.speak("Opening WhatsApp, Sir.", TextToSpeech.QUEUE_FLUSH, null, "chip_wa")
                } else {
                    Toast.makeText(this, "WhatsApp is not installed on this device.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to launch WhatsApp.", Toast.LENGTH_SHORT).show()
            }
        }

        chipMusic.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=trending+music"))
                startActivity(intent)
                if (isTtsReady) tts?.speak("Opening music, Sir.", TextToSpeech.QUEUE_FLUSH, null, "chip_music")
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to launch music player.", Toast.LENGTH_SHORT).show()
            }
        }

        chipAutoType.setOnClickListener {
            val isA11yEnabled = isAccessibilityServiceEnabled()
            val msg = if (isA11yEnabled) {
                "Smart Auto-Type is Active! Say 'type [your text]' in any chat, Sir."
            } else {
                "Enable F.R.I.D.A.Y. Accessibility in Settings to use Smart Auto-Type."
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            if (isTtsReady) tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "chip_autotype")
        }

        chipSyncWidget.setOnClickListener {
            com.personal.friday.widget.FridayHudWidgetProvider.triggerAsyncWeatherRefresh(this)
            Toast.makeText(this, "HUD Widget refreshed with latest telemetry, Sir.", Toast.LENGTH_SHORT).show()
            if (isTtsReady) tts?.speak("HUD Widget synchronized, Sir.", TextToSpeech.QUEUE_FLUSH, null, "chip_sync")
        }

        // Toggle Service Button
        btnToggleService.setOnClickListener {
            if (isServiceRunning && FridayOverlayService.hasScreenCapture) {
                stopFridayService()
            } else {
                checkPermissionsAndStart()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedServiceName = "$packageName/${FridayAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(expectedServiceName) || enabledServices.contains(FridayAccessibilityService::class.java.simpleName)
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isTtsReady = true
            }
        }
    }

    private fun updateDynamicGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11 -> "GOOD MORNING, SIR"
            in 12..16 -> "GOOD AFTERNOON, SIR"
            in 17..21 -> "GOOD EVENING, SIR"
            in 22..23 -> "LATE NIGHT, SIR"
            else -> "SYSTEMS ON STANDBY, SIR"
        }
        tvGreeting.text = greeting
    }

    private fun updateTelemetry() {
        updateDynamicGreeting()

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

                val chargingText = if (isCharging) "Charging" else "Optimal"
                tvBatteryPercent.text = "$batteryPct%"
                pbBattery.progress = batteryPct
                tvThermalStatus.text = "Thermals: ${String.format(Locale.US, "%.1f", tempCelsius)}°C • $chargingText"
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
                val ramPct = ((usedGb / totalGb) * 100).toInt().coerceIn(0, 100)
                tvRamUsage.text = String.format(Locale.US, "%.1f / %.1f GB", usedGb, totalGb)
                pbRam.progress = ramPct
                val memPressure = if (memInfo.lowMemory) "High" else "Nominal"
                tvRamState.text = "Pressure: $memPressure"
            }
        } catch (_: Exception) {}
    }

    private fun updateServiceStateUI() {
        isServiceRunning = FridayOverlayService.isRunning
        val hasCapture = FridayOverlayService.hasScreenCapture
        val prefs = getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
        val isOverwatchEnabled = prefs.getBoolean("overwatch_enabled", true)
        val isDoomscrollEnabled = prefs.getBoolean("doomscroll_enabled", true)

        if (isServiceRunning) {
            if (hasCapture) {
                tvCoreStatus.text = "F.R.I.D.A.Y. ONLINE • SCREEN SENTRY ARMED"
                tvCoreStatus.setTextColor(getColor(R.color.stark_cyan))
                dotCoreStatus.setBackgroundResource(R.drawable.bg_dot_green)
                tvCoreBadge.text = "ONLINE"
                tvCoreBadge.setTextColor(Color.WHITE)

                tvSentryStatus.text = if (isOverwatchEnabled) "ACTIVE" else "STANDBY"
                tvSentryStatus.setTextColor(if (isOverwatchEnabled) Color.parseColor("#00E676") else Color.parseColor("#BDBDBD"))

                btnToggleService.text = "DEACTIVATE OVERLAY"
                btnToggleService.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#C62828"))
                btnToggleService.setTextColor(Color.WHITE)
            } else {
                tvCoreStatus.text = "OVERLAY ACTIVE • TAP TO ARM SENTRY"
                tvCoreStatus.setTextColor(getColor(R.color.stark_gold))
                dotCoreStatus.setBackgroundResource(R.drawable.bg_dot_active)
                tvCoreBadge.text = "AWAITING SENTRY"
                tvCoreBadge.setTextColor(Color.WHITE)

                tvSentryStatus.text = "STANDBY"
                tvSentryStatus.setTextColor(Color.parseColor("#BDBDBD"))

                btnToggleService.text = "ARM SCREEN SENTRY"
                btnToggleService.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF6D00"))
                btnToggleService.setTextColor(Color.BLACK)
            }
        } else {
            tvCoreStatus.text = "ALL SYSTEMS NOMINAL • CORE AT 100%"
            tvCoreStatus.setTextColor(getColor(R.color.stark_gold))
            dotCoreStatus.setBackgroundResource(R.drawable.bg_dot_inactive)
            tvCoreBadge.text = "STANDBY"
            tvCoreBadge.setTextColor(getColor(R.color.stark_text_dim))

            tvSentryStatus.text = "OFFLINE"
            tvSentryStatus.setTextColor(Color.parseColor("#757575"))

            btnToggleService.text = "INITIALIZE OVERLAY"
            btnToggleService.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF6D00"))
            btnToggleService.setTextColor(Color.BLACK)
        }

        // Doomscroll telemetry
        val reelsCount = FridayOverlayService.instance?.doomscrollReelCount ?: 0
        val isA11yOk = isAccessibilityServiceEnabled()
        if (!isA11yOk) {
            tvDoomscrollStatus.text = "A11Y OFF"
            tvDoomscrollStatus.setTextColor(Color.parseColor("#FF9100"))
            tvDoomscrollMetric.text = "Tap to Arm Guard"
            pbDoomscroll.progress = 0
        } else if (isDoomscrollEnabled) {
            tvDoomscrollStatus.text = "ARMED"
            tvDoomscrollStatus.setTextColor(Color.parseColor("#00E676"))
            tvDoomscrollMetric.text = "$reelsCount / 10 Reels"
            pbDoomscroll.progress = reelsCount
        } else {
            tvDoomscrollStatus.text = "DISABLED"
            tvDoomscrollStatus.setTextColor(Color.parseColor("#757575"))
            tvDoomscrollMetric.text = "Guard Paused"
            pbDoomscroll.progress = 0
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
        getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("friday_enabled", true)
            .apply()

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
        getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("friday_enabled", false)
            .apply()

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
            layoutHeader,
            flOrbContainer,
            tvCoreStatus,
            tvOrbHint,
            tvSectionSentinels,
            layoutBentoRow1,
            tvSectionVitals,
            layoutBentoRow2,
            tvSectionActions,
            layoutBentoRow3,
            layoutBottomDock
        )

        viewsToStagger.forEach { v ->
            v.alpha = 0f
            v.translationY = 30f
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
                        .setStartDelay((index * 35).toLong())
                        .setDuration(280)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
        }
    }
}
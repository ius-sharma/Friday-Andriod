package com.personal.friday

import android.app.AppOpsManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import com.personal.friday.meals.FridayMealScheduler
import com.personal.friday.meals.MealDialogueGenerator
import java.util.Calendar
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvOverlayPermissionStatus: TextView
    private lateinit var tvMicPermissionStatus: TextView
    private lateinit var tvAccessibilityPermissionStatus: TextView
    private lateinit var tvTelephonyPermissionStatus: TextView
    private lateinit var tvBatteryOptimizationStatus: TextView
    private lateinit var btnDisableBatteryRestrictions: TextView

    // Bento Summary Tiles
    private lateinit var tileBioFuel: View
    private lateinit var tvTileBioFuelStatus: TextView
    private lateinit var tvTileBioFuelSummary: TextView

    private lateinit var tileCallProxy: View
    private lateinit var tvTileCallProxyStatus: TextView
    private lateinit var tvTileCallProxySummary: TextView

    private lateinit var tileAppSentry: View
    private lateinit var tvTileAppSentryStatus: TextView
    private lateinit var tvTileAppSentrySummary: TextView

    private lateinit var tileVoiceMorning: View
    private lateinit var tvTileVoiceMorningStatus: TextView
    private lateinit var tvTileVoiceMorningSummary: TextView

    private lateinit var tileApiKey: View
    private lateinit var tvTileApiKeyStatus: TextView
    private lateinit var tvTileApiKeySummary: TextView

    private lateinit var tilePersonalContext: View
    private lateinit var tvTileContextStatus: TextView
    private lateinit var tvTileContextSummary: TextView

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionStatuses()
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updatePermissionStatuses()
    }

    private val telephonyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updatePermissionStatuses()
        updateAllTileSummaries()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        updatePermissionStatuses()
        updateAllTileSummaries()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
        updateAllTileSummaries()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        tvOverlayPermissionStatus = findViewById(R.id.tvOverlayPermissionStatus)
        tvMicPermissionStatus = findViewById(R.id.tvMicPermissionStatus)
        tvAccessibilityPermissionStatus = findViewById(R.id.tvAccessibilityPermissionStatus)
        tvTelephonyPermissionStatus = findViewById(R.id.tvTelephonyPermissionStatus)
        tvBatteryOptimizationStatus = findViewById(R.id.tvBatteryOptimizationStatus)
        btnDisableBatteryRestrictions = findViewById(R.id.btnDisableBatteryRestrictions)

        tileBioFuel = findViewById(R.id.tileBioFuel)
        tvTileBioFuelStatus = findViewById(R.id.tvTileBioFuelStatus)
        tvTileBioFuelSummary = findViewById(R.id.tvTileBioFuelSummary)

        tileCallProxy = findViewById(R.id.tileCallProxy)
        tvTileCallProxyStatus = findViewById(R.id.tvTileCallProxyStatus)
        tvTileCallProxySummary = findViewById(R.id.tvTileCallProxySummary)

        tileAppSentry = findViewById(R.id.tileAppSentry)
        tvTileAppSentryStatus = findViewById(R.id.tvTileAppSentryStatus)
        tvTileAppSentrySummary = findViewById(R.id.tvTileAppSentrySummary)

        tileVoiceMorning = findViewById(R.id.tileVoiceMorning)
        tvTileVoiceMorningStatus = findViewById(R.id.tvTileVoiceMorningStatus)
        tvTileVoiceMorningSummary = findViewById(R.id.tvTileVoiceMorningSummary)

        tileApiKey = findViewById(R.id.tileApiKey)
        tvTileApiKeyStatus = findViewById(R.id.tvTileApiKeyStatus)
        tvTileApiKeySummary = findViewById(R.id.tvTileApiKeySummary)

        tilePersonalContext = findViewById(R.id.tilePersonalContext)
        tvTileContextStatus = findViewById(R.id.tvTileContextStatus)
        tvTileContextSummary = findViewById(R.id.tvTileContextSummary)

        // Permission click listeners
        tvOverlayPermissionStatus.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            }
        }

        tvMicPermissionStatus.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }

        tvAccessibilityPermissionStatus.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Sir, enable 'Friday' in Installed/Downloaded Apps for Auto-Typing.", Toast.LENGTH_LONG).show()
        }

        tvTelephonyPermissionStatus.setOnClickListener {
            requestTelephonyPermissions()
        }

        btnDisableBatteryRestrictions.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(this, "Sir, please set Friday to 'Unrestricted' battery in Settings", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Bento Summary Tiles Click Listeners -> Slide-up Bottom Sheets
        tileBioFuel.setOnClickListener { showBioFuelBottomSheet() }
        tileCallProxy.setOnClickListener { showCallProxyBottomSheet() }
        tileAppSentry.setOnClickListener { showAppSentryBottomSheet() }
        tileVoiceMorning.setOnClickListener { showVoiceMorningBottomSheet() }
        tileApiKey.setOnClickListener { showApiKeyBottomSheet() }
        tilePersonalContext.setOnClickListener { showPersonalContextBottomSheet() }
    }

    private fun updateAllTileSummaries() {
        val prefs = getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)

        // 1. Bio-Fuel
        val isBioFuel = prefs.getBoolean(FridayMealScheduler.PREF_MEAL_ENABLED, true)
        val bTime = prefs.getString(FridayMealScheduler.PREF_BREAKFAST_TIME, FridayMealScheduler.DEFAULT_BREAKFAST) ?: FridayMealScheduler.DEFAULT_BREAKFAST
        val bCheckTime = prefs.getString(FridayMealScheduler.PREF_BREAKFAST_CHECK_TIME, FridayMealScheduler.DEFAULT_BREAKFAST_CHECK) ?: FridayMealScheduler.DEFAULT_BREAKFAST_CHECK
        val lTime = prefs.getString(FridayMealScheduler.PREF_LUNCH_TIME, FridayMealScheduler.DEFAULT_LUNCH) ?: FridayMealScheduler.DEFAULT_LUNCH
        val sTime = prefs.getString(FridayMealScheduler.PREF_SNACKS_TIME, FridayMealScheduler.DEFAULT_SNACKS) ?: FridayMealScheduler.DEFAULT_SNACKS
        val dTime = prefs.getString(FridayMealScheduler.PREF_DINNER_TIME, FridayMealScheduler.DEFAULT_DINNER) ?: FridayMealScheduler.DEFAULT_DINNER

        if (isBioFuel) {
            tvTileBioFuelStatus.text = "ONLINE • 5 SLOTS"
            tvTileBioFuelStatus.setTextColor(getColor(R.color.stark_gold))
            tvTileBioFuelSummary.text = "$bTime Nashta • $bCheckTime Check • $lTime Lunch • $sTime Snacks • $dTime Dinner"
        } else {
            tvTileBioFuelStatus.text = "STANDBY (DISABLED)"
            tvTileBioFuelStatus.setTextColor(getColor(R.color.stark_text_dim))
            tvTileBioFuelSummary.text = "Meal alerts paused. Tap to enable."
        }

        // 2. Call Proxy
        val hasCall = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (hasCall) {
            tvTileCallProxyStatus.text = "ACTIVE • VOICE AGENT"
            tvTileCallProxyStatus.setTextColor(getColor(R.color.stark_gold))
            tvTileCallProxySummary.text = "Auto-answers calls, speakerphone audio bridge, barge-in & briefing"
        } else {
            tvTileCallProxyStatus.text = "PERMISSION NEEDED"
            tvTileCallProxyStatus.setTextColor(getColor(R.color.stark_red))
            tvTileCallProxySummary.text = "Tap to grant telephony permissions for in-call assistance"
        }

        // 3. App Sentry
        val isOverwatch = prefs.getBoolean("overwatch_enabled", true)
        val isDoomscroll = prefs.getBoolean("doomscroll_enabled", true)
        val overwatchStr = if (isOverwatch) "Overwatch ON" else "Overwatch OFF"
        val doomscrollStr = if (isDoomscroll) "15M Reel Limit" else "Limit OFF"
        tvTileAppSentryStatus.text = "$overwatchStr • $doomscrollStr".uppercase()
        tvTileAppSentryStatus.setTextColor(if (isOverwatch || isDoomscroll) getColor(R.color.stark_gold) else getColor(R.color.stark_text_dim))
        tvTileAppSentrySummary.text = "Auto screen context inspection on app launch + social media intervention"

        // 4. Voice & Morning
        val isWakeWord = prefs.getBoolean("wakeword_enabled", true)
        val isMorning = prefs.getBoolean("morning_greeting_enabled", true)
        val wakeStr = if (isWakeWord) "Wake Word ON" else "Wake Word OFF"
        val morningStr = if (isMorning) "5-11 AM Briefing" else "Briefing OFF"
        tvTileVoiceMorningStatus.text = "$wakeStr • $morningStr".uppercase()
        tvTileVoiceMorningStatus.setTextColor(if (isWakeWord || isMorning) getColor(R.color.stark_gold) else getColor(R.color.stark_text_dim))
        tvTileVoiceMorningSummary.text = "Say 'Friday' or 'Hey Friday' hotword & first daily unlock greeting"

        // 5. API Key
        val apiKey = prefs.getString("gemini_api_key", "")
        if (!apiKey.isNullOrBlank()) {
            tvTileApiKeyStatus.text = "CONFIGURED (SECURE)"
            tvTileApiKeyStatus.setTextColor(getColor(R.color.stark_cyan))
            tvTileApiKeySummary.text = "Gemini 2.5 Flash Cloud Brain active for vision and reasoning"
        } else {
            tvTileApiKeyStatus.text = "NOT SET (TAP TO CONFIGURE)"
            tvTileApiKeyStatus.setTextColor(getColor(R.color.stark_red))
            tvTileApiKeySummary.text = "Tap to enter Gemini API key for smart multimodal analysis"
        }

        // 6. Personal Context
        val userContext = prefs.getString("user_personal_context", "")
        if (!userContext.isNullOrBlank()) {
            tvTileContextStatus.text = "PROFILE LOCKED IN STORAGE"
            tvTileContextStatus.setTextColor(getColor(R.color.stark_gold))
            val preview = userContext.lines().firstOrNull { it.isNotBlank() } ?: "Custom profile active"
            tvTileContextSummary.text = preview
        } else {
            tvTileContextStatus.text = "NO CONTEXT CONFIGURED"
            tvTileContextStatus.setTextColor(getColor(R.color.stark_text_dim))
            tvTileContextSummary.text = "Tap to train Friday on your habits, targets, and communication rules"
        }
    }

    // ==========================================
    // SLIDE-UP BOTTOM SHEETS
    // ==========================================

    private fun showBioFuelBottomSheet() {
        val dialog = BottomSheetDialog(this, R.style.Theme_Friday_BottomSheetDialog)
        val sheetView = layoutInflater.inflate(R.layout.sheet_bio_fuel, null)
        dialog.setContentView(sheetView)

        val prefs = getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)

        val switchBioFuel = sheetView.findViewById<SwitchMaterial>(R.id.switchBioFuelSheet)
        val tvBioFuelState = sheetView.findViewById<TextView>(R.id.tvBioFuelSheetState)

        val rowBreakfastTime = sheetView.findViewById<View>(R.id.rowBreakfastTimeSheet)
        val tvBreakfastTime = sheetView.findViewById<TextView>(R.id.tvBreakfastTimeSheet)
        val rowBreakfastCheckTime = sheetView.findViewById<View>(R.id.rowBreakfastCheckTimeSheet)
        val tvBreakfastCheckTime = sheetView.findViewById<TextView>(R.id.tvBreakfastCheckTimeSheet)
        val rowLunchTime = sheetView.findViewById<View>(R.id.rowLunchTimeSheet)
        val tvLunchTime = sheetView.findViewById<TextView>(R.id.tvLunchTimeSheet)
        val rowSnacksTime = sheetView.findViewById<View>(R.id.rowSnacksTimeSheet)
        val tvSnacksTime = sheetView.findViewById<TextView>(R.id.tvSnacksTimeSheet)
        val rowDinnerTime = sheetView.findViewById<View>(R.id.rowDinnerTimeSheet)
        val tvDinnerTime = sheetView.findViewById<TextView>(R.id.tvDinnerTimeSheet)
        val btnTestBioFuelAlert = sheetView.findViewById<Button>(R.id.btnTestBioFuelSheet)

        val isBioFuelEnabled = prefs.getBoolean(FridayMealScheduler.PREF_MEAL_ENABLED, true)
        switchBioFuel.isChecked = isBioFuelEnabled
        tvBioFuelState.text = if (isBioFuelEnabled) "ONLINE • 5 DAILY ALERTS" else "Status: STANDBY (Disabled)"
        tvBioFuelState.setTextColor(if (isBioFuelEnabled) getColor(R.color.stark_gold) else getColor(R.color.stark_text_dim))

        tvBreakfastTime.text = prefs.getString(FridayMealScheduler.PREF_BREAKFAST_TIME, FridayMealScheduler.DEFAULT_BREAKFAST)
        tvBreakfastCheckTime.text = prefs.getString(FridayMealScheduler.PREF_BREAKFAST_CHECK_TIME, FridayMealScheduler.DEFAULT_BREAKFAST_CHECK)
        tvLunchTime.text = prefs.getString(FridayMealScheduler.PREF_LUNCH_TIME, FridayMealScheduler.DEFAULT_LUNCH)
        tvSnacksTime.text = prefs.getString(FridayMealScheduler.PREF_SNACKS_TIME, FridayMealScheduler.DEFAULT_SNACKS)
        tvDinnerTime.text = prefs.getString(FridayMealScheduler.PREF_DINNER_TIME, FridayMealScheduler.DEFAULT_DINNER)

        switchBioFuel.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(FridayMealScheduler.PREF_MEAL_ENABLED, isChecked).apply()
            tvBioFuelState.text = if (isChecked) "ONLINE • 5 DAILY ALERTS" else "Status: STANDBY (Disabled)"
            tvBioFuelState.setTextColor(if (isChecked) getColor(R.color.stark_gold) else getColor(R.color.stark_text_dim))
            if (isChecked) {
                FridayMealScheduler.scheduleAllMeals(this)
                Toast.makeText(this, "Bio-Fuel Sentry active. Meal alerts set, Sir.", Toast.LENGTH_SHORT).show()
            } else {
                FridayMealScheduler.cancelAllMeals(this)
                Toast.makeText(this, "Bio-Fuel Sentry paused, Sir.", Toast.LENGTH_SHORT).show()
            }
            updateAllTileSummaries()
        }

        rowBreakfastTime.setOnClickListener {
            showMealTimePicker(MealDialogueGenerator.MEAL_BREAKFAST, tvBreakfastTime, FridayMealScheduler.PREF_BREAKFAST_TIME)
        }
        rowBreakfastCheckTime.setOnClickListener {
            showMealTimePicker(MealDialogueGenerator.MEAL_BREAKFAST_CHECK, tvBreakfastCheckTime, FridayMealScheduler.PREF_BREAKFAST_CHECK_TIME)
        }
        rowLunchTime.setOnClickListener {
            showMealTimePicker(MealDialogueGenerator.MEAL_LUNCH, tvLunchTime, FridayMealScheduler.PREF_LUNCH_TIME)
        }
        rowSnacksTime.setOnClickListener {
            showMealTimePicker(MealDialogueGenerator.MEAL_SNACKS, tvSnacksTime, FridayMealScheduler.PREF_SNACKS_TIME)
        }
        rowDinnerTime.setOnClickListener {
            showMealTimePicker(MealDialogueGenerator.MEAL_DINNER, tvDinnerTime, FridayMealScheduler.PREF_DINNER_TIME)
        }

        btnTestBioFuelAlert.setOnClickListener {
            triggerTestMealAlert()
        }

        dialog.setOnDismissListener {
            updateAllTileSummaries()
        }
        dialog.show()
    }

    private fun showCallProxyBottomSheet() {
        val dialog = BottomSheetDialog(this, R.style.Theme_Friday_BottomSheetDialog)
        val sheetView = layoutInflater.inflate(R.layout.sheet_call_proxy, null)
        dialog.setContentView(sheetView)

        val btnTelephonyPerm = sheetView.findViewById<Button>(R.id.btnTelephonyPermSheet)
        val btnTestCallProxy = sheetView.findViewById<Button>(R.id.btnTestCallProxyIntroSheet)

        btnTelephonyPerm.setOnClickListener {
            requestTelephonyPermissions()
        }

        btnTestCallProxy.setOnClickListener {
            val introText = "Hello! Main Ayush Sir ki AI Personal Assistant Friday hoon. Sir abhi available nahi hain. Kya aap mujhe bata sakte hain kya zaroori kaam hai?"
            FridayOverlayService.instance?.let { service ->
                service.pulseBubbleGlow()
                service.showWhisper("🤖 Friday: \"$introText\"", 6000L)
                service.ttsManager.speak(introText)
            } ?: run {
                val tts = TtsManager(this)
                tts.speak(introText)
            }
            Toast.makeText(this, "🤖 Call Proxy Intro tested, Sir!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showAppSentryBottomSheet() {
        val dialog = BottomSheetDialog(this, R.style.Theme_Friday_BottomSheetDialog)
        val sheetView = layoutInflater.inflate(R.layout.sheet_app_sentry, null)
        dialog.setContentView(sheetView)

        val prefs = getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)

        val switchOverwatch = sheetView.findViewById<SwitchMaterial>(R.id.switchOverwatchSheet)
        val tvOverwatchState = sheetView.findViewById<TextView>(R.id.tvOverwatchSheetState)
        val switchDoomscroll = sheetView.findViewById<SwitchMaterial>(R.id.switchDoomscrollSheet)
        val tvDoomscrollState = sheetView.findViewById<TextView>(R.id.tvDoomscrollSheetState)
        val tvUsageAccessPrompt = sheetView.findViewById<TextView>(R.id.tvUsageAccessPromptSheet)

        val isOverwatchEnabled = prefs.getBoolean("overwatch_enabled", true)
        switchOverwatch.isChecked = isOverwatchEnabled
        tvOverwatchState.text = if (isOverwatchEnabled) "Status: ACTIVE (Inspection on app launch)" else "Status: DISABLED (Paused)"
        tvOverwatchState.setTextColor(if (isOverwatchEnabled) getColor(R.color.stark_cyan) else getColor(R.color.stark_text_dim))

        switchOverwatch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("overwatch_enabled", isChecked).apply()
            tvOverwatchState.text = if (isChecked) "Status: ACTIVE (Inspection on app launch)" else "Status: DISABLED (Paused)"
            tvOverwatchState.setTextColor(if (isChecked) getColor(R.color.stark_cyan) else getColor(R.color.stark_text_dim))
            if (FridayOverlayService.isRunning) {
                val updateIntent = Intent(this, FridayOverlayService::class.java).apply {
                    action = FridayOverlayService.ACTION_UPDATE_OVERWATCH
                    putExtra(FridayOverlayService.EXTRA_OVERWATCH_ENABLED, isChecked)
                }
                startService(updateIntent)
            }
            updateAllTileSummaries()
        }

        val isDoomscrollEnabled = prefs.getBoolean("doomscroll_enabled", true)
        switchDoomscroll.isChecked = isDoomscrollEnabled
        tvDoomscrollState.text = if (isDoomscrollEnabled) "Status: ACTIVE • Intervenes after 15 mins" else "Status: DISABLED (Paused)"
        tvDoomscrollState.setTextColor(if (isDoomscrollEnabled) getColor(R.color.stark_cyan) else getColor(R.color.stark_text_dim))

        switchDoomscroll.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("doomscroll_enabled", isChecked).apply()
            tvDoomscrollState.text = if (isChecked) "Status: ACTIVE • Intervenes after 15 mins" else "Status: DISABLED (Paused)"
            tvDoomscrollState.setTextColor(if (isChecked) getColor(R.color.stark_cyan) else getColor(R.color.stark_text_dim))
            if (FridayOverlayService.isRunning) {
                val updateIntent = Intent(this, FridayOverlayService::class.java).apply {
                    action = FridayOverlayService.ACTION_UPDATE_DOOMSCROLL
                    putExtra(FridayOverlayService.EXTRA_DOOMSCROLL_ENABLED, isChecked)
                }
                startService(updateIntent)
            }
            updateAllTileSummaries()
        }

        tvUsageAccessPrompt.setOnClickListener {
            if (!hasUsageStatsPermission()) {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "Sir, please allow Usage Access for auto Home Screen detection.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Usage Access already granted, Sir.", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.setOnDismissListener {
            updateAllTileSummaries()
        }
        dialog.show()
    }

    private fun showVoiceMorningBottomSheet() {
        val dialog = BottomSheetDialog(this, R.style.Theme_Friday_BottomSheetDialog)
        val sheetView = layoutInflater.inflate(R.layout.sheet_voice_morning, null)
        dialog.setContentView(sheetView)

        val prefs = getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)

        val switchWakeWord = sheetView.findViewById<SwitchMaterial>(R.id.switchWakeWordSheet)
        val tvWakeWordState = sheetView.findViewById<TextView>(R.id.tvWakeWordSheetState)
        val switchMorningGreeting = sheetView.findViewById<SwitchMaterial>(R.id.switchMorningGreetingSheet)
        val tvMorningGreetingState = sheetView.findViewById<TextView>(R.id.tvMorningGreetingSheetState)
        val btnTestMorningGreeting = sheetView.findViewById<Button>(R.id.btnTestMorningGreetingSheet)

        val isWakeWordEnabled = prefs.getBoolean("wakeword_enabled", true)
        switchWakeWord.isChecked = isWakeWordEnabled
        tvWakeWordState.text = if (isWakeWordEnabled) "Status: ACTIVE • Say 'Friday' or 'Hey Friday'" else "Status: DISABLED (Paused)"
        tvWakeWordState.setTextColor(if (isWakeWordEnabled) getColor(R.color.stark_cyan) else getColor(R.color.stark_text_dim))

        switchWakeWord.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("wakeword_enabled", isChecked).apply()
            tvWakeWordState.text = if (isChecked) "Status: ACTIVE • Say 'Friday' or 'Hey Friday'" else "Status: DISABLED (Paused)"
            tvWakeWordState.setTextColor(if (isChecked) getColor(R.color.stark_cyan) else getColor(R.color.stark_text_dim))
            if (FridayOverlayService.isRunning) {
                val updateIntent = Intent(this, FridayOverlayService::class.java).apply {
                    action = FridayOverlayService.ACTION_UPDATE_WAKEWORD
                    putExtra(FridayOverlayService.EXTRA_WAKEWORD_ENABLED, isChecked)
                }
                startService(updateIntent)
            }
            updateAllTileSummaries()
        }

        val isMorningGreetingEnabled = prefs.getBoolean("morning_greeting_enabled", true)
        switchMorningGreeting.isChecked = isMorningGreetingEnabled
        tvMorningGreetingState.text = if (isMorningGreetingEnabled) "Status: ACTIVE • Greets on first unlock (5 AM - 12 PM)" else "Status: DISABLED (Paused)"
        tvMorningGreetingState.setTextColor(if (isMorningGreetingEnabled) getColor(R.color.stark_cyan) else getColor(R.color.stark_text_dim))

        switchMorningGreeting.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("morning_greeting_enabled", isChecked).apply()
            tvMorningGreetingState.text = if (isChecked) "Status: ACTIVE • Greets on first unlock (5 AM - 12 PM)" else "Status: DISABLED (Paused)"
            tvMorningGreetingState.setTextColor(if (isChecked) getColor(R.color.stark_cyan) else getColor(R.color.stark_text_dim))
            updateAllTileSummaries()
        }

        btnTestMorningGreeting.setOnClickListener {
            if (FridayOverlayService.isRunning) {
                FridayOverlayService.instance?.checkAndTriggerMorningGreeting(isManualTest = true)
                Toast.makeText(this, "🌅 Morning Briefing triggered, Sir!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Sir, initialize Friday Overlay first to hear the morning briefing.", Toast.LENGTH_LONG).show()
            }
        }

        dialog.setOnDismissListener {
            updateAllTileSummaries()
        }
        dialog.show()
    }

    private fun showApiKeyBottomSheet() {
        val dialog = BottomSheetDialog(this, R.style.Theme_Friday_BottomSheetDialog)
        val sheetView = layoutInflater.inflate(R.layout.sheet_api_key, null)
        dialog.setContentView(sheetView)

        val prefs = getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
        val etApiKey = sheetView.findViewById<EditText>(R.id.etApiKeySheet)
        val btnSaveApiKey = sheetView.findViewById<Button>(R.id.btnSaveApiKeySheet)

        val savedKey = prefs.getString("gemini_api_key", "")
        if (!savedKey.isNullOrBlank()) {
            etApiKey.setText(savedKey)
        }

        btnSaveApiKey.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            if (key.isNotEmpty()) {
                prefs.edit().putString("gemini_api_key", key).apply()
                Toast.makeText(this, "API Key successfully stored, Sir.", Toast.LENGTH_SHORT).show()
                updateAllTileSummaries()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter a valid key, Sir.", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showPersonalContextBottomSheet() {
        val dialog = BottomSheetDialog(this, R.style.Theme_Friday_BottomSheetDialog)
        val sheetView = layoutInflater.inflate(R.layout.sheet_personal_context, null)
        dialog.setContentView(sheetView)

        val prefs = getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
        val etUserContext = sheetView.findViewById<EditText>(R.id.etUserContextSheet)
        val btnDefaultUserContext = sheetView.findViewById<Button>(R.id.btnDefaultUserContextSheet)
        val btnSaveUserContext = sheetView.findViewById<Button>(R.id.btnSaveUserContextSheet)

        val savedContext = prefs.getString("user_personal_context", "")
        if (!savedContext.isNullOrBlank()) {
            etUserContext.setText(savedContext)
        }

        btnDefaultUserContext.setOnClickListener {
            val template = """
Role: Android & AI Specialist
Focus: Developing F.R.I.D.A.Y. Cybernetic System
Daily Goals: Sleep before 1 AM, code daily, limit doomscrolling to 15 mins
Behavior Rules: Keep me accountable, be witty and slightly sarcastic if I procrastinate
            """.trimIndent()
            etUserContext.setText(template)
            Toast.makeText(this, "Template loaded. Tap SAVE PROFILE to store.", Toast.LENGTH_SHORT).show()
        }

        btnSaveUserContext.setOnClickListener {
            val contextText = etUserContext.text.toString().trim()
            prefs.edit().putString("user_personal_context", contextText).apply()
            Toast.makeText(this, "🔒 Sir, personal profile securely locked in local storage.", Toast.LENGTH_SHORT).show()
            updateAllTileSummaries()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showMealTimePicker(mealType: String, timeView: TextView, prefKey: String) {
        val curr = timeView.text.toString()
        val parts = curr.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 12
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0

        TimePickerDialog(this, { _, hourOfDay, minute ->
            val formatted = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
            timeView.text = formatted
            getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString(prefKey, formatted)
                .apply()

            FridayMealScheduler.scheduleSingleMeal(this, mealType, formatted)
            Toast.makeText(this, "${MealDialogueGenerator.getMealDisplayName(mealType)} alert set for $formatted, Sir.", Toast.LENGTH_SHORT).show()
            updateAllTileSummaries()
        }, h, m, true).show()
    }

    private fun triggerTestMealAlert() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val mealType = when (hour) {
            in 5..7 -> MealDialogueGenerator.MEAL_BREAKFAST
            in 8..10 -> MealDialogueGenerator.MEAL_BREAKFAST_CHECK
            in 11..15 -> MealDialogueGenerator.MEAL_LUNCH
            in 16..18 -> MealDialogueGenerator.MEAL_SNACKS
            else -> MealDialogueGenerator.MEAL_DINNER
        }

        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 280)
        } catch (_: Exception) {}

        val dialogue = MealDialogueGenerator.getNextDialogue(this, mealType)

        if (FridayOverlayService.isRunning && FridayOverlayService.instance != null) {
            FridayOverlayService.instance?.triggerMealAlert(mealType, dialogue)
        } else {
            val tts = TtsManager(this)
            tts.onReadyListener = {
                tts.speak(dialogue)
            }
        }
        Toast.makeText(this, "🍲 Bio-Fuel test alert triggered for ${MealDialogueGenerator.getMealDisplayName(mealType)}, Sir!", Toast.LENGTH_SHORT).show()
    }

    private fun updatePermissionStatuses() {
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        if (hasOverlay) {
            tvOverlayPermissionStatus.text = "OVERLAY: OK"
            tvOverlayPermissionStatus.setTextColor(getColor(R.color.stark_cyan))
        } else {
            tvOverlayPermissionStatus.text = "OVERLAY: GRANT"
            tvOverlayPermissionStatus.setTextColor(getColor(R.color.stark_red))
        }

        val hasMic = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMic) {
            tvMicPermissionStatus.text = "MIC: OK"
            tvMicPermissionStatus.setTextColor(getColor(R.color.stark_cyan))
        } else {
            tvMicPermissionStatus.text = "MIC: GRANT"
            tvMicPermissionStatus.setTextColor(getColor(R.color.stark_red))
        }

        val hasAccessibility = FridayAccessibilityService.isRunning()
        if (hasAccessibility) {
            tvAccessibilityPermissionStatus.text = "AUTO-TYPE: OK"
            tvAccessibilityPermissionStatus.setTextColor(getColor(R.color.stark_cyan))
        } else {
            tvAccessibilityPermissionStatus.text = "AUTO-TYPE: GRANT"
            tvAccessibilityPermissionStatus.setTextColor(getColor(R.color.stark_gold))
        }

        val hasCall = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val hasState = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasCallLog = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val hasContacts = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasAnswer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
        } else true

        if (hasCall && hasState && hasCallLog && hasContacts && hasAnswer) {
            tvTelephonyPermissionStatus.text = "CALLS: OK"
            tvTelephonyPermissionStatus.setTextColor(getColor(R.color.stark_cyan))
        } else {
            tvTelephonyPermissionStatus.text = "CALLS: GRANT"
            tvTelephonyPermissionStatus.setTextColor(getColor(R.color.stark_gold))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName)
            if (isIgnoring) {
                tvBatteryOptimizationStatus.text = "BATTERY: OPTIMAL"
                tvBatteryOptimizationStatus.setTextColor(getColor(R.color.stark_cyan))
                btnDisableBatteryRestrictions.text = "✓ 24/7 Unrestricted Battery Active (Resilient against deep sleep)"
                btnDisableBatteryRestrictions.alpha = 0.7f
            } else {
                tvBatteryOptimizationStatus.text = "BATTERY: RESTRICTED"
                tvBatteryOptimizationStatus.setTextColor(getColor(R.color.stark_gold))
                btnDisableBatteryRestrictions.text = "Tap to set Unrestricted Battery (Prevents overnight sleep termination) ›"
                btnDisableBatteryRestrictions.alpha = 1.0f
            }
        }
    }

    private fun requestTelephonyPermissions() {
        val perms = mutableListOf(
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            perms.add(android.Manifest.permission.ANSWER_PHONE_CALLS)
        }
        telephonyPermissionLauncher.launch(perms.toTypedArray())
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }
}

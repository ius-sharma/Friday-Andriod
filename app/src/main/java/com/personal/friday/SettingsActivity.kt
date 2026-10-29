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
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.personal.friday.meals.FridayMealScheduler
import com.personal.friday.meals.MealDialogueGenerator
import java.util.Calendar
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var etApiKey: EditText
    private lateinit var btnSaveApiKey: Button
    private lateinit var tvOverlayPermissionStatus: TextView
    private lateinit var tvMicPermissionStatus: TextView
    private lateinit var tvAccessibilityPermissionStatus: TextView
    private lateinit var switchOverwatch: SwitchCompat
    private lateinit var tvOverwatchState: TextView
    private lateinit var switchDoomscroll: SwitchCompat
    private lateinit var tvDoomscrollState: TextView
    private lateinit var switchWakeWord: SwitchCompat
    private lateinit var tvWakeWordState: TextView
    private lateinit var switchMorningGreeting: SwitchCompat
    private lateinit var tvMorningGreetingState: TextView
    private lateinit var btnTestMorningGreeting: Button
    private lateinit var etUserContext: EditText
    private lateinit var btnSaveUserContext: Button
    private lateinit var btnDefaultUserContext: Button
    private lateinit var tvUsageAccessPrompt: TextView
    private lateinit var tvTelephonyPermissionStatus: TextView
    private lateinit var tvBatteryOptimizationStatus: TextView
    private lateinit var btnDisableBatteryRestrictions: Button
    private lateinit var btnTestCallProxyIntro: Button

    private lateinit var switchBioFuel: SwitchCompat
    private lateinit var tvBioFuelState: TextView
    private lateinit var rowBreakfastTime: LinearLayout
    private lateinit var tvBreakfastTime: TextView
    private lateinit var rowLunchTime: LinearLayout
    private lateinit var tvLunchTime: TextView
    private lateinit var rowSnacksTime: LinearLayout
    private lateinit var tvSnacksTime: TextView
    private lateinit var rowDinnerTime: LinearLayout
    private lateinit var tvDinnerTime: TextView
    private lateinit var btnTestBioFuelAlert: Button

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        loadSavedApiKey()
        updatePermissionStatuses()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        etApiKey = findViewById(R.id.etApiKey)
        btnSaveApiKey = findViewById(R.id.btnSaveApiKey)
        tvOverlayPermissionStatus = findViewById(R.id.tvOverlayPermissionStatus)
        tvMicPermissionStatus = findViewById(R.id.tvMicPermissionStatus)
        tvAccessibilityPermissionStatus = findViewById(R.id.tvAccessibilityPermissionStatus)
        switchOverwatch = findViewById(R.id.switchOverwatch)
        tvOverwatchState = findViewById(R.id.tvOverwatchState)
        switchDoomscroll = findViewById(R.id.switchDoomscroll)
        tvDoomscrollState = findViewById(R.id.tvDoomscrollState)
        switchWakeWord = findViewById(R.id.switchWakeWord)
        tvWakeWordState = findViewById(R.id.tvWakeWordState)
        switchMorningGreeting = findViewById(R.id.switchMorningGreeting)
        tvMorningGreetingState = findViewById(R.id.tvMorningGreetingState)
        btnTestMorningGreeting = findViewById(R.id.btnTestMorningGreeting)
        etUserContext = findViewById(R.id.etUserContext)
        btnSaveUserContext = findViewById(R.id.btnSaveUserContext)
        btnDefaultUserContext = findViewById(R.id.btnDefaultUserContext)
        tvUsageAccessPrompt = findViewById(R.id.tvUsageAccessPrompt)
        tvTelephonyPermissionStatus = findViewById(R.id.tvTelephonyPermissionStatus)
        tvBatteryOptimizationStatus = findViewById(R.id.tvBatteryOptimizationStatus)
        btnDisableBatteryRestrictions = findViewById(R.id.btnDisableBatteryRestrictions)
        btnTestCallProxyIntro = findViewById(R.id.btnTestCallProxyIntro)

        switchBioFuel = findViewById(R.id.switchBioFuel)
        tvBioFuelState = findViewById(R.id.tvBioFuelState)
        rowBreakfastTime = findViewById(R.id.rowBreakfastTime)
        tvBreakfastTime = findViewById(R.id.tvBreakfastTime)
        rowLunchTime = findViewById(R.id.rowLunchTime)
        tvLunchTime = findViewById(R.id.tvLunchTime)
        rowSnacksTime = findViewById(R.id.rowSnacksTime)
        tvSnacksTime = findViewById(R.id.tvSnacksTime)
        rowDinnerTime = findViewById(R.id.rowDinnerTime)
        tvDinnerTime = findViewById(R.id.tvDinnerTime)
        btnTestBioFuelAlert = findViewById(R.id.btnTestBioFuelAlert)

        setupBioFuelSettings()

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

        btnTestCallProxyIntro.setOnClickListener {
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

        tvTelephonyPermissionStatus.setOnClickListener {
            requestTelephonyPermissions()
        }

        val prefs = getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)

        val isOverwatchEnabled = prefs.getBoolean("overwatch_enabled", true)
        switchOverwatch.isChecked = isOverwatchEnabled
        updateOverwatchText(isOverwatchEnabled)

        switchOverwatch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("overwatch_enabled", isChecked).apply()
            updateOverwatchText(isChecked)
            if (FridayOverlayService.isRunning) {
                val updateIntent = Intent(this, FridayOverlayService::class.java).apply {
                    action = FridayOverlayService.ACTION_UPDATE_OVERWATCH
                    putExtra(FridayOverlayService.EXTRA_OVERWATCH_ENABLED, isChecked)
                }
                startService(updateIntent)
            }
        }

        val isDoomscrollEnabled = prefs.getBoolean("doomscroll_enabled", true)
        switchDoomscroll.isChecked = isDoomscrollEnabled
        updateDoomscrollText(isDoomscrollEnabled)

        switchDoomscroll.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("doomscroll_enabled", isChecked).apply()
            updateDoomscrollText(isChecked)
            if (FridayOverlayService.isRunning) {
                val updateIntent = Intent(this, FridayOverlayService::class.java).apply {
                    action = FridayOverlayService.ACTION_UPDATE_DOOMSCROLL
                    putExtra(FridayOverlayService.EXTRA_DOOMSCROLL_ENABLED, isChecked)
                }
                startService(updateIntent)
            }
        }

        val isWakeWordEnabled = prefs.getBoolean("wakeword_enabled", true)
        switchWakeWord.isChecked = isWakeWordEnabled
        updateWakeWordText(isWakeWordEnabled)

        switchWakeWord.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("wakeword_enabled", isChecked).apply()
            updateWakeWordText(isChecked)
            if (FridayOverlayService.isRunning) {
                val updateIntent = Intent(this, FridayOverlayService::class.java).apply {
                    action = FridayOverlayService.ACTION_UPDATE_WAKEWORD
                    putExtra(FridayOverlayService.EXTRA_WAKEWORD_ENABLED, isChecked)
                }
                startService(updateIntent)
            }
        }

        val isMorningGreetingEnabled = prefs.getBoolean("morning_greeting_enabled", true)
        switchMorningGreeting.isChecked = isMorningGreetingEnabled
        updateMorningGreetingText(isMorningGreetingEnabled)

        switchMorningGreeting.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("morning_greeting_enabled", isChecked).apply()
            updateMorningGreetingText(isChecked)
        }

        btnTestMorningGreeting.setOnClickListener {
            if (FridayOverlayService.isRunning) {
                FridayOverlayService.instance?.checkAndTriggerMorningGreeting(isManualTest = true)
                Toast.makeText(this, "🌅 Morning Briefing triggered, Sir!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Sir, initialize Friday Overlay first to hear the morning briefing.", Toast.LENGTH_LONG).show()
            }
        }

        loadSavedUserContext()

        btnSaveUserContext.setOnClickListener {
            val contextText = etUserContext.text.toString().trim()
            prefs.edit().putString("user_personal_context", contextText).apply()
            Toast.makeText(this, "🔒 Sir, personal profile securely locked in local storage.", Toast.LENGTH_SHORT).show()
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

        tvUsageAccessPrompt.setOnClickListener {
            if (!hasUsageStatsPermission()) {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "Sir, please allow Usage Access for auto Home Screen detection.", Toast.LENGTH_LONG).show()
            }
        }

        btnSaveApiKey.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            if (key.isNotEmpty()) {
                getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("gemini_api_key", key)
                    .apply()
                Toast.makeText(this, "API Key successfully stored, Sir.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter a valid key, Sir.", Toast.LENGTH_SHORT).show()
            }
        }

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
    }

    private fun loadSavedApiKey() {
        val savedKey = getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
            .getString("gemini_api_key", "")
        if (!savedKey.isNullOrBlank()) {
            etApiKey.setText(savedKey)
        }
    }

    private fun updatePermissionStatuses() {
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        if (hasOverlay) {
            tvOverlayPermissionStatus.text = "• Overlay Permission: GRANTED"
            tvOverlayPermissionStatus.setTextColor(getColor(R.color.stark_cyan))
        } else {
            tvOverlayPermissionStatus.text = "• Overlay Permission: REQUIRED (Tap to Grant)"
            tvOverlayPermissionStatus.setTextColor(getColor(R.color.stark_red))
        }

        val hasMic = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMic) {
            tvMicPermissionStatus.text = "• Microphone Access: GRANTED"
            tvMicPermissionStatus.setTextColor(getColor(R.color.stark_cyan))
        } else {
            tvMicPermissionStatus.text = "• Microphone Access: REQUIRED (Tap to Grant)"
            tvMicPermissionStatus.setTextColor(getColor(R.color.stark_red))
        }

        val hasAccessibility = FridayAccessibilityService.isRunning()
        if (hasAccessibility) {
            tvAccessibilityPermissionStatus.text = "• Auto-Type (Accessibility): ACTIVE"
            tvAccessibilityPermissionStatus.setTextColor(getColor(R.color.stark_cyan))
        } else {
            tvAccessibilityPermissionStatus.text = "• Auto-Type (Accessibility): REQUIRED (Tap to Enable)"
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
            tvTelephonyPermissionStatus.text = "• Calling & Caller ID: GRANTED"
            tvTelephonyPermissionStatus.setTextColor(getColor(R.color.stark_cyan))
        } else {
            tvTelephonyPermissionStatus.text = "• Calling & Caller ID: PERMISSION NEEDED (Tap to Grant)"
            tvTelephonyPermissionStatus.setTextColor(getColor(R.color.stark_gold))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName)
            if (isIgnoring) {
                tvBatteryOptimizationStatus.text = "• Overnight Run (No Battery Limits): UNRESTRICTED (Active)"
                tvBatteryOptimizationStatus.setTextColor(getColor(R.color.stark_cyan))
                btnDisableBatteryRestrictions.text = "BATTERY RESTRICTIONS DISABLED (24/7 ACTIVE)"
                btnDisableBatteryRestrictions.alpha = 0.6f
            } else {
                tvBatteryOptimizationStatus.text = "• Overnight Run: RESTRICTED (May sleep overnight)"
                tvBatteryOptimizationStatus.setTextColor(getColor(R.color.stark_gold))
                btnDisableBatteryRestrictions.text = "DISABLE BATTERY LIMITS (RUN OVERNIGHT)"
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

    private fun updateOverwatchText(enabled: Boolean) {
        if (enabled) {
            val usageStatus = if (hasUsageStatsPermission()) "Active" else "Usage Access Needed"
            tvOverwatchState.text = "Status: ACTIVE • Scanning memes & insights ($usageStatus)"
            tvOverwatchState.setTextColor(getColor(R.color.stark_cyan))
        } else {
            tvOverwatchState.text = "Status: DISABLED (Paused)"
            tvOverwatchState.setTextColor(getColor(R.color.stark_text_dim))
        }
    }

    private fun updateDoomscrollText(enabled: Boolean) {
        if (enabled) {
            tvDoomscrollState.text = "Status: ARMED • Limits 10 consecutive reels"
            tvDoomscrollState.setTextColor(getColor(R.color.stark_cyan))
        } else {
            tvDoomscrollState.text = "Status: DISABLED (Paused)"
            tvDoomscrollState.setTextColor(getColor(R.color.stark_text_dim))
        }
    }

    private fun setupBioFuelSettings() {
        val prefs = getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
        val isBioFuelEnabled = prefs.getBoolean(FridayMealScheduler.PREF_MEAL_ENABLED, true)
        switchBioFuel.isChecked = isBioFuelEnabled
        updateBioFuelText(isBioFuelEnabled)

        val bTime = prefs.getString(FridayMealScheduler.PREF_BREAKFAST_TIME, FridayMealScheduler.DEFAULT_BREAKFAST) ?: FridayMealScheduler.DEFAULT_BREAKFAST
        val lTime = prefs.getString(FridayMealScheduler.PREF_LUNCH_TIME, FridayMealScheduler.DEFAULT_LUNCH) ?: FridayMealScheduler.DEFAULT_LUNCH
        val sTime = prefs.getString(FridayMealScheduler.PREF_SNACKS_TIME, FridayMealScheduler.DEFAULT_SNACKS) ?: FridayMealScheduler.DEFAULT_SNACKS
        val dTime = prefs.getString(FridayMealScheduler.PREF_DINNER_TIME, FridayMealScheduler.DEFAULT_DINNER) ?: FridayMealScheduler.DEFAULT_DINNER

        tvBreakfastTime.text = bTime
        tvLunchTime.text = lTime
        tvSnacksTime.text = sTime
        tvDinnerTime.text = dTime

        switchBioFuel.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(FridayMealScheduler.PREF_MEAL_ENABLED, isChecked).apply()
            updateBioFuelText(isChecked)
            if (isChecked) {
                FridayMealScheduler.scheduleAllMeals(this)
                Toast.makeText(this, "Bio-Fuel Sentry active. 4 daily meal alerts set, Sir.", Toast.LENGTH_SHORT).show()
            } else {
                FridayMealScheduler.cancelAllMeals(this)
                Toast.makeText(this, "Bio-Fuel Sentry paused, Sir.", Toast.LENGTH_SHORT).show()
            }
        }

        rowBreakfastTime.setOnClickListener {
            showMealTimePicker(MealDialogueGenerator.MEAL_BREAKFAST, tvBreakfastTime, FridayMealScheduler.PREF_BREAKFAST_TIME)
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
    }

    private fun updateBioFuelText(enabled: Boolean) {
        if (enabled) {
            tvBioFuelState.text = "ONLINE • 4 DAILY ALERTS"
            tvBioFuelState.setTextColor(getColor(R.color.stark_gold))
        } else {
            tvBioFuelState.text = "Status: STANDBY (Disabled)"
            tvBioFuelState.setTextColor(getColor(R.color.stark_text_dim))
        }
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
        }, h, m, true).show()
    }

    private fun triggerTestMealAlert() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val mealType = when (hour) {
            in 5..10 -> MealDialogueGenerator.MEAL_BREAKFAST
            in 11..15 -> MealDialogueGenerator.MEAL_LUNCH
            in 16..18 -> MealDialogueGenerator.MEAL_SNACKS
            else -> MealDialogueGenerator.MEAL_DINNER
        }

        // 1. Signature Chime
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 280)
        } catch (_: Exception) {}

        // 2. Dynamic Dialogue Line
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

    private fun updateWakeWordText(enabled: Boolean) {
        if (!enabled) {
            tvWakeWordState.text = "Status: DISABLED (Paused)"
            tvWakeWordState.setTextColor(getColor(R.color.stark_text_dim))
        } else {
            tvWakeWordState.text = "Status: ACTIVE • Say 'Friday' or 'Hey Friday'"
            tvWakeWordState.setTextColor(getColor(R.color.stark_cyan))
        }
    }

    private fun updateMorningGreetingText(enabled: Boolean) {
        if (!enabled) {
            tvMorningGreetingState.text = "Status: DISABLED (Paused)"
            tvMorningGreetingState.setTextColor(getColor(R.color.stark_text_dim))
        } else {
            tvMorningGreetingState.text = "Status: ACTIVE • Greets on first unlock (5 AM - 12 PM)"
            tvMorningGreetingState.setTextColor(getColor(R.color.stark_cyan))
        }
    }

    private fun loadSavedUserContext() {
        val savedContext = getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
            .getString("user_personal_context", "")
        if (!savedContext.isNullOrBlank()) {
            etUserContext.setText(savedContext)
        }
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

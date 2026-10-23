package com.personal.friday

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

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
    private lateinit var tvUsageAccessPrompt: TextView

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
        tvUsageAccessPrompt = findViewById(R.id.tvUsageAccessPrompt)

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

        tvUsageAccessPrompt.setOnClickListener {
            if (!hasUsageStatsPermission()) {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "Boss, please allow Usage Access for auto Home Screen detection.", Toast.LENGTH_LONG).show()
            }
        }

        btnSaveApiKey.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            if (key.isNotEmpty()) {
                getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("gemini_api_key", key)
                    .apply()
                Toast.makeText(this, "API Key successfully stored, Boss.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter a valid key, Boss.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Boss, enable 'Friday' in Installed/Downloaded Apps for Auto-Typing.", Toast.LENGTH_LONG).show()
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

    private fun updateWakeWordText(enabled: Boolean) {
        if (!enabled) {
            tvWakeWordState.text = "Status: DISABLED (Paused)"
            tvWakeWordState.setTextColor(getColor(R.color.stark_text_dim))
        } else {
            tvWakeWordState.text = "Status: ACTIVE • Say 'Friday' or 'Hey Friday'"
            tvWakeWordState.setTextColor(getColor(R.color.stark_cyan))
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

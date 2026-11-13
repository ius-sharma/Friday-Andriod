package com.personal.friday.detox

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.personal.friday.FridayAccessibilityService
import com.personal.friday.FridayOverlayService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object FridayDetoxManager {

    private const val TAG = "FridayDetox"
    private const val PREFS_NAME = "friday_prefs"

    const val TARGET_PACKAGE = "com.instagram.android"
    const val MAX_USAGE_MILLIS = 10 * 60 * 1000L // 10 minutes maximum usage
    const val WARNING_USAGE_MILLIS = 9 * 60 * 1000L // 9 minutes warning whisper
    const val COOLDOWN_MILLIS = 5 * 60 * 60 * 1000L // 5 hours lockdown cooldown

    const val KEY_DETOX_ENABLED = "detox_enabled"
    private const val KEY_LAST_LOCKOUT_TIME = "detox_last_lockout_time"
    private const val KEY_LAST_LOCKOUT_DATE = "detox_last_lockout_date"
    private const val KEY_ACCUMULATED_TIME = "detox_accumulated_time"
    private const val KEY_LAST_ACTIVE_DATE = "detox_last_active_date"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isTracking = false
    private var sessionStartTime = 0L
    private var hasFired9MinWarning = false
    private var lastEnforcedKickoutSpeechTime = 0L

    private val trackingRunnable = object : Runnable {
        override fun run() {
            if (!isTracking) return

            val context = FridayOverlayService.instance ?: FridayAccessibilityService.instance
            if (context == null) {
                mainHandler.postDelayed(this, 1000L)
                return
            }

            // Verify Instagram is still the current active foreground app
            val currentPkg = FridayAccessibilityService.currentForegroundPackage ?: ""
            if (!isTargetApp(currentPkg)) {
                pauseTracking(context)
                return
            }

            val now = System.currentTimeMillis()
            val sessionElapsed = (now - sessionStartTime).coerceAtLeast(0L)
            val totalAccumulated = getAccumulatedTime(context) + sessionElapsed

            // 1. Check 9th minute warning
            if (totalAccumulated >= WARNING_USAGE_MILLIS && totalAccumulated < MAX_USAGE_MILLIS && !hasFired9MinWarning) {
                hasFired9MinWarning = true
                Log.i(TAG, "⚠️ Detox 9-minute warning reached for Instagram ($totalAccumulated ms)")
                FridayOverlayService.instance?.showWhisper("⚠️ Detox Warning: 1 minute remaining on Instagram, Sir.")
            }

            // 2. Check 10-minute limit
            if (totalAccumulated >= MAX_USAGE_MILLIS) {
                Log.i(TAG, "🚨 Detox 10-minute limit exceeded ($totalAccumulated ms)! Triggering lockout!")
                triggerDetoxLockout(context)
                return
            }

            // Continue heartbeat every 1 second
            mainHandler.postDelayed(this, 1000L)
        }
    }

    fun isTargetApp(pkg: String): Boolean {
        val lower = pkg.lowercase(Locale.ROOT).trim()
        return lower == TARGET_PACKAGE || lower.contains("com.instagram.android")
    }

    fun isDetoxEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DETOX_ENABLED, true)
    }

    fun setDetoxEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_DETOX_ENABLED, enabled)
            .apply()
        if (!enabled) {
            pauseTracking(context)
        }
    }

    fun onForegroundPackageChanged(service: AccessibilityService, newPkg: String) {
        val isInstagram = isTargetApp(newPkg)
        val isEnabled = isDetoxEnabled(service)

        if (!isEnabled) {
            if (isTracking) {
                pauseTracking(service)
            }
            return
        }

        if (isInstagram) {
            // 1. Check if 5-hour cooldown is currently active
            val remainingCooldown = getRemainingCooldownMillis(service)
            if (remainingCooldown > 0L) {
                enforceCooldownKickout(service, remainingCooldown)
                return
            }

            // 2. Cooldown is inactive. Check if today is a fresh calendar day
            checkAndResetDailyAccumulated(service)

            // 3. Start active 10-minute tracking
            startTracking(service)
        } else {
            // User switched away or closed Instagram
            if (isTracking) {
                pauseTracking(service)
            }
        }
    }

    fun onScreenOff(context: Context) {
        if (isTracking) {
            Log.d(TAG, "Screen turned OFF. Pausing Detox tracking.")
            pauseTracking(context)
        }
    }

    @Synchronized
    private fun startTracking(context: Context) {
        if (isTracking) return
        isTracking = true
        sessionStartTime = System.currentTimeMillis()
        val currentAcc = getAccumulatedTime(context)
        Log.i(TAG, "⏱️ Detox tracking started for Instagram. Current accumulated: ${currentAcc / 1000}s")

        mainHandler.removeCallbacks(trackingRunnable)
        mainHandler.postDelayed(trackingRunnable, 1000L)
    }

    @Synchronized
    private fun pauseTracking(context: Context) {
        if (!isTracking) return
        isTracking = false
        mainHandler.removeCallbacks(trackingRunnable)

        val sessionElapsed = (System.currentTimeMillis() - sessionStartTime).coerceAtLeast(0L)
        if (sessionElapsed > 0L) {
            val currentAcc = getAccumulatedTime(context)
            val newAcc = currentAcc + sessionElapsed
            saveAccumulatedTime(context, newAcc)
            Log.i(TAG, "⏸️ Detox tracking paused. Session: ${sessionElapsed / 1000}s, Total accumulated: ${newAcc / 1000}s")
        }
        sessionStartTime = 0L
    }

    private fun triggerDetoxLockout(context: Context) {
        isTracking = false
        mainHandler.removeCallbacks(trackingRunnable)

        // Kick user out of Instagram immediately to Home screen
        FridayAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

        val now = System.currentTimeMillis()
        val today = getTodayDateString()

        // Record lockout timestamp and date; reset session counter
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putLong(KEY_LAST_LOCKOUT_TIME, now)
            putString(KEY_LAST_LOCKOUT_DATE, today)
            putLong(KEY_ACCUMULATED_TIME, 0L)
            apply()
        }

        hasFired9MinWarning = false
        lastEnforcedKickoutSpeechTime = now

        val speech = "Sir, aapne Instagram bohot chala liya hai. 10 minute ka quota poora ho gaya hai. Focus wapas layein, Instagram band kar diya hai."
        val whisper = "🧘 Detox Active • 10m Instagram Quota Reached"

        FridayOverlayService.instance?.let { overlay ->
            overlay.ttsManager.speak(speech)
            overlay.showWhisper(whisper)
        }
    }

    private fun enforceCooldownKickout(service: AccessibilityService, remainingCooldown: Long) {
        // Kick user out immediately before Instagram renders
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        Log.i(TAG, "🛡️ Detox Enforced: Kicked user out of Instagram! Remaining cooldown: ${remainingCooldown}ms")

        // Debounce audio speech to avoid stuttering on rapid taps
        val now = System.currentTimeMillis()
        if (now - lastEnforcedKickoutSpeechTime < 4500L) {
            return
        }
        lastEnforcedKickoutSpeechTime = now

        val hours = TimeUnit.MILLISECONDS.toHours(remainingCooldown)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingCooldown) % 60

        val timeText = when {
            hours > 0L && minutes > 0L -> "$hours ghante $minutes minute"
            hours > 0L -> "$hours ghante"
            minutes > 0L -> "$minutes minute"
            else -> "kuch seconds"
        }

        val speech = "Sir, abhi work time hai. 5 ghante ka Detox cooldown chal raha hai. Abhi $timeText baki hain, focus mat bhatkaiye!"
        val whisper = "🧘 Detox Cooldown • ${if (hours > 0) "${hours}h " else ""}${minutes}m remaining"

        FridayOverlayService.instance?.let { overlay ->
            overlay.ttsManager.speak(speech)
            overlay.showWhisper(whisper)
        }
    }

    fun getRemainingCooldownMillis(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_DETOX_ENABLED, true)) return 0L

        val lastLockoutTime = prefs.getLong(KEY_LAST_LOCKOUT_TIME, 0L)
        if (lastLockoutTime == 0L) return 0L

        val elapsed = System.currentTimeMillis() - lastLockoutTime
        if (elapsed >= COOLDOWN_MILLIS) {
            return 0L
        }

        return (COOLDOWN_MILLIS - elapsed).coerceAtLeast(0L)
    }

    private fun checkAndResetDailyAccumulated(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastActiveDate = prefs.getString(KEY_LAST_ACTIVE_DATE, "")
        val today = getTodayDateString()

        if (lastActiveDate != today) {
            Log.d(TAG, "New day detected ($today vs $lastActiveDate). Resetting daily accumulated Detox time.")
            prefs.edit().apply {
                putString(KEY_LAST_ACTIVE_DATE, today)
                putLong(KEY_ACCUMULATED_TIME, 0L)
                apply()
            }
            hasFired9MinWarning = false
        }
    }

    private fun getAccumulatedTime(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_ACCUMULATED_TIME, 0L)
    }

    private fun saveAccumulatedTime(context: Context, timeMs: Long) {
        val today = getTodayDateString()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putLong(KEY_ACCUMULATED_TIME, timeMs)
            putString(KEY_LAST_ACTIVE_DATE, today)
            apply()
        }
    }

    fun resetDetoxState(context: Context) {
        isTracking = false
        mainHandler.removeCallbacks(trackingRunnable)
        hasFired9MinWarning = false
        sessionStartTime = 0L

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putLong(KEY_LAST_LOCKOUT_TIME, 0L)
            putString(KEY_LAST_LOCKOUT_DATE, "")
            putLong(KEY_ACCUMULATED_TIME, 0L)
            apply()
        }
        Log.i(TAG, "🔄 Detox state fully reset by Sir.")
    }

    fun getDetoxStatusSpeech(context: Context): Pair<String, String> {
        val cooldown = getRemainingCooldownMillis(context)
        if (cooldown > 0L) {
            val hours = TimeUnit.MILLISECONDS.toHours(cooldown)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(cooldown) % 60
            val timeText = when {
                hours > 0L && minutes > 0L -> "$hours ghante $minutes minute"
                hours > 0L -> "$hours ghante"
                minutes > 0L -> "$minutes minute"
                else -> "kuch seconds"
            }
            val tts = "Sir, Instagram par Detox cooldown active hai. Agle $timeText tak access locked hai."
            val whisper = "🧘 Detox Cooldown • ${hours}h ${minutes}m remaining"
            return Pair(tts, whisper)
        } else {
            val acc = getAccumulatedTime(context)
            val sessionElapsed = if (isTracking && sessionStartTime > 0L) (System.currentTimeMillis() - sessionStartTime).coerceAtLeast(0L) else 0L
            val total = acc + sessionElapsed
            val remainingUsage = (MAX_USAGE_MILLIS - total).coerceAtLeast(0L)
            val minutesLeft = TimeUnit.MILLISECONDS.toMinutes(remainingUsage)
            val secondsLeft = TimeUnit.MILLISECONDS.toSeconds(remainingUsage) % 60

            val tts = "Sir, Detox protocol active hai. Instagram par abhi $minutesLeft minute $secondsLeft seconds ka session bacha hai."
            val whisper = "🧘 Detox Ready • ${minutesLeft}m ${secondsLeft}s left"
            return Pair(tts, whisper)
        }
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}

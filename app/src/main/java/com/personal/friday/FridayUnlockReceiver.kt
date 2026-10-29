package com.personal.friday

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * High-reliability static broadcast receiver registered in AndroidManifest.
 * Survives process death, reboots, and deep sleep overnight.
 * 
 * Triggers morning greeting instantly on the first phone unlock of the day (5 AM - 11 AM)
 * and acts as a resilience watchdog to resurrect Friday if killed by aggressive OEM battery killers.
 */
class FridayUnlockReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "FridayUnlockReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return
        Log.i(TAG, "Watchdog triggered with action: $action")

        val prefs = context.getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)

        when (action) {
            Intent.ACTION_USER_PRESENT -> {
                Log.i(TAG, "Screen UNLOCKED by Sir. Checking morning greeting & service health...")

                val cal = Calendar.getInstance()
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val isMorning = hour in 5..11
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val lastGreetDate = prefs.getString("last_morning_greet_date", "")
                val morningEnabled = prefs.getBoolean("morning_greeting_enabled", true)
                val wasEnabled = prefs.getBoolean("friday_enabled", false)

                val shouldTriggerMorning = morningEnabled && isMorning && lastGreetDate != today

                if (FridayOverlayService.isRunning && FridayOverlayService.instance != null) {
                    Log.i(TAG, "FridayOverlayService is currently ONLINE.")
                    if (shouldTriggerMorning) {
                        Log.i(TAG, "Triggering morning greeting via active overlay service...")
                        FridayOverlayService.instance?.checkAndTriggerMorningGreeting(isManualTest = false)
                    }
                } else {
                    Log.w(TAG, "FridayOverlayService was OFFLINE/KILLED when Sir unlocked phone! wasEnabled=$wasEnabled, shouldTriggerMorning=$shouldTriggerMorning")

                    if (shouldTriggerMorning) {
                        prefs.edit().putString("last_morning_greet_date", today).apply()
                        val greetingText = when (hour) {
                            in 5..8 -> "Good morning, Sir! Early start today. All cybernetic systems are online and primed. Ready when you are."
                            else -> "Good morning, Sir! All systems initialized and online. Hope you had a great sleep. Ready for today's mission."
                        }

                        // Independent speech playback so Sir is greeted without fail even if overlay died
                        val appContext = context.applicationContext
                        val tts = TtsManager(appContext)
                        tts.onReadyListener = {
                            Handler(Looper.getMainLooper()).postDelayed({
                                tts.speak(greetingText)
                            }, 500)
                        }
                    }

                    // Resurrect Friday if it was supposed to be running
                    if (wasEnabled) {
                        Log.i(TAG, "Resurrecting FridayOverlayService after OEM background termination...")
                        try {
                            val startIntent = Intent(context, FridayOverlayService::class.java)
                            ContextCompat.startForegroundService(context, startIntent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to resurrect FridayOverlayService: ${e.message}", e)
                        }
                    }
                }
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val wasEnabled = prefs.getBoolean("friday_enabled", false)
                Log.i(TAG, "System event $action. wasEnabled=$wasEnabled")

                // Reschedule daily Bio-Fuel meal alarms
                com.personal.friday.meals.FridayMealScheduler.scheduleAllMeals(context)

                if (wasEnabled && !FridayOverlayService.isRunning) {
                    try {
                        val startIntent = Intent(context, FridayOverlayService::class.java)
                        ContextCompat.startForegroundService(context, startIntent)
                        Log.i(TAG, "Auto-started Friday after boot/update.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to auto-start after boot/update", e)
                    }
                }
            }
        }
    }
}

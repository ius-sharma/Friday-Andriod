package com.personal.friday.meals

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.personal.friday.FridayOverlayService
import com.personal.friday.MainActivity
import com.personal.friday.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FridayMealAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "FridayMealReceiver"
        private const val CHANNEL_ID = "friday_biofuel_channel"
        private const val NOTIFICATION_BASE_ID = 4000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val mealType = intent.getStringExtra(FridayMealScheduler.EXTRA_MEAL_TYPE) ?: MealDialogueGenerator.MEAL_LUNCH
        val isSnooze = intent.getBooleanExtra(FridayMealScheduler.EXTRA_IS_SNOOZE, false)
        Log.i(TAG, "onReceive: action=$action, mealType=$mealType, isSnooze=$isSnooze")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createMealNotificationChannel(notificationManager)

        val notifId = NOTIFICATION_BASE_ID + getMealIndex(mealType)

        when (action) {
            FridayMealScheduler.ACTION_MEAL_ALERT -> {
                // 1. Acquire short wake lock (4.5 seconds) so device processes audio/vitals reliably
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Friday:MealAlertWakeLock"
                )
                wakeLock?.acquire(4500L)

                // 2. Play Signature F.R.I.D.A.Y. Cyber Alert Chime
                playSignatureAlertTone()

                val prefs = context.getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                // 3. Generate dynamic voice line
                val dialogue = if (mealType.equals(MealDialogueGenerator.MEAL_BREAKFAST_CHECK, ignoreCase = true)) {
                    val lastBreakfastDate = prefs.getString("last_breakfast_confirmed_date", "")
                    if (lastBreakfastDate == today) {
                        "Sir, subah ke 8 baj gaye hain. Aapne nashta pehle hi kar liya tha, awesome! Ab full focus ke sath aaj ke targets achieve karte hain."
                    } else {
                        MealDialogueGenerator.getNextDialogue(context, mealType)
                    }
                } else {
                    MealDialogueGenerator.getNextDialogue(context, mealType)
                }

                val mealName = MealDialogueGenerator.getMealDisplayName(mealType)

                // 4. If Friday Overlay Service is active, display Whisper HUD & Speak
                val overlay = FridayOverlayService.instance
                if (overlay != null) {
                    overlay.triggerMealAlert(mealType, dialogue)
                }

                // 5. Build and post High-Priority System Notification
                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openAppPendingIntent = PendingIntent.getActivity(
                    context,
                    notifId,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val confirmIntent = Intent(context, FridayMealAlarmReceiver::class.java).apply {
                    this.action = FridayMealScheduler.ACTION_MEAL_CONFIRM
                    putExtra(FridayMealScheduler.EXTRA_MEAL_TYPE, mealType)
                }
                val confirmPendingIntent = PendingIntent.getBroadcast(
                    context,
                    notifId + 10,
                    confirmIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val snoozeIntent = Intent(context, FridayMealAlarmReceiver::class.java).apply {
                    this.action = FridayMealScheduler.ACTION_MEAL_SNOOZE
                    putExtra(FridayMealScheduler.EXTRA_MEAL_TYPE, mealType)
                }
                val snoozePendingIntent = PendingIntent.getBroadcast(
                    context,
                    notifId + 20,
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_friday_bubble)
                    .setContentTitle("F.R.I.D.A.Y. • $mealName")
                    .setContentText(dialogue)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(dialogue))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .setContentIntent(openAppPendingIntent)
                    .addAction(R.drawable.ic_friday_bubble, "✅ KHA LIYA", confirmPendingIntent)
                    .addAction(R.drawable.ic_friday_bubble, "⏳ 15M SNOOZE", snoozePendingIntent)
                    .build()

                notificationManager.notify(notifId, notification)

                // 6. If this was a regular meal alert (not a snooze), schedule it for tomorrow
                if (!isSnooze) {
                    val timePrefKey = when (mealType.uppercase()) {
                        MealDialogueGenerator.MEAL_BREAKFAST -> FridayMealScheduler.PREF_BREAKFAST_TIME
                        MealDialogueGenerator.MEAL_BREAKFAST_CHECK -> FridayMealScheduler.PREF_BREAKFAST_CHECK_TIME
                        MealDialogueGenerator.MEAL_LUNCH -> FridayMealScheduler.PREF_LUNCH_TIME
                        MealDialogueGenerator.MEAL_SNACKS -> FridayMealScheduler.PREF_SNACKS_TIME
                        MealDialogueGenerator.MEAL_DINNER -> FridayMealScheduler.PREF_DINNER_TIME
                        else -> FridayMealScheduler.PREF_LUNCH_TIME
                    }
                    val defaultTime = when (mealType.uppercase()) {
                        MealDialogueGenerator.MEAL_BREAKFAST -> FridayMealScheduler.DEFAULT_BREAKFAST
                        MealDialogueGenerator.MEAL_BREAKFAST_CHECK -> FridayMealScheduler.DEFAULT_BREAKFAST_CHECK
                        MealDialogueGenerator.MEAL_LUNCH -> FridayMealScheduler.DEFAULT_LUNCH
                        MealDialogueGenerator.MEAL_SNACKS -> FridayMealScheduler.DEFAULT_SNACKS
                        MealDialogueGenerator.MEAL_DINNER -> FridayMealScheduler.DEFAULT_DINNER
                        else -> FridayMealScheduler.DEFAULT_LUNCH
                    }
                    val scheduledTime = prefs.getString(timePrefKey, defaultTime) ?: defaultTime
                    FridayMealScheduler.scheduleSingleMeal(context, mealType, scheduledTime)
                }
            }

            FridayMealScheduler.ACTION_MEAL_CONFIRM -> {
                notificationManager.cancel(notifId)
                val prefs = context.getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                if (mealType.equals(MealDialogueGenerator.MEAL_BREAKFAST, ignoreCase = true) ||
                    mealType.equals(MealDialogueGenerator.MEAL_BREAKFAST_CHECK, ignoreCase = true)) {
                    prefs.edit().putString("last_breakfast_confirmed_date", today).apply()
                }

                FridayOverlayService.instance?.let { overlay ->
                    val confirmMsg = "Great Sir! Core fuel replenished. Back to peak focus."
                    overlay.showWhisper(confirmMsg, 4000L)
                    overlay.ttsManager.speak(confirmMsg)
                }
            }

            FridayMealScheduler.ACTION_MEAL_SNOOZE -> {
                notificationManager.cancel(notifId)
                FridayMealScheduler.snoozeMeal(context, mealType, 15)
                FridayOverlayService.instance?.let { overlay ->
                    val snoozeMsg = "Sir, 15 minute ka snooze set kar diya hai."
                    overlay.showWhisper(snoozeMsg, 3500L)
                    overlay.ttsManager.speak(snoozeMsg)
                }
            }
        }
    }

    private fun playSignatureAlertTone() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 280)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator alert failed", e)
        }
    }

    private fun createMealNotificationChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "F.R.I.D.A.Y. Bio-Fuel Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority meal alerts and nutrition notifications for Sir"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 300)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun getMealIndex(mealType: String): Int {
        return when (mealType.uppercase()) {
            MealDialogueGenerator.MEAL_BREAKFAST -> 1
            MealDialogueGenerator.MEAL_BREAKFAST_CHECK -> 2
            MealDialogueGenerator.MEAL_LUNCH -> 3
            MealDialogueGenerator.MEAL_SNACKS -> 4
            MealDialogueGenerator.MEAL_DINNER -> 5
            else -> 0
        }
    }
}

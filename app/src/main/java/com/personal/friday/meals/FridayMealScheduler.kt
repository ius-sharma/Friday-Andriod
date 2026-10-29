package com.personal.friday.meals

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object FridayMealScheduler {

    private const val TAG = "FridayMealScheduler"

    const val ACTION_MEAL_ALERT = "com.personal.friday.ACTION_MEAL_ALERT"
    const val ACTION_MEAL_SNOOZE = "com.personal.friday.ACTION_MEAL_SNOOZE"
    const val ACTION_MEAL_CONFIRM = "com.personal.friday.ACTION_MEAL_CONFIRM"

    const val EXTRA_MEAL_TYPE = "extra_meal_type"
    const val EXTRA_IS_SNOOZE = "extra_is_snooze"

    const val PREF_MEAL_ENABLED = "meal_alerts_enabled"
    const val PREF_BREAKFAST_TIME = "meal_breakfast_time"
    const val PREF_LUNCH_TIME = "meal_lunch_time"
    const val PREF_SNACKS_TIME = "meal_snacks_time"
    const val PREF_DINNER_TIME = "meal_dinner_time"

    const val DEFAULT_BREAKFAST = "09:00"
    const val DEFAULT_LUNCH = "13:30"
    const val DEFAULT_SNACKS = "17:30"
    const val DEFAULT_DINNER = "21:00"

    private fun getRequestCode(mealType: String): Int {
        return when (mealType.uppercase()) {
            MealDialogueGenerator.MEAL_BREAKFAST -> 1001
            MealDialogueGenerator.MEAL_LUNCH -> 1002
            MealDialogueGenerator.MEAL_SNACKS -> 1003
            MealDialogueGenerator.MEAL_DINNER -> 1004
            else -> 1000
        }
    }

    fun isMealAlertsEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_MEAL_ENABLED, true)
    }

    fun scheduleAllMeals(context: Context) {
        if (!isMealAlertsEnabled(context)) {
            Log.i(TAG, "Meal alerts disabled in preferences. Skipping scheduling.")
            return
        }

        val prefs = context.getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
        val breakfast = prefs.getString(PREF_BREAKFAST_TIME, DEFAULT_BREAKFAST) ?: DEFAULT_BREAKFAST
        val lunch = prefs.getString(PREF_LUNCH_TIME, DEFAULT_LUNCH) ?: DEFAULT_LUNCH
        val snacks = prefs.getString(PREF_SNACKS_TIME, DEFAULT_SNACKS) ?: DEFAULT_SNACKS
        val dinner = prefs.getString(PREF_DINNER_TIME, DEFAULT_DINNER) ?: DEFAULT_DINNER

        scheduleSingleMeal(context, MealDialogueGenerator.MEAL_BREAKFAST, breakfast)
        scheduleSingleMeal(context, MealDialogueGenerator.MEAL_LUNCH, lunch)
        scheduleSingleMeal(context, MealDialogueGenerator.MEAL_SNACKS, snacks)
        scheduleSingleMeal(context, MealDialogueGenerator.MEAL_DINNER, dinner)
        Log.i(TAG, "All 4 daily Bio-Fuel meal alerts scheduled successfully.")
    }

    fun scheduleSingleMeal(context: Context, mealType: String, timeString: String) {
        try {
            val parts = timeString.split(":")
            if (parts.size < 2) return
            val hour = parts[0].toIntOrNull() ?: return
            val minute = parts[1].toIntOrNull() ?: return

            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If target time for today has already elapsed, schedule for tomorrow
            if (target.before(now) || target.timeInMillis <= now.timeInMillis + 10_000L) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, FridayMealAlarmReceiver::class.java).apply {
                action = ACTION_MEAL_ALERT
                putExtra(EXTRA_MEAL_TYPE, mealType)
                putExtra(EXTRA_IS_SNOOZE, false)
            }

            val requestCode = getRequestCode(mealType)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    target.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    target.timeInMillis,
                    pendingIntent
                )
            }

            Log.i(TAG, "Scheduled $mealType alert for ${target.time} (RequestCode: $requestCode)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule meal alert for $mealType", e)
        }
    }

    fun snoozeMeal(context: Context, mealType: String, snoozeMinutes: Int = 15) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val triggerTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)

            val intent = Intent(context, FridayMealAlarmReceiver::class.java).apply {
                action = ACTION_MEAL_ALERT
                putExtra(EXTRA_MEAL_TYPE, mealType)
                putExtra(EXTRA_IS_SNOOZE, true)
            }

            val requestCode = getRequestCode(mealType)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }

            Log.i(TAG, "Snoozed $mealType alert for $snoozeMinutes minutes.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to snooze meal alert for $mealType", e)
        }
    }

    fun cancelAllMeals(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val list = listOf(
            MealDialogueGenerator.MEAL_BREAKFAST,
            MealDialogueGenerator.MEAL_LUNCH,
            MealDialogueGenerator.MEAL_SNACKS,
            MealDialogueGenerator.MEAL_DINNER
        )

        for (meal in list) {
            val intent = Intent(context, FridayMealAlarmReceiver::class.java).apply {
                action = ACTION_MEAL_ALERT
                putExtra(EXTRA_MEAL_TYPE, meal)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                getRequestCode(meal),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
        }
        Log.i(TAG, "Cancelled all scheduled meal alerts.")
    }
}

package com.personal.friday.widget

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.personal.friday.MainActivity
import com.personal.friday.R
import com.personal.friday.FridayOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class FridayHudWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        updateAllWidgets(context)

        // Trigger immediate background refresh if weather is stale (older than 20 mins)
        val cached = FridayWeatherRepository.getCachedWeather(context)
        if (System.currentTimeMillis() - cached.lastUpdatedMillis > 20 * 60 * 1000L) {
            triggerAsyncWeatherRefresh(context)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.i(TAG, "FridayHudWidget enabled. Scheduling WorkManager periodic sync...")
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<FridayWidgetSyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error scheduling WorkManager", e)
        }

        // Fetch fresh weather immediately upon adding widget
        triggerAsyncWeatherRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.i(TAG, "FridayHudWidget disabled. Cancelling periodic sync...")
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling WorkManager", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive action: ${intent.action}")

        when (intent.action) {
            ACTION_REFRESH_WEATHER -> {
                triggerAsyncWeatherRefresh(context)
            }
            ACTION_TRIGGER_VOICE -> {
                if (FridayOverlayService.isRunning) {
                    val serviceIntent = Intent(context, FridayOverlayService::class.java).apply {
                        action = FridayOverlayService.ACTION_TRIGGER_LISTENING
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } else {
                    val appIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(appIntent)
                }
            }
        }
    }

    companion object {
        private const val TAG = "FridayHudWidget"
        const val ACTION_REFRESH_WEATHER = "com.personal.friday.widget.ACTION_REFRESH_WEATHER"
        const val ACTION_TRIGGER_VOICE = "com.personal.friday.widget.ACTION_TRIGGER_VOICE"
        private const val WORK_NAME = "FridayWidgetSyncWorker"

        private val providerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun triggerAsyncWeatherRefresh(context: Context) {
            providerScope.launch {
                try {
                    FridayWeatherRepository.fetchCurrentWeather(context.applicationContext)
                    updateAllWidgets(context.applicationContext)
                } catch (e: Exception) {
                    Log.w(TAG, "Async weather fetch error", e)
                }
            }
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, FridayHudWidgetProvider::class.java)
            )
            if (widgetIds.isEmpty()) return

            val weather = FridayWeatherRepository.getCachedWeather(context)
            val (batteryText, batteryPct) = getBatteryTelemetry(context)
            val (ramText, ramPct) = getRamTelemetry(context)
            val isCoreActive = FridayOverlayService.isRunning

            val chartBitmap = FridayWaveChartDrawer.drawWaveChart(
                hourlyList = weather.hourlyForecast,
                width = 540,
                height = 230
            )

            for (widgetId in widgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_friday_hud)

                // 1. LEFT SECTION (Image 1 Style): Weather Icon, Temp, Condition (Time/Date handled natively by TextClock)
                views.setTextViewText(R.id.tv_widget_weather_icon, weather.iconEmoji)
                views.setTextViewText(R.id.tv_widget_temp, "${weather.tempCelsius.toInt()}°")
                views.setTextViewText(R.id.tv_widget_condition_city, "${weather.conditionText} • ${weather.cityName}")

                if (isCoreActive) {
                    views.setImageViewResource(R.id.dot_widget_core, R.drawable.bg_dot_active)
                } else {
                    views.setImageViewResource(R.id.dot_widget_core, R.drawable.bg_dot_inactive)
                }

                // 2. RIGHT CARD (Image 2 Style): Dynamic Live Smooth Wave Chart
                views.setImageViewBitmap(R.id.iv_widget_wave_chart, chartBitmap)

                // 3. BOTTOM TELEMETRY: Battery & RAM Progress Bars
                views.setTextViewText(R.id.tv_widget_battery, batteryText)
                views.setProgressBar(R.id.progress_widget_battery, 100, batteryPct, false)

                views.setTextViewText(R.id.tv_widget_ram, ramText)
                views.setProgressBar(R.id.progress_widget_ram, 100, ramPct, false)

                // 4. CLICK PENDING INTENTS
                // A: Refresh Weather (Tap on Refresh Icon or Right Chart Card)
                val refreshIntent = Intent(context, FridayHudWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_WEATHER
                }
                val refreshPending = PendingIntent.getBroadcast(
                    context,
                    101,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.btn_widget_refresh, refreshPending)
                views.setOnClickPendingIntent(R.id.layout_chart_pod, refreshPending)

                // B: Voice Action Button (Direct Friday Voice Listening)
                val voiceIntent = Intent(context, FridayHudWidgetProvider::class.java).apply {
                    action = ACTION_TRIGGER_VOICE
                }
                val voicePending = PendingIntent.getBroadcast(
                    context,
                    102,
                    voiceIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.btn_widget_voice, voicePending)

                // C: Open App (Tap on Left Weather Pod or Bottom Telemetry)
                val appIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val appPending = PendingIntent.getActivity(
                    context,
                    103,
                    appIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.layout_weather_pod, appPending)
                views.setOnClickPendingIntent(R.id.layout_telemetry_click, appPending)

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        private fun getBatteryTelemetry(context: Context): Pair<String, Int> {
            try {
                val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryStatus: Intent? = context.registerReceiver(null, batteryFilter)
                if (batteryStatus != null) {
                    val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
                    return Pair("⚡ $batteryPct%", batteryPct)
                }
            } catch (_: Exception) {}
            return Pair("⚡ 100%", 100)
        }

        private fun getRamTelemetry(context: Context): Pair<String, Int> {
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                if (am != null) {
                    am.getMemoryInfo(memInfo)
                    val totalGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
                    val availGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
                    val usedGb = totalGb - availGb
                    val usedPct = if (totalGb > 0) ((usedGb / totalGb) * 100).toInt() else 50
                    val text = String.format("💾 %.1f/%.1f GB", usedGb, totalGb)
                    return Pair(text, usedPct)
                }
            } catch (_: Exception) {}
            return Pair("💾 RAM: Ready", 50)
        }
    }
}

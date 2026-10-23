package com.personal.friday.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class FridayWidgetSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("FridayWidgetWorker", "Executing scheduled background weather & telemetry sync...")
        return try {
            FridayWeatherRepository.fetchCurrentWeather(applicationContext)
            FridayHudWidgetProvider.updateAllWidgets(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.w("FridayWidgetWorker", "Sync worker exception: ${e.message}")
            Result.retry()
        }
    }
}

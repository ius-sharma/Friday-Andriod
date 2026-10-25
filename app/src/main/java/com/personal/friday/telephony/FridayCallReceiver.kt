package com.personal.friday.telephony

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CallLog
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

class FridayCallReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "FridayCallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        Log.i(TAG, "PHONE_STATE broadcast: state=$stateStr, incomingNumber=$incomingNumber")

        val numberToUse = if (!incomingNumber.isNullOrBlank()) {
            incomingNumber
        } else {
            fetchLatestCallLogNumber(context)
        }

        if (!numberToUse.isNullOrBlank()) {
            FridayTelephonyManager.updateIncomingCaller(context, numberToUse)
        }
    }

    private fun fetchLatestCallLogNumber(context: Context): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val cursor: Cursor? = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying CallLog for recent number", e)
            null
        }
    }
}

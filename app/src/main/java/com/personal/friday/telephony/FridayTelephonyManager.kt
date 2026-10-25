package com.personal.friday.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat

object FridayTelephonyManager {
    private const val TAG = "FridayTelephonyManager"

    // Telephony Call States
    const val STATE_IDLE = 0
    const val STATE_RINGING = 1
    const val STATE_OFFHOOK = 2

    private var telephonyManager: TelephonyManager? = null
    private var legacyListener: PhoneStateListener? = null
    private var modernCallback: Any? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var currentCallState: Int = STATE_IDLE
        private set

    @Volatile
    var lastIncomingNumber: String = ""
        private set

    @Volatile
    var currentCallerName: String = "Incoming Caller"
        private set

    @Volatile
    var isProxyActive: Boolean = false
        private set

    private var onCallStateChangedListener: ((state: Int, number: String, contactName: String) -> Unit)? = null

    fun setCallStateListener(listener: (state: Int, number: String, contactName: String) -> Unit) {
        onCallStateChangedListener = listener
    }

    fun updateIncomingCaller(context: Context, number: String, explicitName: String? = null) {
        lastIncomingNumber = number
        val name = if (!explicitName.isNullOrBlank()) {
            explicitName
        } else {
            resolveContactName(context, number) ?: number
        }
        currentCallerName = name
        Log.i(TAG, "updateIncomingCaller: number=$number, name=$currentCallerName, state=$currentCallState")
        if (currentCallState == STATE_RINGING) {
            mainHandler.post {
                onCallStateChangedListener?.invoke(STATE_RINGING, lastIncomingNumber, currentCallerName)
            }
        }
    }

    /**
     * Registers system phone state listener for incoming calls and disconnections
     */
    @SuppressLint("NewApi")
    fun registerListener(context: Context) {
        try {
            telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephonyManager == null) {
                Log.w(TAG, "TelephonyManager not available on this device")
                return
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "READ_PHONE_STATE permission not granted")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallStateChange(context, state, null)
                    }
                }
                modernCallback = callback
                telephonyManager?.registerTelephonyCallback(context.mainExecutor, callback)
                Log.i(TAG, "TelephonyCallback registered successfully (Android 12+)")
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallStateChange(context, state, phoneNumber)
                    }
                }
                legacyListener = listener
                @Suppress("DEPRECATION")
                telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                Log.i(TAG, "PhoneStateListener registered successfully (Legacy Android)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register telephony listener", e)
        }
    }

    private fun handleCallStateChange(context: Context, state: Int, phoneNumber: String?) {
        val newState = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> STATE_RINGING
            TelephonyManager.CALL_STATE_OFFHOOK -> STATE_OFFHOOK
            else -> STATE_IDLE
        }

        if (phoneNumber != null && phoneNumber.isNotBlank()) {
            updateIncomingCaller(context, phoneNumber)
        } else if (newState == STATE_RINGING && lastIncomingNumber.isBlank()) {
            val logNumber = fetchLatestCallLogNumber(context)
            if (!logNumber.isNullOrBlank()) {
                updateIncomingCaller(context, logNumber)
            }
        }

        val prev = currentCallState
        currentCallState = newState

        Log.i(TAG, "Call state transition: $prev -> $newState (number: $lastIncomingNumber, caller: $currentCallerName)")

        val finalCallerName = if (currentCallerName.isNotBlank() && currentCallerName != "Incoming Caller") {
            currentCallerName
        } else if (lastIncomingNumber.isNotBlank()) {
            resolveContactName(context, lastIncomingNumber) ?: lastIncomingNumber
        } else {
            "Incoming Caller"
        }
        currentCallerName = finalCallerName

        mainHandler.post {
            onCallStateChangedListener?.invoke(newState, lastIncomingNumber, finalCallerName)
        }

        if (newState == STATE_IDLE) {
            isProxyActive = false
            mainHandler.postDelayed({
                if (currentCallState == STATE_IDLE) {
                    lastIncomingNumber = ""
                    currentCallerName = "Incoming Caller"
                }
            }, 3000)
        }
    }

    fun fetchLatestCallLogNumber(context: Context): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val cursor: android.database.Cursor? = context.contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                arrayOf(android.provider.CallLog.Calls.NUMBER, android.provider.CallLog.Calls.TYPE, android.provider.CallLog.Calls.DATE),
                null,
                null,
                "${android.provider.CallLog.Calls.DATE} DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    it.getString(it.getColumnIndexOrThrow(android.provider.CallLog.Calls.NUMBER))
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying CallLog for recent number", e)
            null
        }
    }

    /**
     * Resolves contact name from phone number via ContactsContract
     */
    fun resolveContactName(context: Context, phoneNumber: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error looking up contact name for $phoneNumber", e)
            null
        }
    }

    /**
     * Resolves phone number given a contact name query
     */
    fun findContactNumber(context: Context, nameQuery: String): Pair<String, String>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val cleanName = nameQuery.trim()
        return try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$cleanName%")

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                    val number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    Pair(name, number)
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error searching contact for $nameQuery", e)
            null
        }
    }

    /**
     * Dials out to a contact name or phone number
     */
    fun makeCall(context: Context, target: String): Pair<Boolean, String> {
        val cleanTarget = target.trim()
        val digits = cleanTarget.replace(Regex("[^0-9+]"), "")
        val isExplicitNumber = digits.length >= 3 && cleanTarget.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }

        val (displayName, phoneNumber) = if (isExplicitNumber) {
            Pair(cleanTarget, digits)
        } else {
            val contact = findContactNumber(context, cleanTarget)
            if (contact != null) {
                contact
            } else {
                Pair(cleanTarget, digits.ifEmpty { cleanTarget })
            }
        }

        if (phoneNumber.isBlank() || !phoneNumber.any { it.isDigit() }) {
            return Pair(false, "Sir, contact '$cleanTarget' ka number nahi mila.")
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val intent = if (hasCallPermission) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        return try {
            context.startActivity(intent)
            Pair(true, "Calling $displayName, Sir.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call", e)
            Pair(false, "Sir, call initiate karne me error aya: ${e.message}")
        }
    }

    /**
     * Answers ringing call via TelecomManager
     */
    fun acceptCall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                try {
                    telecomManager?.acceptRingingCall()
                    Log.i(TAG, "Ringing call accepted via TelecomManager.acceptRingingCall()")
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Error calling acceptRingingCall()", e)
                }
            }
        }

        // Fallback: Dispatch Headset Hook media key
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK)
            val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK)
            audioManager?.dispatchMediaKeyEvent(down)
            audioManager?.dispatchMediaKeyEvent(up)
            Log.i(TAG, "Ringing call accepted via dispatchMediaKeyEvent(KEYCODE_HEADSETHOOK)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fallback call accept failed", e)
            false
        }
    }

    /**
     * Ends the current call
     */
    fun endCall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                try {
                    telecomManager?.endCall()
                    Log.i(TAG, "Call ended via TelecomManager.endCall()")
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Error calling endCall()", e)
                }
            }
        }
        return false
    }

    /**
     * Controls Speakerphone for In-Call AI Audio Bridging
     */
    fun setSpeakerphoneEnabled(context: Context, enabled: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (enabled) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val devices = audioManager.availableCommunicationDevices
                    val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) {
                        val res = audioManager.setCommunicationDevice(speaker)
                        Log.i(TAG, "setCommunicationDevice(BUILTIN_SPEAKER) result: $res")
                    } else {
                        Log.w(TAG, "No BUILTIN_SPEAKER in availableCommunicationDevices")
                    }
                }
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
                Log.i(TAG, "Speakerphone ENGAGED (isSpeakerphoneOn=${audioManager.isSpeakerphoneOn})")

                // Boost Music/Media volume so Friday's vocal response blasts cleanly into the mic
                try {
                    val maxMedia = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxMedia * 0.95).toInt(), 0)
                    val maxCall = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxCall, 0)
                } catch (_: Exception) {}
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                }
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_NORMAL
                Log.i(TAG, "Speakerphone DISENGAGED, restored to NORMAL audio mode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring speakerphone mode: $enabled", e)
        }
    }

    /**
     * Brings the Phone app / InCall screen to front so in-call accessibility controls are interactable
     */
    fun bringInCallUiToFront(context: Context) {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val dialerPkg = telecomManager?.defaultDialerPackage ?: "com.android.dialer"
            val launchIntent = context.packageManager.getLaunchIntentForPackage(dialerPkg)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                Log.i(TAG, "Brought in-call dialer to front: $dialerPkg")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error bringing in-call UI to front", e)
        }
    }

    fun setProxyActiveState(active: Boolean) {
        isProxyActive = active
    }

    fun unregisterListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                modernCallback?.let {
                    telephonyManager?.unregisterTelephonyCallback(it as TelephonyCallback)
                }
            } else {
                legacyListener?.let {
                    @Suppress("DEPRECATION")
                    telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
                }
            }
            modernCallback = null
            legacyListener = null
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering telephony listener", e)
        }
    }
}

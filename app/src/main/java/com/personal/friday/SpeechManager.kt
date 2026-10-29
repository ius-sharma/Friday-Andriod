package com.personal.friday

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class SpeechManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var isUserWantingToListen = false
    private var retryCount = 0

    // Watchdog to prevent indefinite hang on "Initializing mic..." if Phone Management / OEM Call Recorder locks the audio hardware
    private val micInitTimeoutRunnable = Runnable {
        if (isUserWantingToListen) {
            Log.w("SpeechManager", "Mic initialization timed out after 4500ms. Hardware mic likely locked by OEM Phone Management.")
            isUserWantingToListen = false
            onListeningStateChanged?.invoke(false)
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
            if (com.personal.friday.telephony.FridayTelephonyManager.isProxyActive) {
                onError("Sir, mic lock hai. Phone Dialer se 'Auto Call Recording' off kijiye.")
            } else {
                onError("Sir, mic audio stream busy hai. Ek baar retry kijiye.")
            }
        }
    }

    var onListeningStateChanged: ((Boolean) -> Unit)? = null
    var onCallerBargeIn: (() -> Unit)? = null

    fun isListening(): Boolean = isUserWantingToListen

    init {
        mainHandler.post {
            initRecognizer()
        }
    }

    private fun initRecognizer(forceStandard: Boolean = false) {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null

            // Prioritize on-device speech recognition on Android 12+ (API 31+) for fast, conflict-free listening
            if (!forceStandard && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                try {
                    speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    Log.i("SpeechManager", "Using ON-DEVICE SpeechRecognizer for ultra-low latency & in-call stability")
                } catch (e: Exception) {
                    Log.w("SpeechManager", "createOnDeviceSpeechRecognizer failed, falling back to standard", e)
                }
            }

            if (speechRecognizer == null) {
                val defaultService = Settings.Secure.getString(
                    context.contentResolver,
                    "voice_recognition_service"
                )
                val component = if (!defaultService.isNullOrBlank()) {
                    ComponentName.unflattenFromString(defaultService)
                } else null

                speechRecognizer = if (component != null) {
                    try {
                        SpeechRecognizer.createSpeechRecognizer(context, component)
                    } catch (e: Exception) {
                        SpeechRecognizer.createSpeechRecognizer(context)
                    }
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    mainHandler.removeCallbacks(micInitTimeoutRunnable)
                    if (!isUserWantingToListen) return
                    Log.d("SpeechManager", "onReadyForSpeech: Mic is hot and listening")
                    retryCount = 0
                    if (com.personal.friday.telephony.FridayTelephonyManager.isProxyActive) {
                        onStatus("Listening to caller...")
                    } else {
                        onStatus("Listening to Sir… (Boliye Sir)")
                    }
                }

                override fun onBeginningOfSpeech() {
                    if (!isUserWantingToListen) return
                    Log.d("SpeechManager", "onBeginningOfSpeech: Speech detected")
                    onCallerBargeIn?.invoke()
                    if (com.personal.friday.telephony.FridayTelephonyManager.isProxyActive) {
                        onStatus("Caller speaking...")
                    } else {
                        onStatus("Voice receiving, Sir...")
                    }
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (!isUserWantingToListen) return
                    Log.d("SpeechManager", "onEndOfSpeech")
                    onStatus("Processing voice...")
                }

                override fun onError(error: Int) {
                    mainHandler.removeCallbacks(micInitTimeoutRunnable)
                    Log.e("SpeechManager", "onError code: $error, isUserWantingToListen=$isUserWantingToListen, isProxy=${com.personal.friday.telephony.FridayTelephonyManager.isProxyActive}")
                    // If user closed or cancelled the mic, ignore all errors and NEVER restart
                    if (!isUserWantingToListen) {
                        return
                    }

                    // In Call Proxy Mode: silence/timeout (code 6 or 7) means caller hasn't answered yet or is thinking
                    if (com.personal.friday.telephony.FridayTelephonyManager.isProxyActive &&
                        (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                        Log.d("SpeechManager", "Caller silence/pause in proxy mode. Continuing listen loop...")
                        mainHandler.postDelayed({
                            if (isUserWantingToListen && com.personal.friday.telephony.FridayTelephonyManager.isProxyActive) {
                                doStartListening()
                            }
                        }, 400)
                        return
                    }

                    // If recognition service or audio hardware is busy (error 8 or error 3):
                    if (error == 8 || error == SpeechRecognizer.ERROR_AUDIO) {
                        Log.w("SpeechManager", "Mic or Speech Engine busy (error $error)")
                        isUserWantingToListen = false
                        onListeningStateChanged?.invoke(false)
                        onError("Sir, phone mic is currently busy.")
                        return
                    }

                    // If error is ERROR_CLIENT (code 5, binding race on first tap), auto-retry ONCE
                    if (error == SpeechRecognizer.ERROR_CLIENT && retryCount < 2) {
                        retryCount++
                        Log.d("SpeechManager", "Binding in progress, retrying startListening once in 350ms (retry=$retryCount)")
                        onStatus("Connecting voice engine...")
                        mainHandler.postDelayed({
                            if (isUserWantingToListen) {
                                initRecognizer()
                                doStartListening()
                            }
                        }, 350)
                        return
                    }

                    isUserWantingToListen = false
                    onListeningStateChanged?.invoke(false)

                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Kuch samajh nahi aaya Sir. Ek baar dubara bolein."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Time out ho gaya Sir."
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording issue aya Sir."
                        SpeechRecognizer.ERROR_CLIENT -> "Mic ready ho raha hai, dubara tap karein Sir."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission needed Sir."
                        SpeechRecognizer.ERROR_NETWORK -> "Network issue audio me aya Sir."
                        else -> "Mic code $error: Please retry Sir."
                    }
                    onError(msg)
                }

                override fun onResults(results: Bundle?) {
                    mainHandler.removeCallbacks(micInitTimeoutRunnable)
                    if (!isUserWantingToListen) return
                    isUserWantingToListen = false
                    onListeningStateChanged?.invoke(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty() && matches[0].isNotBlank()) {
                        Log.d("SpeechManager", "Recognized voice: ${matches[0]}")
                        onResult(matches[0])
                    } else {
                        onError("Sir, audio clear nahi tha.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!isUserWantingToListen) return
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onStatus("Sir: \"${matches[0]}...\"")
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            Log.d("SpeechManager", "SpeechRecognizer successfully initialized & bound")
        } catch (e: Exception) {
            Log.e("SpeechManager", "Failed to init SpeechRecognizer", e)
        }
    }

    private fun buildRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN", "en-US", "hi"))
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        }
    }

    fun startListening() {
        mainHandler.post {
            isUserWantingToListen = true
            retryCount = 0
            mainHandler.removeCallbacksAndMessages(null)
            if (speechRecognizer == null) {
                initRecognizer()
                mainHandler.postDelayed({
                    if (isUserWantingToListen) {
                        doStartListening()
                    }
                }, 300)
            } else {
                doStartListening()
            }
        }
    }

    private fun doStartListening() {
        if (!isUserWantingToListen) return
        try {
            mainHandler.removeCallbacks(micInitTimeoutRunnable)
            mainHandler.postDelayed(micInitTimeoutRunnable, 4500L)
            val intent = buildRecognizerIntent()
            speechRecognizer?.startListening(intent)
            onStatus("Initializing mic...")
            onListeningStateChanged?.invoke(true)
        } catch (e: Exception) {
            mainHandler.removeCallbacks(micInitTimeoutRunnable)
            Log.e("SpeechManager", "Error in doStartListening", e)
            initRecognizer()
            mainHandler.postDelayed({
                if (isUserWantingToListen) {
                    try {
                        speechRecognizer?.startListening(buildRecognizerIntent())
                    } catch (ex: Exception) {
                        onError("Mic start failed: ${ex.message}")
                    }
                }
            }, 350)
        }
    }

    fun stopListening() {
        mainHandler.post {
            mainHandler.removeCallbacks(micInitTimeoutRunnable)
            isUserWantingToListen = false
            mainHandler.removeCallbacksAndMessages(null)
            onListeningStateChanged?.invoke(false)
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w("SpeechManager", "Error in stopListening", e)
            }
        }
    }

    fun release() {
        mainHandler.post {
            mainHandler.removeCallbacks(micInitTimeoutRunnable)
            isUserWantingToListen = false
            mainHandler.removeCallbacksAndMessages(null)
            onListeningStateChanged?.invoke(false)
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.w("SpeechManager", "Error in release", e)
            }
        }
    }
}

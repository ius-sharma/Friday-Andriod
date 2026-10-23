package com.personal.friday

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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

    var onListeningStateChanged: ((Boolean) -> Unit)? = null

    fun isListening(): Boolean = isUserWantingToListen

    init {
        mainHandler.post {
            initRecognizer()
        }
    }

    private fun initRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null

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

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!isUserWantingToListen) return
                    Log.d("SpeechManager", "onReadyForSpeech: Mic is hot and listening")
                    retryCount = 0
                    onStatus("Listening to Boss… (Boliye Boss)")
                }

                override fun onBeginningOfSpeech() {
                    if (!isUserWantingToListen) return
                    Log.d("SpeechManager", "onBeginningOfSpeech: Boss started speaking")
                    onStatus("Voice receiving, Boss...")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (!isUserWantingToListen) return
                    Log.d("SpeechManager", "onEndOfSpeech")
                    onStatus("Processing voice command...")
                }

                override fun onError(error: Int) {
                    Log.e("SpeechManager", "onError code: $error, isUserWantingToListen=$isUserWantingToListen")
                    // If user closed or cancelled the mic, ignore all errors and NEVER restart
                    if (!isUserWantingToListen) {
                        return
                    }

                    // If error is ERROR_CLIENT (code 5, binding race on first tap), auto-retry ONCE
                    if (error == SpeechRecognizer.ERROR_CLIENT && retryCount < 1) {
                        retryCount++
                        Log.d("SpeechManager", "Binding in progress, retrying startListening once in 300ms")
                        onStatus("Connecting voice engine...")
                        mainHandler.postDelayed({
                            if (isUserWantingToListen) {
                                doStartListening()
                            }
                        }, 300)
                        return
                    }

                    isUserWantingToListen = false
                    onListeningStateChanged?.invoke(false)

                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Kuch samajh nahi aaya Boss. Ek baar dubara bolein."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Time out ho gaya Boss."
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording issue aya Boss."
                        SpeechRecognizer.ERROR_CLIENT -> "Mic ready ho raha hai, dubara tap karein Boss."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission needed Boss."
                        SpeechRecognizer.ERROR_NETWORK -> "Network issue audio me aya Boss."
                        else -> "Mic code $error: Please retry Boss."
                    }
                    onError(msg)
                }

                override fun onResults(results: Bundle?) {
                    if (!isUserWantingToListen) return
                    isUserWantingToListen = false
                    onListeningStateChanged?.invoke(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty() && matches[0].isNotBlank()) {
                        Log.d("SpeechManager", "Recognized voice: ${matches[0]}")
                        onResult(matches[0])
                    } else {
                        onError("Boss, audio clear nahi tha.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!isUserWantingToListen) return
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onStatus("Boss: \"${matches[0]}...\"")
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
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN", "en-US", "hi"))
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
            speechRecognizer?.cancel()
            val intent = buildRecognizerIntent()
            speechRecognizer?.startListening(intent)
            onStatus("Initializing mic...")
            onListeningStateChanged?.invoke(true)
        } catch (e: Exception) {
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

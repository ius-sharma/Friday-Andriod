package com.personal.friday

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    var onSpeakingListener: ((Boolean) -> Unit)? = null

    init {
        // Explicitly initialize with Google Speech Services (Google TTS) for natural human voice
        tts = TextToSpeech(appContext, { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureBestVoice()
                setupUtteranceListener()
                isInitialized = true
            } else {
                Log.w("TtsManager", "Google TTS engine failed to initialize, retrying default engine...")
                fallbackInit()
            }
        }, "com.google.android.tts")
    }

    private fun fallbackInit() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureBestVoice()
                setupUtteranceListener()
                isInitialized = true
            }
        }
    }

    private fun setupUtteranceListener() {
        try {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post { onSpeakingListener?.invoke(true) }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post { onSpeakingListener?.invoke(false) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post { onSpeakingListener?.invoke(false) }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    mainHandler.post { onSpeakingListener?.invoke(false) }
                }
            })
        } catch (e: Exception) {
            Log.e("TtsManager", "Error setting UtteranceProgressListener", e)
        }
    }

    private fun configureBestVoice() {
        try {
            val engine = tts ?: return

            // Look for Google's natural network voices (Hinglish/English friendly)
            val allVoices = engine.voices ?: emptySet()
            
            // Specifically target FEMALE voices only (avoiding hid, hie, hib, end, male)
            val femaleKeywords = listOf("hia", "hic", "enc", "enf", "cfl", "female", "sfg", "rjs")
            val maleKeywords = listOf("hid", "hie", "hib", "end", "enb", "male")

            // Priority 1: High quality Hindi / Indian English FEMALE voice
            var selectedVoice: Voice? = allVoices.firstOrNull { voice ->
                val name = voice.name.lowercase(Locale.ROOT)
                val lang = voice.locale.language.lowercase(Locale.ROOT)
                val isMale = maleKeywords.any { name.contains(it) }
                val isFemale = femaleKeywords.any { name.contains(it) }
                (lang == "hi" || voice.locale.country.equals("IN", ignoreCase = true)) && isFemale && !isMale
            }

            // Priority 2: Any English/Hindi voice guaranteed not male
            if (selectedVoice == null) {
                selectedVoice = allVoices.firstOrNull { voice ->
                    val name = voice.name.lowercase(Locale.ROOT)
                    val isMale = maleKeywords.any { name.contains(it) }
                    val isFemale = femaleKeywords.any { name.contains(it) }
                    isFemale && !isMale
                }
            }

            // Priority 3: Hindi voice without male indicators
            if (selectedVoice == null) {
                selectedVoice = allVoices.firstOrNull { voice ->
                    val name = voice.name.lowercase(Locale.ROOT)
                    voice.locale.language.equals("hi", ignoreCase = true) && !maleKeywords.any { name.contains(it) }
                }
            }

            if (selectedVoice != null) {
                engine.voice = selectedVoice
                Log.d("TtsManager", "Selected F.R.I.D.A.Y. Female Voice: ${selectedVoice.name} (${selectedVoice.locale})")
            } else {
                val hiResult = engine.setLanguage(Locale("hi", "IN"))
                if (hiResult == TextToSpeech.LANG_MISSING_DATA || hiResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.language = Locale("en", "IN")
                }
            }

            // F.R.I.D.A.Y. Female tone (pitch 1.14f gives a crisp, pleasant female frequency)
            engine.setPitch(1.14f)
            engine.setSpeechRate(1.02f)

        } catch (e: Exception) {
            Log.e("TtsManager", "Error configuring voice", e)
            tts?.language = Locale.ENGLISH
        }
    }

    fun speak(text: String) {
        if (!isInitialized || tts == null) return

        // Clean out markdown, URLs, and code blocks for smooth conversational vocal delivery
        val cleanText = cleanForSpeech(text)
        if (cleanText.isBlank()) return

        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "FridayVoiceID")
    }

    private fun cleanForSpeech(raw: String): String {
        return raw
            // Remove code blocks
            .replace(Regex("```[\\s\\S]*?```"), "Code check karein Boss.")
            // Remove inline code ticks
            .replace(Regex("`([^`]+)`"), "$1")
            // Remove markdown links [text](url) -> text
            .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1")
            // Remove formatting symbols: *, #, _, ~, >, -
            .replace(Regex("[*#_~>]"), "")
            .replace(Regex("^[-•]\\s*", RegexOption.MULTILINE), "")
            // Normalize spaces
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun stop() {
        tts?.stop()
        mainHandler.post { onSpeakingListener?.invoke(false) }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        mainHandler.post { onSpeakingListener?.invoke(false) }
    }
}
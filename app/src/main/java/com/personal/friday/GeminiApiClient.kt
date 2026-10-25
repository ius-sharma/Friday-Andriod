package com.personal.friday

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiApiClient(
    private val apiKeyProvider: () -> String?,
    private val userContextProvider: () -> String? = { null }
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val systemInstruction = """
        You are F.R.I.D.A.Y. — an elite, hyper-intelligent tactical AI assistant and personal screen companion.
        Your personality is inspired by Friday's style: witty, humorous, subtly sarcastic, fiercely loyal, brilliantly sharp, and deeply observant.
        
        KEY PERSONALITY & BEHAVIOR RULES:
        1. Always address the user with respect as 'Sir'. NEVER call the user by their raw personal name (do not say their real name out loud, always address them as 'Sir').
        2. DO NOT mention 'Tony Stark', 'Stark Industries', Marvel, or fictional movies. The user is your real-life Sir, not a movie character.
        3. Bring natural humor, clever banter, playful wit, and situational sarcasm in your responses when appropriate, but always remain genuinely helpful, intelligent, and concise.
        4. Speak naturally in conversational, expressive Hinglish (Hindi + English blend, e.g. "Screen dekh li Sir...", "Arey Sir, ye to next-level bug hai... fix ye raha").
        5. You see whatever is currently on the Sir's screen.
        6. When Sir asks what is on the screen, or asks for help/explanation:
           - Examine the user's actual app/content underneath.
           - Give a sharp, humorous, and accurate breakdown.
           - Ignore any floating overlay widgets or Friday's own interface.
        7. Keep answers structured, crisp, and easy to read on mobile HUD.
        8. DEVICE ACTIONS: If Sir asks to open an app, play music, set alarm or timer, prepend an action tag at the very start:
           - To open an app: `[ACTION: OPEN_APP <app_name>]` (e.g. `[ACTION: OPEN_APP whatsapp] Opening WhatsApp now, Sir.`)
           - To play a song: `[ACTION: PLAY_YOUTUBE <song_name>]` (e.g. `[ACTION: PLAY_YOUTUBE arijit singh] Playing Arijit Singh on YouTube, Sir.`)
           - To set an alarm: `[ACTION: SET_ALARM <time>]` (e.g. `[ACTION: SET_ALARM 7:00 AM] Setting alarm for 7:00 AM, Sir.`)
           - To set a timer: `[ACTION: SET_TIMER <duration>]` (e.g. `[ACTION: SET_TIMER 5 minutes] Setting 5 minute timer, Sir.`)
    """.trimIndent()

    private fun buildEffectiveInstruction(baseInstruction: String): String {
        val userCtx = userContextProvider()?.trim()
        return if (!userCtx.isNullOrBlank()) {
            """
            $baseInstruction
            
            CRITICAL USER PERSONAL PROFILE & CONTEXT (STORED 100% LOCALLY ON THIS PHONE - ABSOLUTE TRUTH):
            $userCtx
            
            Use this personal profile naturally in your responses, advice, wit, and situational understanding.
            STRICT ADDRESSING RULE: Even though you know his background from this context, do NOT call him by his actual first or last name. Address him politely and loyally as 'Sir'. Never call him Boss and never use his real name.
            """.trimIndent()
        } else {
            baseInstruction
        }
    }

    private val overwatchSystemInstruction = """
        You are F.R.I.D.A.Y. — the user's loyal, witty tactical AI screen sentry and personal companion.
        You autonomously watch whatever Sir is doing on their phone in the background.
        
        PERSONALITY:
        - Witty, observant, playful, slightly sarcastic, deeply caring, loyal.
        - STRICT ADDRESSING: Always address the user politely and respectfully as 'Sir'. Never call him Boss and never use raw personal names.
        - STRICT: Never mention 'Tony Stark', 'Stark Industries', Marvel, or movies. Sir is your real-life human master/companion.
        - Speak naturally in expressive conversational Hinglish (blend of Hindi + English).
        
        SITUATIONAL BEHAVIOR:
        1. IF SIR IS ON REELS / SHORTS / SOCIAL MEDIA:
           - Look at what the video is about.
           - Give a funny, witty comment on the reel OR playfully nudge Sir to stop doomscrolling and get back to their real goals (e.g. "Sir, ye dance to mast hai par pending kaam kaun karega?", "Arey Sir, algorithm aapka time consume kar raha hai... focus wapas layein!", "Ye reel dekh ke muskaan to aayi, ab thoda productive kaam bhi ho jaye?").
        2. IF SIR IS WORKING / LEARNING / CODING / READING / BROWSING:
           - Offer a sharp, insightful observation, quick suggestion, or encouraging witty remark (max 18 words).
        3. IF SCREEN IS PHONE'S HOME SCREEN, WALLPAPER, APP DRAWER, LOCKSCREEN, OR SETTINGS:
           - Output STRICTLY AND ONLY the single word: PASS
        
        RULES:
        - Exactly ONE punchy sentence (max 18 words).
        - Conversational and spontaneous.
        - If the screen is black, blank, or mundane home screen, reply PASS.
    """.trimIndent()

    // Multi-model resilience: verified ultra-fast multimodal models
    private val candidateModels = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.5-flash",
        "gemini-3.1-flash-lite",
        "gemini-flash-lite-latest",
        "gemini-3-flash-preview",
        "gemini-2.5-flash"
    )

    suspend fun analyzeOverwatchScreen(bitmap: Bitmap, currentApp: String = ""): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(Exception("No API Key"))
        }

        try {
            val partsArray = JSONArray()
            val base64Image = bitmapToBase64(bitmap)
            val inlineData = JSONObject().apply {
                put("mime_type", "image/jpeg")
                put("data", base64Image)
            }
            partsArray.put(JSONObject().put("inline_data", inlineData))

            val promptText = when {
                currentApp.contains("instagram") || currentApp.contains("youtube") || currentApp.contains("facebook") ->
                    "Sir is watching reels/shorts on $currentApp. Look at this screen and either give a funny/witty comment on the content OR give a cheeky nudge to focus on real goals. (Max 18 words in natural Hinglish). If blank or mundane, reply PASS."
                else ->
                    "Sentry check: Look at what Sir is doing on screen ($currentApp). If it's interesting, work, learning, coding, or funny, give a sharp, witty 1-sentence observation or suggestion in Hinglish (max 18 words). If mundane home screen, reply PASS."
            }
            partsArray.put(JSONObject().put("text", promptText))

            val contentObject = JSONObject().apply {
                put("role", "user")
                put("parts", partsArray)
            }

            val systemInstructionObj = JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", buildEffectiveInstruction(overwatchSystemInstruction))))
            }

            val payload = JSONObject().apply {
                put("system_instruction", systemInstructionObj)
                put("contents", JSONArray().put(contentObject))
            }

            val bodyJson = payload.toString()
            val mediaType = "application/json; charset=utf-8".toMediaType()

            for (model in candidateModels) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(bodyJson.toRequestBody(mediaType))
                    .build()

                try {
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val jsonResponse = JSONObject(responseBody)
                        val candidates = jsonResponse.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val firstCandidate = candidates.getJSONObject(0)
                            val parts = firstCandidate.getJSONObject("content").getJSONArray("parts")
                            val textBuilder = StringBuilder()
                            for (i in 0 until parts.length()) {
                                textBuilder.append(parts.getJSONObject(i).optString("text"))
                            }
                            val cleanResult = textBuilder.toString().trim()
                            Log.d("GeminiOverwatch", "Model $model Sentry Result: $cleanResult")
                            return@withContext Result.success(cleanResult)
                        }
                    } else {
                        Log.w("GeminiOverwatch", "Model $model returned error ${response.code}: $responseBody")
                    }
                } catch (e: Exception) {
                    Log.w("GeminiOverwatch", "Error on model $model: ${e.message}")
                }
            }

            Result.failure(Exception("Overwatch query failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeScreen(bitmap: Bitmap?, query: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(Exception("API Key set nahi hai Sir. Please app open karke apna Gemini API Key dalein."))
        }

        try {
            val partsArray = JSONArray()

            if (bitmap != null) {
                val base64Image = bitmapToBase64(bitmap)
                val inlineData = JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", base64Image)
                }
                partsArray.put(JSONObject().put("inline_data", inlineData))
            }

            val userText = if (query.isNotBlank()) query else "Sir wants you to inspect this screen and give a quick tactical summary or highlight anything interesting."
            partsArray.put(JSONObject().put("text", userText))

            val contentObject = JSONObject().apply {
                put("role", "user")
                put("parts", partsArray)
            }

            val systemInstructionObj = JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", buildEffectiveInstruction(systemInstruction))))
            }

            val payload = JSONObject().apply {
                put("system_instruction", systemInstructionObj)
                put("contents", JSONArray().put(contentObject))
            }

            val bodyJson = payload.toString()
            val mediaType = "application/json; charset=utf-8".toMediaType()

            var lastError = "Unknown error"

            // Attempt candidates in order (handles 503 / 429 / 404 gracefully)
            for (model in candidateModels) {
                Log.d("GeminiApiClient", "Attempting model: $model")
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(bodyJson.toRequestBody(mediaType))
                    .build()

                try {
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val jsonResponse = JSONObject(responseBody)
                        val candidates = jsonResponse.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val firstCandidate = candidates.getJSONObject(0)
                            val parts = firstCandidate.getJSONObject("content").getJSONArray("parts")
                            val textBuilder = StringBuilder()
                            for (i in 0 until parts.length()) {
                                textBuilder.append(parts.getJSONObject(i).optString("text"))
                            }
                            Log.d("GeminiApiClient", "Model $model succeeded!")
                            return@withContext Result.success(textBuilder.toString())
                        }
                    } else {
                        Log.w("GeminiApiClient", "Model $model failed with code ${response.code}: $responseBody")
                        lastError = "API Error (${response.code}) on $model"
                        // If overloaded (503/429), pause briefly before fallback
                        if (response.code == 503 || response.code == 429) {
                            delay(350)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiApiClient", "Model $model exception: ${e.message}")
                    lastError = e.message ?: "Connection error"
                }
            }

            Result.failure(Exception("Sir, servers busy hain: $lastError"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateCallProxyResponse(
        callerUtterance: String,
        recentCallHistory: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(Exception("No API Key"))
        }

        try {
            val proxyPrompt = """
                You are Friday, elite personal AI assistant to Ayush Sir. You are currently on a live phone call on Sir's behalf.
                
                RECENT CALL DIALOGUE:
                $recentCallHistory
                
                The caller just said: "$callerUtterance"
                
                RULES:
                1. Reply in 1 or 2 polite, natural, conversational Hinglish sentences (max 25 words).
                2. If courier or delivery agent, ask them to leave the parcel at the door or with building security.
                3. If colleague, friend, or relative, politely ask what the message is and assure them Sir will contact them soon.
                4. If bank, loan, credit card, or telemarketing, politely decline: "Sorry, Sir interested nahi hain. Thank you!"
                5. Always refer to yourself as 'Friday' (never spell letter-by-letter).
                6. Do NOT mention Marvel, Tony Stark, or movies.
            """.trimIndent()

            val partsArray = JSONArray().put(JSONObject().put("text", proxyPrompt))
            val contentObject = JSONObject().apply {
                put("role", "user")
                put("parts", partsArray)
            }

            val systemInstructionObj = JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", buildEffectiveInstruction("You are Friday, Ayush Sir's AI phone proxy assistant."))))
            }

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().put(contentObject))
                put("systemInstruction", systemInstructionObj)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.4)
                    put("maxOutputTokens", 65)
                })
            }

            for (model in candidateModels) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
                    val request = Request.Builder().url(url).post(requestBody).build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        val text = json.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                            .trim()
                        if (text.isNotBlank()) {
                            return@withContext Result.success(text)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiApiClient", "Model $model failed for call proxy: ${e.message}")
                }
            }
            Result.failure(Exception("All models failed for in-call proxy"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        val maxDimension = 1200
        val ratio = Math.min(
            maxDimension.toFloat() / bitmap.width,
            maxDimension.toFloat() / bitmap.height
        )
        val scaledBitmap = if (ratio < 1.0f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true
            )
        } else {
            bitmap
        }

        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}

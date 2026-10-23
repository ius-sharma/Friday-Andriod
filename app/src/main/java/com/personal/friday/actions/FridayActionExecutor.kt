package com.personal.friday.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import java.net.URLEncoder

data class ActionResult(
    val success: Boolean,
    val ttsMessage: String,
    val whisperMessage: String
)

object FridayActionExecutor {

    private const val TAG = "FridayActionExecutor"

    // Primary package map for fast instant launching
    private val KNOWN_APP_ALIASES = mapOf(
        "whatsapp business" to "com.whatsapp.w4b",
        "wa business" to "com.whatsapp.w4b",
        "business whatsapp" to "com.whatsapp.w4b",
        "whatsapp" to "com.whatsapp",
        "wa" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "yt" to "com.google.android.youtube",
        "instagram" to "com.instagram.android",
        "insta" to "com.instagram.android",
        "ig" to "com.instagram.android",
        "chrome" to "com.android.chrome",
        "google chrome" to "com.android.chrome",
        "browser" to "com.android.chrome",
        "internet" to "com.android.chrome",
        "telegram" to "org.telegram.messenger",
        "truecaller" to "com.truecaller",
        "groww" to "tech.butterfly.app",
        "zomato" to "com.application.zomato",
        "duolingo" to "com.duolingo",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "photos" to "com.google.android.apps.photos",
        "gallery" to "com.google.android.apps.photos",
        "keep" to "com.google.android.keep",
        "notes" to "com.google.android.keep",
        "spotify" to "com.spotify.music"
    )

    fun tryExecuteAction(context: Context, rawQuery: String): ActionResult? {
        val q = cleanQuery(rawQuery)

        // Ignore if query looks like a question or information query
        if (isInformationQuery(q)) {
            return null
        }

        // 1. Try Media Controls (Pause, Resume, Next, Previous)
        val mediaControlResult = tryExecuteMediaControl(context, q)
        if (mediaControlResult != null) {
            return mediaControlResult
        }

        // 2. Try Music Playback ("Play Believer on YouTube", "Song bajao", etc.)
        val musicResult = tryExecuteMusicPlayback(context, q)
        if (musicResult != null) {
            return musicResult
        }

        // 3. Try App Launching ("WhatsApp kholo", "Open YouTube", etc.)
        val appLaunchResult = tryExecuteAppLaunch(context, q)
        if (appLaunchResult != null) {
            return appLaunchResult
        }

        return null
    }

    private fun cleanQuery(query: String): String {
        return query.trim()
            .replace(Regex("""^(?:hey\s+|ok\s+|hello\s+)?(?:friday\s+)?(?:please\s+)?(?:zara\s+)?(?:ek\s+baar\s+)?""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun isInformationQuery(q: String): Boolean {
        val lower = q.lowercase()
        val questionKeywords = listOf(
            "kya hai", "what is", "kaise", "how to", "kyun", "why",
            "meaning", "kisko", "kiska", "batao", "explain", "kya hota"
        )
        return questionKeywords.any { lower.contains(it) }
    }

    // --- MEDIA CONTROLS ---

    private fun tryExecuteMediaControl(context: Context, query: String): ActionResult? {
        val lower = query.lowercase().trim()

        val isPause = lower.matches(Regex("""^(?:pause|stop|roko|gaana\s+roko|music\s+roko|gaana\s+pause|music\s+pause|band\s+karo)(?:\s+(?:karo|song|music))?$"""))
        if (isPause) {
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
            return ActionResult(true, "Music paused, Boss.", "⏸️ Music Paused")
        }

        val isResume = lower.matches(Regex("""^(?:resume|unpause|play|gaana\s+chalao|music\s+chalao|gaana\s+play|music\s+play)(?:\s+(?:karo|song|music))?$"""))
        if (isResume) {
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY)
            return ActionResult(true, "Resuming playback, Boss.", "▶️ Music Resumed")
        }

        val isNext = lower.matches(Regex("""^(?:next|next\s+song|next\s+track|agla\s+gaana|change\s+song|skip|gaana\s+badlo)(?:\s+karo)?$"""))
        if (isNext) {
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            return ActionResult(true, "Skipping to next track, Boss.", "⏭️ Next Track")
        }

        val isPrevious = lower.matches(Regex("""^(?:previous|previous\s+song|pichhla\s+gaana|pehle\s+wala\s+gaana|prev\s+track)(?:\s+karo)?$"""))
        if (isPrevious) {
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            return ActionResult(true, "Playing previous track, Boss.", "⏮️ Previous Track")
        }

        return null
    }

    private fun dispatchMediaKey(context: Context, keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                Log.i(TAG, "Dispatched media key: $keyCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed dispatching media key $keyCode", e)
        }
    }

    // --- MUSIC PLAYBACK ---

    private fun tryExecuteMusicPlayback(context: Context, query: String): ActionResult? {
        val lower = query.lowercase().trim()

        // Match music intents:
        // "play <song> on youtube", "<song> bajao youtube pe", "youtube pe <song> chalao", "<song> bajao"
        val musicRegexes = listOf(
            Regex("""^(?:play|bajao|chalao|lagao|sunao)\s+(?:song\s+|gaana\s+)?(.+?)(?:\s+(?:on|pe|in)\s+youtube)?$""", RegexOption.IGNORE_CASE),
            Regex("""^(?:youtube\s+(?:pe|par)\s+)?(.+?)\s+(?:bajao|chalao|play\s+karo|lagao|sunao)$""", RegexOption.IGNORE_CASE),
            Regex("""^(?:youtube\s+(?:pe|par)\s+play\s+karo)\s+(.+)$""", RegexOption.IGNORE_CASE)
        )

        var songTitle: String? = null
        for (regex in musicRegexes) {
            val match = regex.find(lower)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.isNotBlank() && !isAppLaunchCandidate(candidate)) {
                    songTitle = candidate
                    break
                }
            }
        }

        if (songTitle.isNullOrBlank()) {
            return null
        }

        // Clean up common filler words from song title
        val cleanedSong = songTitle
            .replace(Regex("""^(?:song|gaana|music|track)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:song|gaana|music|track)$""", RegexOption.IGNORE_CASE), "")
            .trim()

        if (cleanedSong.isBlank()) return null

        Log.i(TAG, "Detected Music Playback query: \"$cleanedSong\"")
        return playOnYouTube(context, cleanedSong)
    }

    private fun playOnYouTube(context: Context, songQuery: String): ActionResult {
        return try {
            val pm = context.packageManager
            val ytPkg = "com.google.android.youtube"
            val isYtInstalled = try {
                pm.getPackageInfo(ytPkg, 0)
                true
            } catch (_: Exception) {
                false
            }

            if (isYtInstalled) {
                val ytIntent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage(ytPkg)
                    putExtra("query", songQuery)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                context.startActivity(ytIntent)
            } else {
                val webUrl = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(songQuery, "UTF-8")
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
            }

            ActionResult(
                success = true,
                ttsMessage = "Playing $songQuery on YouTube, Boss.",
                whisperMessage = "🎵 YouTube: $songQuery"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch YouTube for: $songQuery", e)
            ActionResult(
                success = false,
                ttsMessage = "Boss, gaana play karne me dikkat aayi: ${e.message}",
                whisperMessage = "⚠️ Music playback error"
            )
        }
    }

    // --- APP LAUNCHER ---

    private fun tryExecuteAppLaunch(context: Context, query: String): ActionResult? {
        val lower = query.lowercase().trim()

        // App launch regex patterns:
        // e.g. "whatsapp kholo", "open youtube", "launch instagram", "chrome chalao"
        val appLaunchRegexes = listOf(
            Regex("""^(?:open|kholo|launch|chalao|start|chalu\s+karo)\s+(?:the\s+)?(?:app\s+)?(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""^(.+?)\s+(?:open\s+karo|kholo|launch\s+karo|chalao|chalu\s+karo|open|khol\s+do|kholdo)$""", RegexOption.IGNORE_CASE)
        )

        var targetAppName: String? = null
        for (regex in appLaunchRegexes) {
            val match = regex.find(lower)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.isNotBlank()) {
                    targetAppName = candidate
                    break
                }
            }
        }

        if (targetAppName.isNullOrBlank()) {
            return null
        }

        val cleanedTarget = targetAppName
            .replace(Regex("""\s+(?:app|application|karo|ko)$""", RegexOption.IGNORE_CASE), "")
            .trim()

        if (cleanedTarget.isBlank()) return null

        Log.i(TAG, "Detected App Launch candidate: \"$cleanedTarget\"")

        // 1. Check Special System Apps (Camera, Settings, Calculator, Clock, Dial)
        val systemResult = tryLaunchSystemApp(context, cleanedTarget)
        if (systemResult != null) {
            return systemResult
        }

        // 2. Check Static Aliases Map
        val knownPkg = KNOWN_APP_ALIASES[cleanedTarget]
        if (knownPkg != null) {
            return launchPackage(context, knownPkg, cleanedTarget)
        }

        // 3. Dynamic Installed App Search across entire device
        val dynamicPkg = findInstalledAppPackage(context, cleanedTarget)
        if (dynamicPkg != null) {
            return launchPackage(context, dynamicPkg.first, dynamicPkg.second)
        }

        return null
    }

    private fun isAppLaunchCandidate(text: String): Boolean {
        val t = text.lowercase()
        return KNOWN_APP_ALIASES.containsKey(t) ||
                listOf("camera", "settings", "calculator", "dialer", "phone", "gallery").contains(t)
    }

    private fun tryLaunchSystemApp(context: Context, appName: String): ActionResult? {
        try {
            when (appName) {
                "camera", "cam", "photo" -> {
                    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        return ActionResult(true, "Opening Camera now, Boss.", "📷 Opening Camera")
                    }
                }
                "settings", "setting" -> {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return ActionResult(true, "Opening Settings now, Boss.", "⚙️ Opening Settings")
                }
                "calculator", "calc", "hisaab" -> {
                    val intent = Intent().apply {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_APP_CALCULATOR)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        return ActionResult(true, "Opening Calculator, Boss.", "🔢 Opening Calculator")
                    }
                }
                "clock", "alarm", "ghadi" -> {
                    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        return ActionResult(true, "Opening Clock now, Boss.", "⏰ Opening Clock")
                    }
                }
                "phone", "dialer", "call" -> {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return ActionResult(true, "Opening Dialer, Boss.", "📞 Opening Phone")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching system app: $appName", e)
        }
        return null
    }

    private fun launchPackage(context: Context, packageName: String, displayName: String): ActionResult {
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }

            if (launchIntent != null) {
                context.startActivity(launchIntent)
                val prettyName = displayName.replaceFirstChar { it.uppercase() }
                ActionResult(
                    success = true,
                    ttsMessage = "Opening $prettyName now, Boss.",
                    whisperMessage = "🚀 Opening $prettyName"
                )
            } else {
                ActionResult(
                    success = false,
                    ttsMessage = "Boss, $displayName app installed nahi mila.",
                    whisperMessage = "⚠️ $displayName not installed"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching package $packageName", e)
            ActionResult(
                success = false,
                ttsMessage = "Boss, app open karne me error aaya: ${e.message}",
                whisperMessage = "⚠️ Launch error"
            )
        }
    }

    private fun findInstalledAppPackage(context: Context, targetName: String): Pair<String, String>? {
        return try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(mainIntent, 0)
            val cleanTarget = targetName.lowercase().replace(" ", "")

            var bestMatch: Pair<String, String>? = null

            for (resolveInfo in apps) {
                val label = resolveInfo.loadLabel(pm).toString()
                val pkg = resolveInfo.activityInfo.packageName
                val cleanLabel = label.lowercase().replace(" ", "")

                if (cleanLabel == cleanTarget) {
                    return Pair(pkg, label) // Exact match
                }

                if (bestMatch == null && (cleanLabel.contains(cleanTarget) || cleanTarget.contains(cleanLabel))) {
                    bestMatch = Pair(pkg, label)
                }
            }

            bestMatch
        } catch (e: Exception) {
            Log.e(TAG, "Error querying installed apps for $targetName", e)
            null
        }
    }
}

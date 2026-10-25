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
import java.util.Calendar
import java.util.Locale

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

        // 1. Try Alarms & Timers ("friday alarm laga do 7 baje ka", "timer laga do 5 min", etc.)
        val alarmResult = tryExecuteAlarm(context, q)
        if (alarmResult != null) {
            return alarmResult
        }

        // 2. Try Media Controls (Pause, Resume, Next, Previous)
        val mediaControlResult = tryExecuteMediaControl(context, q)
        if (mediaControlResult != null) {
            return mediaControlResult
        }

        // 3. Try Music Playback ("Play Believer on YouTube", "Song bajao", etc.)
        val musicResult = tryExecuteMusicPlayback(context, q)
        if (musicResult != null) {
            return musicResult
        }

        // 4. Try App Launching ("WhatsApp kholo", "Open YouTube", etc.)
        val appLaunchResult = tryExecuteAppLaunch(context, q)
        if (appLaunchResult != null) {
            return appLaunchResult
        }

        return null
    }

    // --- ALARMS & TIMERS ---

    private fun tryExecuteAlarm(context: Context, query: String): ActionResult? {
        val lower = query.lowercase().trim()

        val isAlarmQuery = lower.contains("alarm") || lower.contains("timer") ||
                lower.contains("utha dena") || lower.contains("wake me up") ||
                lower.contains("remind me at") || lower.contains("ghadi")

        if (!isAlarmQuery) return null

        // A. Show alarms ("alarm dikhao", "show alarms", "alarms check karo")
        if (lower.matches(Regex("""^(?:show|check|dikhao|open|kholo)?\s*(?:all\s+)?alarms?(?:\s*(?:dikhao|kholo|check\s+karo))?$"""))) {
            return try {
                val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                ActionResult(true, "Displaying your alarms, Boss.", "⏰ Showing Alarms")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show alarms", e)
                ActionResult(false, "Boss, alarms open karne me dikkat aayi: ${e.message}", "⚠️ Alarm Error")
            }
        }

        // B. Timer ("5 minute ka timer", "set timer for 10 minutes", "15 second ka timer")
        val timerMatch = Regex("""(?:(?:set\s+)?timer\s+(?:for\s+)?(\d+)\s*(min|minute|sec|second|ghanta|hour|hr)s?|(\d+)\s*(minute|min|second|sec|ghante)\s*ka\s*timer)""").find(lower)
        if (timerMatch != null) {
            val amountStr = timerMatch.groupValues[1].ifEmpty { timerMatch.groupValues[3] }
            val unitStr = timerMatch.groupValues[2].ifEmpty { timerMatch.groupValues[4] }
            val amount = amountStr.toIntOrNull() ?: 5
            val seconds = when {
                unitStr.startsWith("sec") -> amount
                unitStr.startsWith("ghant") || unitStr.startsWith("hour") || unitStr.startsWith("hr") -> amount * 3600
                else -> amount * 60
            }
            val displayStr = when {
                seconds >= 3600 -> "${seconds / 3600} hr"
                seconds >= 60 -> "${seconds / 60} mins"
                else -> "$seconds secs"
            }
            return try {
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "Friday Timer")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                ActionResult(true, "Setting a $displayStr timer, Boss.", "⏳ Timer: $displayStr")
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "Friday Timer")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                    ActionResult(true, "Timer set for $displayStr, Boss.", "⏳ Timer: $displayStr")
                } catch (t: Throwable) {
                    ActionResult(false, "Boss, timer set nahi ho paya: ${t.message}", "⚠️ Timer Error")
                }
            }
        }

        // C. Relative Alarm ("aadhe ghante baad ka alarm", "10 minute baad ka alarm", "1 ghante baad")
        val relativeMatch = Regex("""(?:(\d+|aadhe?)\s*(minute|min|ghante|ghanta|hour|hours)?\s*baad(?:\s*ka\s*alarm)?)""").find(lower)
        if (relativeMatch != null) {
            val numRaw = relativeMatch.groupValues[1]
            val unit = relativeMatch.groupValues[2]
            val minutesToAdd = if (numRaw.startsWith("aadh")) {
                30
            } else {
                val n = numRaw.toIntOrNull() ?: 30
                if (unit.startsWith("ghant") || unit.startsWith("hour")) n * 60 else n
            }
            val nowCal = Calendar.getInstance()
            nowCal.add(Calendar.MINUTE, minutesToAdd)
            val targetHour = nowCal.get(Calendar.HOUR_OF_DAY)
            val targetMin = nowCal.get(Calendar.MINUTE)
            return setAlarmDirect(context, targetHour, targetMin)
        }

        // D. Specific Time Alarm
        val normalized = normalizeHindiTimeWords(lower)

        val timeRegexes = listOf(
            Regex("""(?:alarm\s+(?:laga\s*do|lagao|set\s*karo)?\s*)?(?:(subah|shaam|raat|dopahar)\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*(?:baje)?(?:\s*ka\s*alarm)?"""),
            Regex("""(?:(subah|shaam|raat|dopahar)\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*baje\s*(?:ka\s+alarm|utha\s+dena|alarm\s+laga\s*do)"""),
            Regex("""(?:set\s+(?:an\s+)?alarm\s+(?:for|at)\s+)(\d{1,2})(?::(\d{2}))?\s*(am|pm)?"""),
            Regex("""(?:wake\s+me\s+up\s+at\s+)(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")
        )

        for (regex in timeRegexes) {
            val m = regex.find(normalized)
            if (m != null) {
                var timeOfDay = ""
                var hourStr = ""
                var minStr = ""
                var amPmStr = ""

                for (i in 1 until m.groupValues.size) {
                    val g = m.groupValues[i].trim()
                    if (g.isEmpty()) continue
                    if (g == "subah" || g == "shaam" || g == "raat" || g == "dopahar") {
                        timeOfDay = g
                    } else if (g == "am" || g == "pm") {
                        amPmStr = g
                    } else if (hourStr.isEmpty() && g.toIntOrNull() != null) {
                        hourStr = g
                    } else if (minStr.isEmpty() && g.toIntOrNull() != null) {
                        minStr = g
                    }
                }

                if (hourStr.isNotEmpty()) {
                    var hour = hourStr.toInt()
                    val minute = if (minStr.isNotEmpty()) minStr.toInt() else 0

                    if (hour in 0..24 && minute in 0..59) {
                        val isPm = amPmStr == "pm" || timeOfDay == "shaam" || timeOfDay == "raat" || timeOfDay == "dopahar"
                        val isAm = amPmStr == "am" || timeOfDay == "subah"

                        if (isPm) {
                            if (hour < 12) hour += 12
                        } else if (isAm) {
                            if (hour == 12) hour = 0
                        } else {
                            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                            if (hour in 1..4 && currentHour in 9..17) {
                                hour += 12
                            }
                        }
                        if (hour >= 24) hour = 0
                        return setAlarmDirect(context, hour, minute)
                    }
                }
            }
        }

        // E. Generic "alarm laga do" without time -> Open Clock alarm setter
        if (lower.matches(Regex("""^(?:alarm\s+(?:laga\s*do|lagao|set\s*karo)|set\s+(?:an\s+)?alarm)$"""))) {
            return try {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                ActionResult(true, "Alarm setup open kar diya hai Boss, time select kar lijiye.", "⏰ Setting Alarm")
            } catch (e: Exception) {
                ActionResult(false, "Boss, clock open nahi ho paaya: ${e.message}", "⚠️ Clock Error")
            }
        }

        return null
    }

    private fun setAlarmDirect(context: Context, hour: Int, minute: Int): ActionResult {
        val dispAmPm = if (hour >= 12) "PM" else "AM"
        val dispHour = when (val h = hour % 12) {
            0 -> 12
            else -> h
        }
        val formattedTime = String.format(Locale.US, "%02d:%02d %s", dispHour, minute, dispAmPm)

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Friday Alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ActionResult(
                success = true,
                ttsMessage = "Alarm set for $formattedTime, Boss. Main waqt par aapko alert kar dungi.",
                whisperMessage = "⏰ Alarm Set: $formattedTime"
            )
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "Friday Alarm")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                ActionResult(
                    success = true,
                    ttsMessage = "Alarm set for $formattedTime, Boss.",
                    whisperMessage = "⏰ Alarm: $formattedTime"
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to set alarm for $formattedTime", t)
                ActionResult(
                    success = false,
                    ttsMessage = "Boss, alarm set karne me dikkat aayi: ${t.message}",
                    whisperMessage = "⚠️ Alarm Set Error"
                )
            }
        }
    }

    private fun normalizeHindiTimeWords(input: String): String {
        var s = input
        val words = mapOf(
            "ek" to "1", "do" to "2", "teen" to "3", "chaar" to "4",
            "paanch" to "5", "chhe" to "6", "che" to "6", "saat" to "7",
            "aath" to "8", "nau" to "9", "das" to "10",
            "gyarah" to "11", "barah" to "12"
        )
        for ((word, digit) in words) {
            s = s.replace(Regex("\\b$word\\b"), digit)
        }
        s = s.replace(Regex("""saadhe\s*(\d+)"""), "$1:30")
        s = s.replace(Regex("""sawa\s*(\d+)"""), "$1:15")
        val pauneMatch = Regex("""(?:paune|pone)\s*(\d+)""").find(s)
        if (pauneMatch != null) {
            val d = pauneMatch.groupValues[1].toIntOrNull() ?: 1
            val prevD = if (d == 1) 12 else d - 1
            s = s.replace(pauneMatch.value, "$prevD:45")
        }
        return s
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

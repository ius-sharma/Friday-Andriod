package com.personal.friday

import android.annotation.SuppressLint
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class FridayOverlayService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var cardView: View? = null
    private var whisperView: View? = null

    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var cardParams: WindowManager.LayoutParams
    private lateinit var whisperParams: WindowManager.LayoutParams

    private var screenCaptureManager: ScreenCaptureManager? = null
    private lateinit var geminiClient: GeminiApiClient
    private lateinit var speechManager: SpeechManager
    private lateinit var ttsManager: TtsManager
    private lateinit var hotwordSpotter: HotwordSpotter
    private var isWakeWordEnabled = true

    private var lastResponseText = ""
    private var isCardVisible = false
    private var isWhisperVisible = false
    private val screenSize = Point()

    // Stark Overwatch Sentry state
    private var overwatchJob: Job? = null
    private var isOverwatchEnabled = true
    private var overwatchCooldownUntil = 0L
    private var lastScreenHash = 0L
    private var lastPixelSum = 0L
    private val whisperDismissRunnable = Runnable { hideWhisper() }

    // Doomscroll Protocol (Reel Sentry) state
    private var isDoomscrollEnabled = true
    internal var doomscrollReelCount = 0
    private var lastReelChangeTime = 0L
    private var hasCountedCurrentReel = false
    private var lastReelFingerprint: ReelFingerprint? = null
    private var lastReelAppLeaveTime = 0L
    private var doomscrollCooldownUntil = 0L
    private var lastReelDetectedTimestamp = 0L

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d("FridayOverlayService", "Screen OFF: Entering True Auto-Sleep (Mic OFF, Sentry Loop Paused)")
                    bubbleView?.findViewById<FridayBubbleReactorView>(R.id.bubbleReactor)?.pauseAnimationLoop()
                    try {
                        hotwordSpotter.pause()
                    } catch (t: Throwable) {
                        Log.w("FridayOverlayService", "Error pausing hotword on screen off", t)
                    }
                    stopOverwatchLoop()
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d("FridayOverlayService", "Screen ON: Resuming Wake Word & Sentry...")
                    bubbleView?.findViewById<FridayBubbleReactorView>(R.id.bubbleReactor)?.resumeAnimationLoop()
                    if (isWakeWordEnabled && isRunning) {
                        mainHandler.postDelayed({
                            startWakeWordListeningIfAllowed()
                        }, 400)
                    }
                    if (isOverwatchEnabled && isRunning) {
                        startOverwatchLoop()
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    Log.d("FridayOverlayService", "Device UNLOCKED: Checking for morning greeting...")
                    checkAndTriggerMorningGreeting(isManualTest = false)
                }
            }
        }
    }

    data class ReelFingerprint(
        val videoLum: IntArray,
        val overlayLum: IntArray,
        val avgR: Int,
        val avgG: Int,
        val avgB: Int
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i("FridayOverlayService", "FridayOverlayService onCreate starting...")
        isRunning = true
        instance = this
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(screenSize)

            val prefs = getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
            isOverwatchEnabled = prefs.getBoolean("overwatch_enabled", true)
            isDoomscrollEnabled = prefs.getBoolean("doomscroll_enabled", true)
            isWakeWordEnabled = prefs.getBoolean("wakeword_enabled", true)

            geminiClient = GeminiApiClient(
                apiKeyProvider = {
                    getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
                        .getString("gemini_api_key", null)
                },
                userContextProvider = {
                    getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
                        .getString("user_personal_context", null)
                }
            )

            ttsManager = TtsManager(this).apply {
                onSpeakingListener = { isSpeaking ->
                    val reactor = bubbleView?.findViewById<FridayBubbleReactorView>(R.id.bubbleReactor)
                    reactor?.setSpeaking(isSpeaking)
                    val glow = bubbleView?.findViewById<View>(R.id.bubbleGlow)
                    if (isSpeaking) {
                        glow?.animate()
                            ?.alpha(0.65f)
                            ?.scaleX(1.22f)
                            ?.scaleY(1.22f)
                            ?.setDuration(300)
                            ?.start()
                    } else {
                        glow?.animate()
                            ?.alpha(0.28f)
                            ?.scaleX(1.0f)
                            ?.scaleY(1.0f)
                            ?.setDuration(400)
                            ?.start()
                    }
                }
            }

            speechManager = SpeechManager(
                context = this,
                onResult = { recognizedText ->
                    processUserQuery(recognizedText)
                },
                onStatus = { status ->
                    updateCardStatus(status)
                    if (!isCardVisible) {
                        showWhisper(status)
                    }
                },
                onError = { errorMsg ->
                    updateCardStatus(errorMsg)
                    setMicActive(false)
                    if (!isCardVisible) {
                        showWhisper(errorMsg)
                    }
                }
            )

            hotwordSpotter = HotwordSpotter(
                context = this,
                onHotwordSpotted = { keyword ->
                    handleWakeWordTriggered(keyword)
                }
            )

            speechManager.onListeningStateChanged = { listening ->
                if (!listening) {
                    setMicActive(false)
                    mainHandler.postDelayed({
                        startWakeWordListeningIfAllowed()
                    }, 650)
                }
            }

            createNotificationChannel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }

            setupBubbleOverlay()
            setupCardOverlay()
            setupWhisperOverlay()

            val screenFilter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenStateReceiver, screenFilter)

            startWakeWordListeningIfAllowed()
            startOverwatchLoop()
            try {
                com.personal.friday.widget.FridayHudWidgetProvider.updateAllWidgets(this)
            } catch (_: Exception) {}
            Log.i("FridayOverlayService", "FridayOverlayService onCreate finished successfully!")
        } catch (t: Throwable) {
            Log.e("FridayOverlayService", "FATAL CRASH in FridayOverlayService onCreate", t)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("FridayOverlayService", "onStartCommand received action: ${intent?.action}")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }

            if (intent != null) {
                when (intent.action) {
                    ACTION_START_PROJECTION -> {
                        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra<Intent>(EXTRA_DATA)
                        }
                        if (resultCode != 0 && data != null) {
                            try {
                                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                val mediaProjection: MediaProjection? = projectionManager.getMediaProjection(resultCode, data)
                                if (mediaProjection != null) {
                                    screenCaptureManager?.release()
                                    screenCaptureManager = ScreenCaptureManager(this, mediaProjection)
                                    hasScreenCapture = true
                                    Log.i("FridayOverlayService", "ScreenCaptureManager initialized successfully! Screen Sentry ARMED.")
                                    startOverwatchLoop()
                                }
                            } catch (e: Exception) {
                                Log.e("FridayOverlayService", "Error initializing MediaProjection", e)
                            }
                        }
                    }
                    ACTION_UPDATE_OVERWATCH -> {
                        isOverwatchEnabled = intent.getBooleanExtra(EXTRA_OVERWATCH_ENABLED, true)
                        updateCardOverwatchUI()
                    }
                    ACTION_UPDATE_DOOMSCROLL -> {
                        isDoomscrollEnabled = intent.getBooleanExtra(EXTRA_DOOMSCROLL_ENABLED, true)
                        updateReelCounterUI()
                    }
                    ACTION_UPDATE_WAKEWORD -> {
                        isWakeWordEnabled = intent.getBooleanExtra(EXTRA_WAKEWORD_ENABLED, true)
                        if (isWakeWordEnabled) {
                            startWakeWordListeningIfAllowed()
                        } else {
                            hotwordSpotter.stop()
                        }
                    }
                    ACTION_TRIGGER_LISTENING -> {
                        handleWakeWordTriggered("widget")
                    }
                    ACTION_TRIGGER_MORNING_GREETING -> {
                        checkAndTriggerMorningGreeting(isManualTest = true)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e("FridayOverlayService", "FATAL in onStartCommand", t)
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "F.R.I.D.A.Y. Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "F.R.I.D.A.Y. tactical screen companion overlay"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("F.R.I.D.A.Y. Online")
            .setContentText("Screen Sentry active • Watching your back, Boss.")
            .setSmallIcon(R.drawable.ic_friday_bubble)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun setupBubbleOverlay() {
        val themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_Friday)
        val inflater = LayoutInflater.from(themedContext)
        bubbleView = inflater.inflate(R.layout.overlay_bubble, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = screenSize.y / 3
        }

        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var isClick = false

        bubbleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isClick = false
                    }
                    bubbleParams.x = initialX + dx
                    bubbleParams.y = initialY + dy
                    windowManager.updateViewLayout(bubbleView, bubbleParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        onBubbleClicked()
                    } else {
                        snapBubbleToEdge()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(bubbleView, bubbleParams)
            Log.i("FridayOverlayService", "Bubble overlay added successfully to WindowManager")
        } catch (t: Throwable) {
            Log.e("FridayOverlayService", "Failed to add bubble overlay", t)
        }
    }

    private fun snapBubbleToEdge() {
        val middle = screenSize.x / 2
        val bubbleWidth = bubbleView?.width?.takeIf { it > 0 } ?: (42 * resources.displayMetrics.density).toInt()
        val targetX = if (bubbleParams.x >= middle) screenSize.x - bubbleWidth - 10 else 10
        val startX = bubbleParams.x

        if (startX != targetX) {
            val snapAnim = ValueAnimator.ofInt(startX, targetX).apply {
                duration = 200L
                interpolator = DecelerateInterpolator()
                addUpdateListener { va ->
                    bubbleParams.x = va.animatedValue as Int
                    try {
                        windowManager.updateViewLayout(bubbleView, bubbleParams)
                    } catch (_: Exception) {}
                }
            }
            snapAnim.start()
        }

        if (isWhisperVisible) {
            hideWhisper()
        }
    }

    @SuppressLint("InflateParams")
    private fun setupWhisperOverlay() {
        val themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_Friday)
        val inflater = LayoutInflater.from(themedContext)
        whisperView = inflater.inflate(R.layout.overlay_whisper, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        whisperParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = screenSize.y / 3
        }

        whisperView?.findViewById<ImageButton>(R.id.btnDismissWhisper)?.setOnClickListener {
            hideWhisper()
        }

        whisperView?.setOnClickListener {
            hideWhisper()
            showCard()
        }

        whisperView?.visibility = View.GONE
        try {
            windowManager.addView(whisperView, whisperParams)
        } catch (t: Throwable) {
            Log.e("FridayOverlayService", "Failed to add whisperView", t)
        }
    }

    @SuppressLint("InflateParams")
    private fun setupCardOverlay() {
        val themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_Friday)
        val inflater = LayoutInflater.from(themedContext)
        cardView = inflater.inflate(R.layout.overlay_response_card, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        cardParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 80
        }

        cardView?.findViewById<ImageButton>(R.id.btnCloseCard)?.setOnClickListener {
            hideCard()
        }

        cardView?.findViewById<ImageButton>(R.id.btnTypeInApp)?.setOnClickListener {
            val etInput = cardView?.findViewById<EditText>(R.id.etQuickQuestion)
            val currentInput = etInput?.text?.toString()?.trim() ?: ""
            val textToInject = when {
                currentInput.isNotBlank() -> {
                    etInput?.setText("")
                    currentInput
                }
                lastResponseText.isNotBlank() -> lastResponseText
                else -> ""
            }

            if (textToInject.isNotBlank()) {
                hideCard()
                performAutoType(textToInject)
            } else {
                ttsManager.speak("Boss, pehle question box me likhiye ya koi query puchiye.")
            }
        }

        cardView?.findViewById<ImageButton>(R.id.btnTtsReplay)?.setOnClickListener {
            if (lastResponseText.isNotBlank()) {
                ttsManager.speak(lastResponseText)
            }
        }

        cardView?.findViewById<ImageButton>(R.id.btnQuickMic)?.setOnClickListener {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                updateCardStatus("Boss, Mic permission missing. Grant in main screen.")
                return@setOnClickListener
            }
            hotwordSpotter.pause()
            setMicActive(true)
            updateCardStatus("Listening to Boss… (Boliye Boss)")
            speechManager.startListening()
        }

        val etInput = cardView?.findViewById<EditText>(R.id.etQuickQuestion)
        cardView?.findViewById<ImageButton>(R.id.btnQuickSend)?.setOnClickListener {
            val text = etInput?.text?.toString()?.trim() ?: ""
            if (text.isNotBlank()) {
                etInput?.setText("")
                processUserQuery(text)
            }
        }

        // Tap outside anywhere to dismiss card and input field
        cardView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hideCard()
                true
            } else {
                false
            }
        }

        updateCardOverwatchUI()
        updateReelCounterUI()

        cardView?.findViewById<View>(R.id.btnToggleOverwatch)?.setOnClickListener {
            isOverwatchEnabled = !isOverwatchEnabled
            getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("overwatch_enabled", isOverwatchEnabled)
                .apply()
            updateCardOverwatchUI()
            val promptMsg = if (isOverwatchEnabled) "Sentry active, Boss." else "Sentry paused, Boss."
            ttsManager.speak(promptMsg)
        }

        cardView?.findViewById<View>(R.id.layoutReelBadge)?.setOnClickListener {
            android.widget.Toast.makeText(
                this,
                "Doomscroll Protocol: $doomscrollReelCount / 10 reels watched this session.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        cardView?.visibility = View.GONE
        try {
            windowManager.addView(cardView, cardParams)
        } catch (t: Throwable) {
            Log.e("FridayOverlayService", "Failed to add cardView", t)
        }
    }

    private fun updateCardOverwatchUI() {
        val btnToggle = cardView?.findViewById<View>(R.id.btnToggleOverwatch)
        val dotStatus = cardView?.findViewById<View>(R.id.dotOverwatchStatus)
        val tvBadge = cardView?.findViewById<TextView>(R.id.tvOverwatchBadge)
        val tvHint = cardView?.findViewById<TextView>(R.id.tvOverwatchHint)

        if (isOverwatchEnabled) {
            dotStatus?.setBackgroundResource(R.drawable.bg_dot_active)
            tvBadge?.text = "SENTRY: ON"
            tvBadge?.setTextColor(android.graphics.Color.WHITE)
            tvHint?.text = "Auto screen sentry • Tap to pause"
            btnToggle?.alpha = 1.0f
        } else {
            dotStatus?.setBackgroundResource(R.drawable.bg_dot_inactive)
            tvBadge?.text = "SENTRY: OFF"
            tvBadge?.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
            tvHint?.text = "Sentry paused • Tap to activate"
            btnToggle?.alpha = 0.65f
        }
    }

    private fun onBubbleClicked() {
        if (isCardVisible) {
            hideCard()
        } else {
            showCard()
        }
    }

    private fun showCard() {
        hideWhisper()
        isCardVisible = true
        updateReelCounterUI()

        // Enable focus and touch handling while card is active
        cardParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        try {
            windowManager.updateViewLayout(cardView, cardParams)
        } catch (_: Exception) {}

        cardView?.visibility = View.VISIBLE
        cardView?.alpha = 0f
        cardView?.translationY = 80f
        cardView?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setDuration(220)
            ?.setInterpolator(OvershootInterpolator(1.1f))
            ?.start()
    }

    private fun hideCard() {
        if (!isCardVisible) return
        isCardVisible = false
        speechManager.stopListening()
        ttsManager.stop()
        setMicActive(false)

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(cardView?.windowToken, 0)

        // Make overlay completely unfocusable immediately so system focus snaps back to the target app
        cardParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        try {
            windowManager.updateViewLayout(cardView, cardParams)
        } catch (_: Exception) {}

        cardView?.animate()
            ?.alpha(0f)
            ?.translationY(80f)
            ?.setDuration(180)
            ?.withEndAction {
                cardView?.visibility = View.GONE
            }
            ?.start()
    }

    private fun setMicActive(active: Boolean) {
        bubbleView?.findViewById<ImageView>(R.id.micBadge)?.visibility =
            if (active) View.VISIBLE else View.GONE
    }

    private fun updateCardStatus(statusText: String) {
        val tvQuery = cardView?.findViewById<TextView>(R.id.tvUserQuery)
        tvQuery?.text = statusText
    }

    private fun extractTypeCommand(query: String): String? {
        val q = query.trim()
        val regex = Regex(
            """^(?:hey\s+|ok\s+|hello\s+)?(?:friday\s+)?(?:please\s+)?(?:reply\s+type\s+karo|reply\s+likh\s+do|reply\s+likho|type\s+karo|type\s+kar\s+do|type\s+kardo|likh\s+do|likhdo|likho|type\s+this|type|write\s+this|write\s+down|write)\s*[:,\-]?\s*(.+)""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.find(q)
        if (match != null) {
            val text = match.groupValues[1].trim()
            if (text.isNotEmpty()) return text
        }
        return null
    }

    private fun performAutoType(text: String) {
        val accessibilityService = FridayAccessibilityService.instance
        if (accessibilityService == null) {
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Friday AutoType", text)
                clipboard.setPrimaryClip(clip)
            } catch (_: Exception) {}

            showWhisper("⚠️ Accessibility OFF • Copied text to clipboard!")
            ttsManager.speak("Boss, text clipboard me copy kar diya hai. Screen par direct type karne ke liye Settings se Friday Accessibility on kijiye.")
            return
        }

        // Hide card first to release overlay focus back to the target app
        hideCard()

        // Wait 220ms for WindowManager to shift focus back to the underlying app (WhatsApp, etc.)
        mainHandler.postDelayed({
            val typed = accessibilityService.typeTextIntoActiveField(text)
            if (typed) {
                pulseBubbleGlow()
                val preview = if (text.length > 25) text.take(25) + "..." else text
                showWhisper("✍️ Typed: \"$preview\"")
                ttsManager.speak("Likh diya Boss.")
                try {
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(45, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(45)
                    }
                } catch (_: Exception) {}
            } else {
                accessibilityService.copyToClipboard(text)
                showWhisper("⚠️ No typing field active • Copied to clipboard!")
                ttsManager.speak("Boss, kisi message ya text box par click kijiye. Text clipboard par copy kar diya hai.")
            }
        }, 220)
    }

    private fun processUserQuery(query: String) {
        val cleanQuery = query.trim()
        val textToType = extractTypeCommand(cleanQuery)
        if (textToType != null) {
            hideCard()
            performAutoType(textToType)
            return
        }

        // Feature 3: Voice App Launcher & Music Controller Actions
        val actionResult = com.personal.friday.actions.FridayActionExecutor.tryExecuteAction(this, cleanQuery)
        if (actionResult != null) {
            hideCard()
            if (actionResult.ttsMessage.isNotBlank()) {
                ttsManager.speak(actionResult.ttsMessage)
            }
            if (actionResult.whisperMessage.isNotBlank()) {
                showWhisper(actionResult.whisperMessage)
            }
            return
        }

        val wasCardOpen = isCardVisible

        // 1. Hide keyboard immediately
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(cardView?.windowToken, 0)

        // 2. Stop mic and listening state
        setMicActive(false)
        speechManager.stopListening()

        // 3. Make overlays completely invisible immediately so they do NOT appear in the screenshot
        cardView?.animate()?.cancel()
        cardView?.visibility = View.GONE
        whisperView?.animate()?.cancel()
        whisperView?.visibility = View.GONE
        bubbleView?.visibility = View.INVISIBLE

        // 4. Flush any stale buffered frames from ImageReader
        screenCaptureManager?.drainBuffer()

        serviceScope.launch(Dispatchers.Default) {
            try {
                // Wait 180ms for SurfaceFlinger to composite the clean screen without overlays or keyboard
                delay(180)

                // Capture clean bitmap of the underlying screen
                val bitmap = screenCaptureManager?.captureScreen()

                // Restore bubble
                withContext(Dispatchers.Main) {
                    bubbleView?.visibility = View.VISIBLE
                    if (wasCardOpen) {
                        showCard()
                        val tvUserQuery = cardView?.findViewById<TextView>(R.id.tvUserQuery)
                        val tvResponse = cardView?.findViewById<TextView>(R.id.tvResponseContent)
                        val progressBar = cardView?.findViewById<ProgressBar>(R.id.progressBar)
                        tvUserQuery?.text = "Boss: \"$query\""
                        tvResponse?.text = "Scanning display now, Boss..."
                        progressBar?.visibility = View.VISIBLE
                    } else {
                        showWhisper("Scanning display, Boss...")
                    }
                }

                // Analyze the clean screen with Gemini
                val result = geminiClient.analyzeScreen(bitmap, query)

                withContext(Dispatchers.Main) {
                    result.onSuccess { rawResponse ->
                        var response = rawResponse.trim()
                        val actionMatch = Regex("""^\[ACTION:\s*([A-Z_]+)\s+([^\]]+)\]""").find(response)
                        if (actionMatch != null) {
                            val actionType = actionMatch.groupValues[1]
                            val actionParam = actionMatch.groupValues[2].trim()
                            response = response.substring(actionMatch.range.last + 1).trim()
                            when (actionType) {
                                "OPEN_APP" -> com.personal.friday.actions.FridayActionExecutor.tryExecuteAction(this@FridayOverlayService, "open $actionParam")
                                "PLAY_YOUTUBE" -> com.personal.friday.actions.FridayActionExecutor.tryExecuteAction(this@FridayOverlayService, "play $actionParam on youtube")
                                "SET_ALARM" -> com.personal.friday.actions.FridayActionExecutor.tryExecuteAction(this@FridayOverlayService, "alarm laga do $actionParam")
                                "SET_TIMER" -> com.personal.friday.actions.FridayActionExecutor.tryExecuteAction(this@FridayOverlayService, "timer laga do $actionParam")
                            }
                        }
                        lastResponseText = response
                        if (wasCardOpen) {
                            val tvResponse = cardView?.findViewById<TextView>(R.id.tvResponseContent)
                            val progressBar = cardView?.findViewById<ProgressBar>(R.id.progressBar)
                            progressBar?.visibility = View.GONE
                            tvResponse?.text = response
                        } else {
                            showWhisper(response)
                        }
                        ttsManager.speak(response)
                    }.onFailure { error ->
                        val errorMsg = "Boss, error occurred: ${error.message}"
                        if (wasCardOpen) {
                            val tvResponse = cardView?.findViewById<TextView>(R.id.tvResponseContent)
                            val progressBar = cardView?.findViewById<ProgressBar>(R.id.progressBar)
                            progressBar?.visibility = View.GONE
                            tvResponse?.text = errorMsg
                        } else {
                            showWhisper(errorMsg)
                        }
                        ttsManager.speak("Boss, screen analyze nahi ho payi. Ek baar dubara tap kijiye.")
                    }
                }

                bitmap?.recycle()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    bubbleView?.visibility = View.VISIBLE
                    if (wasCardOpen) {
                        showCard()
                        val progressBar = cardView?.findViewById<ProgressBar>(R.id.progressBar)
                        val tvResponse = cardView?.findViewById<TextView>(R.id.tvResponseContent)
                        progressBar?.visibility = View.GONE
                        tvResponse?.text = "Boss, processing failure: ${e.message}"
                    } else {
                        showWhisper("Processing failure, Boss.")
                    }
                }
            }
        }
    }

    private fun handleWakeWordTriggered(keyword: String) {
        hotwordSpotter.pause()
        pulseBubbleGlow()
        // Never open the typing card on wake word - keep screen and target app undisturbed!
        setMicActive(true)
        showWhisper("⚡ Listening, Boss...")

        ttsManager.speak("Yes Boss?")
        // Wait 750ms so TTS finishes speaking "Yes Boss?" before SpeechRecognizer opens mic
        mainHandler.postDelayed({
            if (isRunning) {
                speechManager.startListening()
            }
        }, 750)
    }

    private fun startWakeWordListeningIfAllowed() {
        if (!isWakeWordEnabled) {
            hotwordSpotter.stop()
            return
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w("FridayOverlayService", "Record audio permission not granted yet")
            return
        }
        if (speechManager.isListening()) {
            hotwordSpotter.pause()
            return
        }
        try {
            hotwordSpotter.start()
        } catch (t: Throwable) {
            Log.e("FridayOverlayService", "Failed to start hotword spotter", t)
        }
    }

    // --- Stark Overwatch Autonomous Sentry & Doomscroll Protocol Logic ---

    private fun stopOverwatchLoop() {
        Log.d("FridayOverwatch", "stopOverwatchLoop: Screen locked/off. Halting all background screen captures.")
        overwatchJob?.cancel()
        overwatchJob = null
    }

    private fun startOverwatchLoop() {
        overwatchJob?.cancel()
        overwatchJob = serviceScope.launch(Dispatchers.Default) {
            delay(2000)
            Log.i("FridayOverwatch", "startOverwatchLoop active and monitoring screen!")

            while (isActive) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (powerManager?.isInteractive == false) {
                    Log.d("FridayOverwatch", "Screen inactive. Halting loop until screen ON.")
                    break
                }

                val foregroundPkg = getForegroundAppPackage()
                val isInReelApp = foregroundPkg != null && REEL_PACKAGES.contains(foregroundPkg)
                val isFridayApp = foregroundPkg != null && (foregroundPkg == packageName || foregroundPkg.startsWith("com.personal.friday"))

                if (isInReelApp || isFridayApp) {
                    lastReelAppLeaveTime = 0L
                    // Vision Reel Sentry check when screenCaptureManager is armed
                    if (isInReelApp && isDoomscrollEnabled && screenCaptureManager != null) {
                        val captureManager = screenCaptureManager
                        val frame = captureManager?.captureScreen()
                        if (frame != null) {
                            processReelFrame(frame)
                            frame.recycle()
                        }
                    }
                } else {
                    if (lastReelAppLeaveTime == 0L) {
                        lastReelAppLeaveTime = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - lastReelAppLeaveTime > 15 * 60 * 1000L && doomscrollReelCount > 0) {
                        doomscrollReelCount = 0
                        lastReelFingerprint = null
                        withContext(Dispatchers.Main) {
                            updateReelCounterUI(false)
                        }
                    }
                }

                // --- AUTONOMOUS SENTRY SCREEN INSPECTION ---
                // Operates on ALL apps (including Reels, Shorts, Chrome, Games, Code, Notes) except Home Screen
                var hasChanged = false
                val now = System.currentTimeMillis()
                if (isOverwatchEnabled && !isCardVisible && now >= overwatchCooldownUntil && !isUserOnHomeScreen()) {
                    val captureManager = screenCaptureManager
                    if (captureManager != null) {
                        val bitmap = captureManager.captureScreen()
                        if (bitmap != null) {
                            hasChanged = hasScreenChangedSignificantly(bitmap)
                            if (hasChanged) {
                                Log.d("FridayOverwatch", "Screen content changed. Running Sentry AI scan on $foregroundPkg...")
                                val result = geminiClient.analyzeOverwatchScreen(bitmap, foregroundPkg ?: "")
                                result.onSuccess { response ->
                                    val trimmed = response.trim()
                                    Log.i("FridayOverwatch", "AI Sentry remark: \"$trimmed\"")
                                    if (trimmed.isNotBlank() &&
                                        !trimmed.equals("PASS", ignoreCase = true) &&
                                        !trimmed.startsWith("PASS", ignoreCase = true)
                                    ) {
                                        withContext(Dispatchers.Main) {
                                            triggerOverwatchAlert(trimmed)
                                        }
                                        // Set cooldown of 22 seconds between autonomous remarks
                                        overwatchCooldownUntil = System.currentTimeMillis() + 22_000L
                                    } else {
                                        // If PASS, check again in 8 seconds
                                        overwatchCooldownUntil = System.currentTimeMillis() + 8_000L
                                    }
                                }.onFailure { err ->
                                    Log.w("FridayOverwatch", "AI Sentry scan failed: ${err.message}")
                                }
                            }
                            bitmap.recycle()
                        }
                    } else {
                        Log.d("FridayOverwatch", "Waiting for screenCaptureManager to arm...")
                    }
                }

                // Adaptive Sleep: If screen is static, sleep longer to cool CPU and prevent heat!
                val sleepTime = when {
                    isInReelApp -> if (hasChanged) 3500L else 4500L
                    hasChanged -> 6000L
                    else -> 10000L // 10 seconds on static content!
                }
                delay(sleepTime)
            }
        }
    }

    fun onReelDetected(pkg: String) {
        if (!isDoomscrollEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastReelDetectedTimestamp < 1200L) {
            Log.d("FridayDoomscroll", "Reel counted too rapidly (${now - lastReelDetectedTimestamp}ms), debouncing duplicate.")
            return
        }
        lastReelDetectedTimestamp = now
        lastReelAppLeaveTime = 0L

        if (doomscrollReelCount >= 10) {
            doomscrollReelCount = 1
        } else {
            doomscrollReelCount++
        }
        Log.i("FridayDoomscroll", "Reel counted ($pkg): $doomscrollReelCount / 10")

        mainHandler.post {
            updateReelCounterUI(true)
            pulseBubbleGlow()

            if (doomscrollReelCount >= 10) {
                if (now >= doomscrollCooldownUntil) {
                    doomscrollCooldownUntil = now + 45_000L
                    triggerDoomscrollAlert()
                }
            } else {
                if (!isCardVisible) {
                    showWhisper("🎬 Reel #$doomscrollReelCount / 10", 2500L)
                }
            }
        }
    }

    private suspend fun processReelFrame(bitmap: Bitmap) {
        try {
            val currentFp = extractReelFingerprint(bitmap)
            val prevFp = lastReelFingerprint
            val now = System.currentTimeMillis()

            if (prevFp == null) {
                lastReelFingerprint = currentFp
                lastReelChangeTime = now
                hasCountedCurrentReel = false
                withContext(Dispatchers.Main) {
                    updateReelCounterUI(true)
                }
                return
            }

            val (overlayDiff, videoDiff) = compareFingerprints(prevFp, currentFp)
            val isSwipe = overlayDiff > 24.0f || (videoDiff > 42.0f && overlayDiff > 16.0f)

            if (isSwipe) {
                // User swiped to next reel
                lastReelFingerprint = currentFp
                lastReelChangeTime = now
                hasCountedCurrentReel = false
            } else {
                // User stayed on current reel
                val dwell = now - lastReelChangeTime
                if (dwell >= 3000L && !hasCountedCurrentReel) {
                    hasCountedCurrentReel = true
                    val pkg = getForegroundAppPackage() ?: "reel_app"
                    onReelDetected(pkg)
                }
            }
        } catch (e: Exception) {
            Log.e("FridayDoomscroll", "Error processing reel frame", e)
        }
    }

    private fun triggerDoomscrollAlert() {
        pulseBubbleGlow()
        showWhisper("⚠️ Boss, 10 reels complete! Focus wapas laane ka time hai.", 6500L)
        ttsManager.speak("Boss, 10 reels complete ho chuki hain. Dopamine levels peak par hain, aapke real goals ko aapke focus ki zaroorat hai... phone side me rakhiye.")
    }

    fun checkAndTriggerMorningGreeting(isManualTest: Boolean = false) {
        val prefs = getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("morning_greeting_enabled", true)
        if (!isEnabled && !isManualTest) {
            Log.d("FridayOverlayService", "Morning greeting is disabled in settings.")
            return
        }

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        val isMorning = hour in 5..11
        if (!isMorning && !isManualTest) {
            Log.d("FridayOverlayService", "Not morning hour ($hour:00). Skipping morning briefing.")
            return
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastGreetDate = prefs.getString("last_morning_greet_date", "")

        if (lastGreetDate == today && !isManualTest) {
            Log.d("FridayOverlayService", "Morning greeting already delivered today ($today).")
            return
        }

        if (!isManualTest) {
            prefs.edit().putString("last_morning_greet_date", today).apply()
        }

        val greetingText = when {
            isManualTest -> "Good morning, Sir! All systems initialized and online. Today's protocols are armed and ready. Let's make today productive."
            hour in 5..8 -> "Good morning, Sir! Early start today. All cybernetic systems are online and primed. Ready when you are."
            else -> "Good morning, Sir! All systems initialized and online. Hope you had a great sleep. Ready for today's mission."
        }

        Log.i("FridayOverlayService", "Triggering Morning Greeting for Sir: \"$greetingText\"")
        mainHandler.post {
            pulseBubbleGlow()
            showWhisper("🌅 Good Morning, Sir! All systems online.", 5500L)
            mainHandler.postDelayed({
                if (isRunning) {
                    ttsManager.speak(greetingText)
                }
            }, 600)
        }
    }

    private fun extractBossNameFromContext(context: String): String? {
        if (context.isBlank()) return null
        val nameMatch = Regex("""(?:name|naam|boss|called)\s*[:=-]\s*([a-zA-Z]+)""", RegexOption.IGNORE_CASE).find(context)
        return nameMatch?.groupValues?.get(1)?.trim()?.replaceFirstChar { it.uppercase() }
    }

    private fun updateReelCounterUI(isInReelApp: Boolean = false) {
        val layoutBadge = cardView?.findViewById<View>(R.id.layoutReelBadge)
        val tvBadge = cardView?.findViewById<TextView>(R.id.tvReelCounterBadge)
        if (isDoomscrollEnabled && (isInReelApp || doomscrollReelCount > 0)) {
            layoutBadge?.visibility = View.VISIBLE
            tvBadge?.text = "🎬 REELS: $doomscrollReelCount/10"
        } else {
            layoutBadge?.visibility = View.GONE
        }
    }

    private fun extractReelFingerprint(bitmap: Bitmap): ReelFingerprint {
        val width = bitmap.width
        val height = bitmap.height

        // 1. Video center region: x: 10%..90%, y: 15%..75%
        val videoCropX = (width * 0.10f).toInt()
        val videoCropY = (height * 0.15f).toInt()
        val videoCropW = ((width * 0.80f).toInt()).coerceAtLeast(16)
        val videoCropH = ((height * 0.60f).toInt()).coerceAtLeast(16)
        val videoCrop = Bitmap.createBitmap(bitmap, videoCropX, videoCropY, videoCropW, videoCropH)
        val videoThumb = Bitmap.createScaledBitmap(videoCrop, 16, 16, false)
        if (videoCrop != bitmap) videoCrop.recycle()

        val videoLum = IntArray(256)
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L

        var idx = 0
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val p = videoThumb.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                sumR += r
                sumG += g
                sumB += b
                videoLum[idx++] = (r * 299 + g * 587 + b * 114) / 1000
            }
        }
        videoThumb.recycle()

        // 2. Caption/Creator Overlay zone: x: 5%..75%, y: 72%..88%
        val overlayCropX = (width * 0.05f).toInt()
        val overlayCropY = (height * 0.72f).toInt()
        val overlayCropW = ((width * 0.70f).toInt()).coerceAtLeast(16)
        val overlayCropH = ((height * 0.16f).toInt()).coerceAtLeast(8)
        val overlayCrop = Bitmap.createBitmap(bitmap, overlayCropX, overlayCropY, overlayCropW, overlayCropH)
        val overlayThumb = Bitmap.createScaledBitmap(overlayCrop, 16, 8, false)
        if (overlayCrop != bitmap) overlayCrop.recycle()

        val overlayLum = IntArray(128)
        idx = 0
        for (y in 0 until 8) {
            for (x in 0 until 16) {
                val p = overlayThumb.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                overlayLum[idx++] = (r * 299 + g * 587 + b * 114) / 1000
            }
        }
        overlayThumb.recycle()

        return ReelFingerprint(
            videoLum = videoLum,
            overlayLum = overlayLum,
            avgR = (sumR / 256).toInt(),
            avgG = (sumG / 256).toInt(),
            avgB = (sumB / 256).toInt()
        )
    }

    private fun compareFingerprints(a: ReelFingerprint, b: ReelFingerprint): Pair<Float, Float> {
        var overlayDiffSum = 0
        for (i in a.overlayLum.indices) {
            overlayDiffSum += abs(a.overlayLum[i] - b.overlayLum[i])
        }
        val overlayMAD = overlayDiffSum.toFloat() / a.overlayLum.size

        var videoDiffSum = 0
        for (i in a.videoLum.indices) {
            videoDiffSum += abs(a.videoLum[i] - b.videoLum[i])
        }
        val videoMAD = videoDiffSum.toFloat() / a.videoLum.size

        return Pair(overlayMAD, videoMAD)
    }

    private fun getForegroundAppPackage(): String? {
        // Priority 1: Instant & 100% accurate Accessibility Service tracking (zero permission delay)
        FridayAccessibilityService.currentForegroundPackage?.let { pkg ->
            if (pkg.isNotBlank() && pkg != packageName && !pkg.startsWith("com.personal.friday")) {
                return pkg.lowercase()
            }
        }

        // Priority 2: Fallback to UsageStatsManager
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val time = System.currentTimeMillis()
            val stats = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 15,
                time
            )
            if (!stats.isNullOrEmpty()) {
                val recentApp = stats.maxByOrNull { it.lastTimeUsed }
                val pkg = recentApp?.packageName?.lowercase()
                if (pkg != null && pkg != packageName && !pkg.startsWith("com.personal.friday")) {
                    return pkg
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun triggerOverwatchAlert(remark: String) {
        // Option A: Glow Pulse Arc Reactor
        pulseBubbleGlow()
        // Option A: Pop up Whisper Bubble
        showWhisper(remark)
        // Option B: Speak Remark out loud
        ttsManager.speak(remark)
    }

    private fun pulseBubbleGlow() {
        bubbleView?.findViewById<FridayBubbleReactorView>(R.id.bubbleReactor)?.triggerPulse()
        val glow = bubbleView?.findViewById<View>(R.id.bubbleGlow) ?: return
        glow.animate()
            .scaleX(1.45f)
            .scaleY(1.45f)
            .alpha(1.0f)
            .setDuration(350)
            .withEndAction {
                glow.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(0.35f)
                    .setDuration(350)
                    .withEndAction {
                        glow.animate()
                            .scaleX(1.35f)
                            .scaleY(1.35f)
                            .alpha(0.9f)
                            .setDuration(300)
                            .withEndAction {
                                glow.animate()
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .alpha(0.35f)
                                    .setDuration(300)
                                    .start()
                            }.start()
                    }.start()
            }.start()
    }

    private fun showWhisper(remark: String, durationMs: Long = 6500L) {
        val tvWhisper = whisperView?.findViewById<TextView>(R.id.tvWhisperContent)
        tvWhisper?.text = remark

        val density = resources.displayMetrics.density
        val whisperWidthApprox = (240 * density).toInt()
        val middle = screenSize.x / 2

        if (bubbleParams.x >= middle) {
            whisperParams.x = (bubbleParams.x - whisperWidthApprox).coerceAtLeast(16)
        } else {
            whisperParams.x = bubbleParams.x + (46 * density).toInt()
        }
        whisperParams.y = (bubbleParams.y - (10 * density).toInt()).coerceAtLeast(50)

        windowManager.updateViewLayout(whisperView, whisperParams)

        whisperView?.visibility = View.VISIBLE
        whisperView?.alpha = 0f
        whisperView?.scaleX = 0.85f
        whisperView?.scaleY = 0.85f
        whisperView?.animate()
            ?.alpha(1f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setDuration(240)
            ?.setInterpolator(OvershootInterpolator(1.2f))
            ?.start()

        isWhisperVisible = true

        mainHandler.removeCallbacks(whisperDismissRunnable)
        mainHandler.postDelayed(whisperDismissRunnable, durationMs)
    }

    private fun hideWhisper() {
        if (!isWhisperVisible) return
        isWhisperVisible = false
        mainHandler.removeCallbacks(whisperDismissRunnable)
        whisperView?.animate()
            ?.alpha(0f)
            ?.scaleX(0.85f)
            ?.scaleY(0.85f)
            ?.setDuration(180)
            ?.withEndAction {
                whisperView?.visibility = View.GONE
            }
            ?.start()
    }

    private fun isUserOnHomeScreen(): Boolean {
        val curr = FridayAccessibilityService.currentForegroundPackage?.lowercase()
        if (curr != null) {
            if (curr == packageName.lowercase() || curr.startsWith("com.personal.friday")) {
                return true // Exempt Friday's own UI from Sentry auto-triggers
            }
            if (curr.contains("launcher") || curr.contains("home") || curr.contains("quickstep")) {
                return true
            }
        }
        val pkg = getForegroundAppPackage() ?: return false
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val defaultLauncher = resolveInfo?.activityInfo?.packageName?.lowercase() ?: "com.android.launcher"
        return pkg == defaultLauncher || pkg.contains("launcher") || pkg.contains("home") || pkg.contains("quickstep")
    }

    private fun hasScreenChangedSignificantly(bitmap: Bitmap): Boolean {
        try {
            val thumb = Bitmap.createScaledBitmap(bitmap, 16, 16, false)
            var hash = 0L
            var pixelSum = 0L
            for (x in 0 until 16) {
                for (y in 0 until 16) {
                    val pixel = thumb.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    val lum = (r * 299 + g * 587 + b * 114) / 1000
                    hash = 31 * hash + lum
                    pixelSum += lum
                }
            }
            thumb.recycle()

            val delta = abs(pixelSum - lastPixelSum)
            val isDiff = delta > 120 || hash != lastScreenHash
            lastScreenHash = hash
            lastPixelSum = pixelSum
            return isDiff
        } catch (e: Exception) {
            return false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        bubbleView?.findViewById<FridayBubbleReactorView>(R.id.bubbleReactor)?.stopAnimationLoop()
        overwatchJob?.cancel()
        mainHandler.removeCallbacks(whisperDismissRunnable)
        serviceScope.cancel()

        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}

        hotwordSpotter.stop()
        ttsManager.release()
        speechManager.release()
        screenCaptureManager?.release()

        bubbleView?.let { windowManager.removeView(it) }
        cardView?.let { windowManager.removeView(it) }
        whisperView?.let { windowManager.removeView(it) }

        instance = null
        hasScreenCapture = false
        try {
            com.personal.friday.widget.FridayHudWidgetProvider.updateAllWidgets(this)
        } catch (_: Exception) {}
    }

    companion object {
        var isRunning = false
        @Volatile
        var instance: FridayOverlayService? = null
            private set
        @Volatile
        var hasScreenCapture = false
            internal set

        const val CHANNEL_ID = "friday_overlay_channel"
        const val NOTIFICATION_ID = 4201
        const val ACTION_START_PROJECTION = "com.personal.friday.START_PROJECTION"
        const val ACTION_UPDATE_OVERWATCH = "com.personal.friday.ACTION_UPDATE_OVERWATCH"
        const val ACTION_UPDATE_DOOMSCROLL = "com.personal.friday.ACTION_UPDATE_DOOMSCROLL"
        const val ACTION_UPDATE_WAKEWORD = "com.personal.friday.ACTION_UPDATE_WAKEWORD"
        const val ACTION_TRIGGER_LISTENING = "com.personal.friday.ACTION_TRIGGER_LISTENING"
        const val ACTION_TRIGGER_MORNING_GREETING = "com.personal.friday.ACTION_TRIGGER_MORNING_GREETING"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"
        const val EXTRA_OVERWATCH_ENABLED = "extra_overwatch_enabled"
        const val EXTRA_DOOMSCROLL_ENABLED = "extra_doomscroll_enabled"
        const val EXTRA_WAKEWORD_ENABLED = "extra_wakeword_enabled"

        private val REEL_PACKAGES = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.snapchat.android"
        )
    }
}

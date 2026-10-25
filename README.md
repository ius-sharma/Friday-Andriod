<div align="center">

# ⚡ F.R.I.D.A.Y. (Android)
### *Next-Gen Tactical AI Screen Companion & Personal On-Device Assistant*

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-3DDC84.svg?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target_SDK-34-brightgreen.svg?style=for-the-badge&logo=android)](https://developer.android.com)
[![Gemini AI](https://img.shields.io/badge/Brain-Google_Gemini_Multimodal-4285F4.svg?style=for-the-badge&logo=google&logoColor=white)](https://ai.google.dev/)
[![Sherpa ONNX](https://img.shields.io/badge/Hotword-Sherpa--ONNX_Zipformer-FF6F00.svg?style=for-the-badge)](https://github.com/k2-fsa/sherpa-onnx)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

<br/>

> **"Always at your service, Boss."**  
> F.R.I.D.A.Y. is an intelligent, reactive screen companion built for Android. Combining **100% on-device wake-word detection** with **multimodal screen vision**, real-time floating holographic HUDs, accessibility automation, and an interactive cyberpunk home screen widget.

</div>

---

## 🌟 Key Highlights

### 🎙️ 1. Ultra-Low-Latency On-Device Wake Word
- Powered by embedded **Sherpa-ONNX (Zipformer)** engine.
- Instant, offline wake-word spotting (`"Friday"`) directly on-device with zero audio transmitted to the cloud until triggered.
- Low power consumption running as a continuous background foreground service.

### 👁️ 2. Autonomous Screen Overwatch & Multimodal Vision
- Integrates Android **MediaProjection API** for real-time visual screen capture.
- Analyzes on-screen context using **Google Gemini Flash / Pro Multimodal Vision** models.
- **Doomscroll Sentry**: Detects endless reel/short scrolling on social media apps and delivers witty, humorous nudges to keep you focused.
- **Workflow Companion**: Offers tactical suggestions, debugging insights, or crisp summaries when learning, coding, or reading.
- Converses naturally in expressive **Hinglish** (Hindi + English blend) with witty, sharp banter.

### 🔮 3. Stark Nanotech Particle HUD & Living Arc Reactor Bubble
- **Kinetic Arc Reactor Overlay**: Custom `FridayBubbleReactorView` with multi-ring rotation, breathing idle state, and live audio waveform reactive pulse when speaking.
- **Holographic Floating Bubble**: Draggable, persistent floating orb with dynamic glow effects for idle, listening, processing, and speaking states.
- **Nanotech Particle Canvas**: Custom Android `Canvas` rendering live particle physics and smooth audio waveform animations.
- **Interactive Whisper & Response Cards**: Glassmorphism cards presenting concise AI responses and quick action shortcuts without blocking your screen.

### ⚡ 4. Deep Device Automation & Natural Action Engine
- Integrated with `FridayAccessibilityService` and package manager queries.
- **Natural Hindi/Hinglish Alarms & Timers**: Sets system alarms and timers with human phrasing (`"saadhe 7 baje ka alarm"`, `"sawa 8 baje"`, `"paune 10"`, `"15 minute ka timer"`).
- **Direct App & Media Actions**:
  - Open applications dynamically (`[ACTION: OPEN_APP <app>]`)
  - Direct media playback (`[ACTION: PLAY_YOUTUBE <query>]`)
  - Smart field interaction, text typing, and navigation.

### 🌅 5. Morning Briefing & Local Profile Sandbox
- **Autonomous Morning Greeting**: Plays a smart, motivating tactical briefing on first device unlock (between 5 AM - 12 PM).
- **Local Persona & Context Sandbox**: Personal user profile is stored 100% locally in private device storage, empowering Gemini with custom context while strictly respecting user privacy.

### 🌤️ 6. Futuristic Cyber-HUD Home Screen Widget & Bento Dashboard
- **Stark Bento Grid Dashboard**: Complete main console with live sentry states, doomscroll progress bars, system vitals (RAM & Battery), and instant action chips.
- **Cyber-HUD 4x2 Home Screen Widget**:
  - Real-time weather data fetched from **Open-Meteo**.
  - **Dynamic Bezier Temperature Wave Graph**: Smooth hourly forecast curve rendered directly on a remote canvas.
  - Quick-action microphone button to summon Friday with a single tap.
  - Background periodic sync via Android `WorkManager`.

### 📞 7. AI Telephony & Autonomous Call Proxy
- **Incoming Caller Identification**: Real-time caller announcement via dialer hooks, contact lookup, and accessibility inspection.
- **Autonomous Call Proxy ("Friday Baat Karo")**: F.R.I.D.A.Y. can answer incoming phone calls on your behalf, route audio over loudspeaker, and hold a conversational dialogue with callers (delivery drivers, colleagues, banks, telemarketers) using Gemini Multimodal reasoning.
- **Post-Call Briefing**: Once the call hangs up, Friday provides an instant spoken and whisper summary of what the caller needed.
- **Direct Voice Calling**: Dial contacts or phone numbers seamlessly (`"call Rahul"`, `"phone lagao Papa ko"`, `"call uthao"`, `"call cut karo"`).

### 🛡️ 8. 24/7 Watchdog & Boot Resilience
- **Persistent Unlock Watchdog**: `FridayUnlockReceiver` registered at system level to trigger morning greetings and resurrect Friday services if killed by aggressive OEM battery cleaners.
- **Overnight Unrestricted Execution**: Optimized for zero-sleep standby to ensure morning briefings fire without fail.

---

## 🏗️ System Architecture

```
                                  ┌──────────────────────────────┐
                                  │      User Voice & Screen     │
                                  └──────────────┬───────────────┘
                                                 │
                         ┌───────────────────────┴───────────────────────┐
                         ▼                                               ▼
               [ HotwordSpotter ]                            [ ScreenCaptureManager ]
       Sherpa-ONNX (Offline Zipformer)                     MediaProjection Virtual Display
                         │                                               │
                         ▼                                               ▼
              [ FridayOverlayService ] ◄──────────────────────► [ FridayAccessibilityService ]
              Floating Particle HUD Orb                               UI Click & Input Action
                         │
                         ▼
               [ GeminiApiClient ]
          Multi-Model Multimodal Engine
       (gemini-2.5-flash / flash-lite / pro)
                         │
                         ▼
        ┌───────────────────────────────────┐
        │       Output & Interaction        │
        ├─────────────────┬─────────────────┤
        │  TtsManager     │  HUD Bubble     │
        │  (Voice Output) │  Response Cards │
        └─────────────────┴─────────────────┘
```

---

## 📁 Repository Structure

```
Friday/
├── app/
│   ├── libs/
│   │   └── sherpa-onnx-1.13.8.aar          # Embedded ONNX runtime for ASR & KWS
│   ├── src/main/
│   │   ├── AndroidManifest.xml             # Permissions & service declarations
│   │   ├── assets/kws/                     # Quantized ONNX acoustic models
│   │   │   ├── encoder.onnx                # Zipformer acoustic encoder
│   │   │   ├── decoder.onnx                # Hotword decoder
│   │   │   ├── joiner.onnx                 # Joint network
│   │   │   ├── keywords.txt                # Wake-word configurations
│   │   │   └── tokens.txt                  # Vocabulary tokens
│   │   ├── java/com/personal/friday/
│   │   │   ├── MainActivity.kt             # Stark UI dashboard & telemetry control
│   │   │   ├── SettingsActivity.kt         # Secure API configuration & permissions
│   │   │   ├── FridayOverlayService.kt     # Core floating HUD & state machine
│   │   │   ├── FridayAccessibilityService.kt # Assistive device automation
│   │   │   ├── GeminiApiClient.kt          # Resilient multimodal AI integration
│   │   │   ├── HotwordSpotter.kt           # Real-time microphone audio streamer
│   │   │   ├── ScreenCaptureManager.kt     # High-speed bitmap frame grabber
│   │   │   ├── NanotechParticleView.kt     # Particle physics engine
│   │   │   ├── ParticleOrbView.kt          # Animated glowing HUD orb
│   │   │   ├── SpeechManager.kt            # Speech recognition coordinator
│   │   │   ├── TtsManager.kt               # Voice synthesis engine
│   │   │   ├── actions/
│   │   │   │   └── FridayActionExecutor.kt # Intent routing & action handlers
│   │   │   └── widget/
│   │   │       ├── FridayHudWidgetProvider.kt # Cyberpunk widget lifecycle
│   │   │       ├── FridayWaveChartDrawer.kt   # Bezier spline weather renderer
│   │   │       ├── FridayWeatherData.kt       # Weather data models
│   │   │       ├── FridayWeatherRepository.kt # Open-Meteo REST repository
│   │   │       └── FridayWidgetSyncWorker.kt  # WorkManager sync scheduler
│   │   └── res/                            # Layouts, vector drawables & cyberpunk themes
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🔒 Privacy & Security Blueprint

F.R.I.D.A.Y. was engineered with strict user privacy as a foundational principle:

1. **Zero Audio Leaks**: Audio recorded by the wake-word listener is processed locally in RAM via Sherpa-ONNX and never sent to external servers.
2. **Encrypted Local Storage**: Your Gemini API Key is stored exclusively within private, sandboxed `SharedPreferences` on your device.
3. **Opt-in Screen Streaming**: The screen projection is only active when explicitly authorized through Android's system `MediaProjection` prompt.
4. **No Third-Party Trackers**: No third-party tracking, advertising SDKs, or external telemetry libraries are included.

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio**: Hedgehog (2023.1.1) or newer
- **JDK**: Version 17
- **Target Platform**: Android 8.0 (API 26) or higher
- **Architecture**: `arm64-v8a` (optimized for Sherpa-ONNX JNI binaries)
- **Gemini API Key**: Obtainable from [Google AI Studio](https://aistudio.google.com/)

### Build & Run
1. **Clone the repository**:
   ```bash
   git clone https://github.com/ius-sharma/Friday-Andriod.git
   cd Friday-Andriod
   ```

2. **Open in Android Studio**:
   - Open Android Studio and select **Open** -> Select the project root folder.
   - Allow Gradle to sync dependencies.

3. **Deploy to Device**:
   - Connect your Android device via USB with USB Debugging enabled.
   - Run the project (`Shift + F10` or click **Run 'app'**).

4. **Initial Configuration**:
   - Open **Settings** within Friday.
   - Enter your **Google Gemini API Key**.
   - Grant required permissions:
     - 📌 **Display over other apps** (Draw overlay HUD)
     - 🎤 **Microphone** (Wake word detection)
     - 📱 **Screen Capture** (Multimodal screen analysis)
     - ♿ **Accessibility Service** (Device automation & app launching)

---

## ⚙️ Required Permissions

| Permission | Purpose |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Display the interactive floating HUD and response overlays. |
| `RECORD_AUDIO` | Real-time wake-word spotter on-device. |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Visual screen analysis for contextual assistant capabilities. |
| `BIND_ACCESSIBILITY_SERVICE` | Executing automation actions (opening apps, smart taps, in-call speaker control). |
| `ACCESS_COARSE_LOCATION` | Fetching local weather data for the home screen widget. |
| `PACKAGE_USAGE_STATS` | Detecting foreground apps for situational doomscrolling nudges. |
| `CALL_PHONE` | Initiating voice calls on your command. |
| `ANSWER_PHONE_CALLS` & `READ_PHONE_STATE` | Screen incoming calls and manage hands-free call answering / proxy. |
| `READ_CONTACTS` & `READ_CALL_LOG` | Resolving contact names for incoming calls and voice dialing. |
| `RECEIVE_BOOT_COMPLETED` | Auto-resurrecting watchdog service upon device restart. |

---

## 🤝 Contributing

Contributions, feedback, and suggestions are warmly welcomed!
1. Fork the Project.
2. Create your Feature Branch (`git checkout -b feature/TacticalUpgrade`).
3. Commit your Changes (`git commit -m 'feat: Add tactical upgrade'`).
4. Push to the Branch (`git push origin feature/TacticalUpgrade`).
5. Open a Pull Request.

---

## 📄 License

Distributed under the **MIT License**. See `LICENSE` for more information.

<div align="center">
<sub>Designed and Developed by <b>Ayush Sharma</b> • Inspired by Stark Industries Tactical OS</sub>
</div>

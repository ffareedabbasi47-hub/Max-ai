# 🚀 MAX — PRODUCTION-GRADE ANDROID AI AGENT

**MAX** is a production-ready, futuristic Android AI Assistant inspired by JARVIS. Built natively with **Kotlin**, **Jetpack Compose (Material Design 3)**, **Coroutines Flow**, **Room DB**, and a **Multi-Provider AI Brain Architecture**.

---

## 🛠️ AUDIT & MASTER BUG FIXES

| Category | Problem Identified | Fix Implemented |
| :--- | :--- | :--- |
| **INSTALLATION** | APK failed installation on real devices due to missing signing scheme and SDK target misalignment. | Configured `v1` and `v2` APK Signing Schemes in `build.gradle.kts`, targeted Android 15 (`targetSdk = 35`), set `versionCode = 2`, and added optional hardware flags (`microphone`, `telephony` required=false). |
| **VOICE ENGINE** | Basic TTS without wake-word response or custom voice parameters. | Implemented wake word engine listening for `"Max"` or `"Hey Max"`. Responds with `"Yes Boss? Boliyen, main sun raha hoon!"`. |
| **AI BRAIN** | Single API key dependency prone to rate limiting. | Implemented `MultiBrainManager` supporting 5 API Key Slots across **Gemini 2.5 Flash**, **OpenAI GPT-4o Mini**, **Claude 3.5 Sonnet**, and a local offline Hinglish fallback parser. |
| **SCREEN VISION** | Static UI without real screen analysis or accessibility interaction. | Added `MaxAccessibilityService` and `ScreenAssistScreen` to scan visible text elements, describe screen layout, read text aloud, and perform hands-free button clicks. |
| **PERMISSIONS** | Missing runtime permission requests for hardware features. | Integrated Compose runtime permission launcher for Microphone (`RECORD_AUDIO`), Calls (`CALL_PHONE`), Contacts (`READ_CONTACTS`), and Notifications (`POST_NOTIFICATIONS`). |
| **CI/CD** | Missing unit test execution and Release APK artifact export. | Updated `.github/workflows/android.yml` to run unit tests, build both `assembleRelease` & `assembleDebug`, and export installable APK artifacts. |

---

## 🧠 MULTI-PROVIDER AI BRAIN ARCHITECTURE

```
User Voice / Text Command
           │
           ▼
 Wake Word ("Max" / "Hey Max")
           │
           ▼
   MultiBrainManager
   ├── Priority 1: Google Gemini 2.5 Flash (5 API Key Slots)
   ├── Priority 2: OpenAI GPT-4o Mini
   ├── Priority 3: Anthropic Claude 3.5 Sonnet
   └── Priority 4: Offline Local Hinglish Command Parser
           │
           ▼
    Command Router & Tool Dispatcher
   ├── Open App / System Control
   ├── Call Secretary / Phone Dialing
   ├── WhatsApp / Email Drafting
   ├── File Creation & Storage Vault
   ├── Web Search & Live News
   └── Vision / Screen Assist Engine
```

---

## 🤖 MAX IDENTITY & PERSONALITY

- **Name**: MAX
- **Wake Word**: `"Max"` or `"Hey Max"`
- **Activation Response**: `"Yes Boss? Boliyen, main sun raha hoon!"` or `"Yes, Boss."`
- **Execution Response**: `"On it, Boss!"` or `"Arrey Wah Boss! YouTube khol raha hoon abhi."`
- **Languages**: Natural mix of Hindi, English, and Hinglish.

---

## 🛡️ PERMISSIONS & RATIONALE

- `android.permission.RECORD_AUDIO`: Voice command recognition & wake word detection.
- `android.permission.CALL_PHONE` & `READ_CONTACTS`: Phone dialing & contact lookup.
- `android.permission.FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MICROPHONE`: Background wake-word listening service.
- `android.permission.BIND_ACCESSIBILITY_SERVICE`: Screen reading, UI parsing, and guided element clicking.
- `android.permission.POST_NOTIFICATIONS`: Ongoing status notifications for background listening.

---

## 🔑 ENVIRONMENT VARIABLES & API CONFIGURATION

Copy `.env.example` to `.env` or configure via **Secrets Panel** in AI Studio:

```env
# Google Gemini API Key
GEMINI_API_KEY=YOUR_GEMINI_API_KEY
```

For custom provider slots, enter API keys directly in the **MAX CONFIG** Settings screen in the app.

---

## 🔨 BUILDING THE APK

### Local Command Line Build
```bash
# Clean project
gradle clean

# Run Unit & Screenshot Tests
gradle test

# Build Installable Release APK
gradle assembleRelease

# Build Debug APK
gradle assembleDebug
```

Output APK Location:
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/apk/debug/app-debug.apk`

---

## 📲 APK INSTALLATION INSTRUCTIONS (REAL PHONE)

1. **Uninstall Any Previous Build**: Uninstall older versions of the app from your device to prevent signature conflicts.
2. **Download APK**: Transfer `app-release.apk` or `app-debug.apk` to your phone.
3. **Allow Unknown Apps**: If prompted, enable *"Install Unknown Apps"* for your browser or file manager in phone Settings.
4. **Google Play Protect**: If a warning appears (*"Blocked by Play Protect"*), tap **More details** ➔ **Install anyway**.

---

## ⚠️ KNOWN ANDROID PLATFORM LIMITATIONS & ALTERNATIVES

1. **Direct System Shutdown**: Android blocks standard non-root applications from powering off the device. *Alternative*: MAX opens the system Power Interface.
2. **Silent Direct WhatsApp Dispatch**: Security restrictions require user confirmation when opening external chat intents. *Alternative*: MAX drafts the exact text and opens the chat dispatch window ready for sending.

---

## 📂 PROJECT STRUCTURE

```
app/src/main/java/com/example/
├── MainActivity.kt                      # Edge-to-edge Compose entry point & permissions
├── system/
│   ├── MaxWakeService.kt               # Foreground background wake word service
│   ├── MaxAccessibilityService.kt      # Screen reader & UI element finder
│   └── SystemControlManager.kt         # Wi-Fi, Bluetooth, Apps, Telemetry
├── data/
│   ├── api/
│   │   ├── MultiBrainManager.kt        # Multi-provider fallback orchestration
│   │   ├── GeminiBrain.kt              # Backward compatible brain alias
│   │   └── providers/
│   │       ├── AIProvider.kt           # Common Provider Interface
│   │       ├── GeminiProvider.kt       # Google Gemini 2.5 Flash implementation
│   │       ├── OpenAIProvider.kt       # OpenAI GPT-4o implementation
│   │       └── ClaudeProvider.kt       # Anthropic Claude 3.5 implementation
│   └── db/
│       ├── MaxDatabase.kt              # Room Local Database
│       └── MaxDao.kt                   # Command logs, Notes & Auto-reply DAOs
├── voice/
│   └── MaxVoiceEngine.kt               # Speech-to-Text & Text-to-Speech Engine
└── ui/
    ├── components/
    │   ├── ArcReactorView.kt           # Futuristic central animated HUD core
    │   ├── HudHeader.kt                # Status bar padded header
    │   ├── HudBottomNav.kt             # Navigation bar with Vision tab
    │   └── SystemStatsHud.kt           # Real-time RAM, CPU, Battery HUD
    └── screens/
        ├── HomeScreen.kt               # Main HUD Dashboard
        ├── ScreenAssistScreen.kt       # Vision & Screen Assist HUD
        ├── SystemControlScreen.kt      # Phone control panel
        ├── CommunicationScreen.kt      # WhatsApp & Email dispatch
        ├── FileManagerScreen.kt        # Document & Notes Vault
        ├── CallSecretaryScreen.kt      # Call Secretary & Keypad
        └── SettingsScreen.kt           # Multi-slot API Key Config
```

# Narendra Phone — Native Android Dialer, Call Recorder & Call Settings

**Developer:** Narendra  
**Package ID:** `com.narendra.callrecorder`  
**UI Stack:** Kotlin + Jetpack Compose + Material 3 (Blue & Black Theme)  
**Compatibility:** Android 8.0 (API 26) – Android 15+ (OPPO, Vivo, Xiaomi / MIUI / HyperOS, Realme, Samsung, Pixel)

## Core Features

### 1. Real Phone Dialer & In-Call Screen (`InCallService`)
- **Full Keypad & T9 Search:** Responsive 0–9, `*`, `#` keypad with DTMF tones, haptic feedback, long-press `0` for `+`, and instant T9 contact matching.
- **Default Phone App (`ROLE_DIALER`):** Registers as a legitimate Android Phone app via `DialerInCallService` and `InCallActivity`.
- **In-Call Controls:** Answer, Decline, End Call, Mute, Speakerphone, Hold, Swap, Merge Conference, and live DTMF keypad.
- **Call Pickup Auto & Manual Recording:** Automatically starts recording when a call transitions to `Call.STATE_ACTIVE` (picked up) when Auto-Record is enabled, plus live Record / Pause / Resume / Stop buttons directly on the In-Call dialer screen.

### 2. Call History & Contacts
- **Real Call Log (`CallLog.Calls`):** Displays incoming, outgoing, and missed calls, filters by call type, searches history, lets you redial with 1 tap, delete individual logs, clear all call history, and jump directly to linked call recordings.
- **Contacts (`ContactsContract`):** Searchable phonebook with Favorites filter and 1-tap calling.

### 3. Honest Two-Way Call Recording & Formats
- **Formats & Quality:** M4A (MPEG-4 AAC), AAC (ADTS), and uncompressed 16-bit PCM WAV (`16 kHz / 64 kbps`, `44.1 kHz / 128 kbps`, `48 kHz / 192 kbps`).
- **Transparent Audio Capability Check:** Only uses `MediaRecorder.AudioSource.VOICE_CALL` if `CAPTURE_AUDIO_OUTPUT` is actually permitted by the OS/OEM; otherwise safely falls back to `VOICE_COMMUNICATION` / `MIC` / `VOICE_RECOGNITION` with a clear status indicator and 1-tap Speakerphone toggle on `InCallActivity`.
- **No Hidden APIs or Accessibility Abuse:** Does not misuse Accessibility Services or unauthorized hooks.

### 4. Carrier Call Settings (Call Forwarding & Call Waiting)
- **Call Waiting:** Real GSM MMI / USSD actions to Enable (`*43#`), Disable (`#43#`), and Check Status (`*#43#`).
- **Call Forwarding:** Configure Always (`*21*`), When Busy (`*67*`), When Unanswered (`*61*`), When Unreachable (`*62*`), Cancel All (`##002#`), plus quick access to system SIM call settings.

### 5. OPPO, Vivo, Xiaomi & Privacy Controls
- **OEM Compatibility Shortcuts:** Direct links to Default Phone App role, Battery Optimization exclusion, and OPPO/ColorOS, Vivo/FuntouchOS, Xiaomi/MIUI/HyperOS Auto-Start settings.
- **Privacy & Backup:** Optional 4-digit PIN lock, Fingerprint/Biometric lock, user-consented local ZIP backup & restore, and full Reset All App Data control.

## Building & CI
- **Local Test:** `gradle :app:testDebugUnitTest`
- **Debug APK:** `gradle :app:assembleDebug`
- **GitHub Actions:** `.github/workflows/main.yml` builds the Debug APK with JDK 17 + Android SDK 36.1, runs Robolectric/JUnit tests, and uploads the APK artifact.

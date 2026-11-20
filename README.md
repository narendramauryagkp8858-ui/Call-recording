# Narendra Call Recorder

**Developer:** Narendra  
**Android Package Name (`applicationId`):** `com.narendra.callrecorder`  
**Platform:** Android (minSdk 24 / Android 7.0+ · targetSdk 36 / Android 15+)  
**Stack:** Kotlin · Jetpack Compose · Material 3 · Room · DataStore · Foreground Microphone Service · BiometricPrompt

---

## Features

1. **Automatic & Manual Call Recording**
   - Monitors incoming (`RINGING` / `OFFHOOK`) and outgoing calls via `CallStateManager` & `CallReceiver`.
   - Configurable rules: **Record All Calls**, **Selected Contacts Only**, or **Exclude Selected Contacts**.
   - Separate toggles for incoming and outgoing calls, plus instant manual studio recording with live animated waveform, Pause/Resume, and foreground notification controls.
   - Restores call-monitor readiness after device reboot (`BOOT_COMPLETED`) and app updates (`MY_PACKAGE_REPLACED`) within Android's background execution limits.

2. **Honest Two-Way Compatibility & Legal Consent**
   - Checks `CAPTURE_AUDIO_OUTPUT` and Android API level to accurately report whether true two-way cellular line capture (`VOICE_CALL`) is permitted by the device OS/ROM.
   - On standard Android 10–15+ devices where third-party apps are blocked from capturing remote cellular audio, the app **clearly warns the user** and records the local microphone stream (`VOICE_COMMUNICATION` / `MIC`) without faking two-way support.
   - Includes first-launch and on-demand recording consent & legal compliance disclosure.
   - Does **not** misuse `AccessibilityService`, hidden APIs, or broad storage permissions.

3. **Recording Dashboard & Library Manager**
   - Live metrics: Total recordings, Today's recordings, Incoming calls, Outgoing calls, and Storage usage.
   - Full library search (by contact, number, title, format), call-direction filter chips, and sorting (Newest, Oldest, Longest Duration, Contact A–Z).
   - Built-in player (`AudioPlayerManager`) with play, pause, resume, and scrub seekbar, plus Rename, Share (`FileProvider`), and Delete actions.

4. **Formats, Security & User-Consented Backup**
   - Supports **M4A** (MPEG-4 AAC), **AAC** (ADTS), and **WAV** (genuine 16-bit PCM via `AudioRecord` with RIFF header patching).
   - Quality presets: Standard (16 kHz / 64 kbps), High (44.1 kHz / 128 kbps), Studio (48 kHz / 192 kbps).
   - Optional 4-digit PIN lock (salted SHA-256) and Fingerprint / Biometric lock (`BiometricPrompt`).
   - Dark mode (Blue & Black Obsidian) and Light mode (Cool Cobalt) themes.
   - Export and restore local ZIP backups via Storage Access Framework (SAF) **only with explicit user consent**.

---

## Project Structure

- `.github/workflows/main.yml` — Automated CI build & test workflow
- `app/src/main/java/com/example/MainActivity.kt` — App shell, adaptive navigation, permissions, SAF backup, biometrics
- `app/src/main/java/com/example/audio/RecordingManager.kt` — M4A/AAC/WAV recording coordinator & two-way compatibility detector
- `app/src/main/java/com/example/audio/WavAudioRecorder.kt` — `AudioRecord` 16-bit PCM WAV engine
- `app/src/main/java/com/example/audio/AudioPlayerManager.kt` — `MediaPlayer` playback controller
- `app/src/main/java/com/example/call/CallStateManager.kt` — Telephony state & direction coordinator
- `app/src/main/java/com/example/call/CallReceiver.kt` — BroadcastReceiver for calls and boot restoration
- `app/src/main/java/com/example/service/RecordingService.kt` — `foregroundServiceType="microphone"` service
- `app/src/main/java/com/example/data/` — Room entities, DAOs, `AppDatabase`, `RecordingRepository`, `SettingsDataStore`, `BackupManager`
- `app/src/main/java/com/example/ui/` — `RecordingViewModel`, `HomeScreen`, `RecordingsScreen`, `SettingsScreen`, dialogs, and Blue & Black theme

---

## Build & APK Generation Instructions

### Option 1: Build APK Directly on Mobile via GitHub Actions (No PC Required)
1. Push or upload this entire project repository to GitHub (ensure `.github/workflows/main.yml` is included).
2. Open your repository in your mobile browser or GitHub mobile app and tap the **Actions** tab.
3. Select **Build Narendra Call Recorder APK** and tap **Run workflow** (or push a commit to `main`).
4. Wait ~2–4 minutes for `Run Unit & Robolectric Tests` and `Build Debug APK` to complete.
5. Scroll down to **Artifacts** and tap **`NarendraCallRecorder-debug-apk`** to download the ZIP containing `app-debug.apk`.
6. Extract the `.apk` file on your Android phone and install it.

### Option 2: Build in Android Studio (Desktop)
1. Open the root project folder in **Android Studio (Ladybug / Meerkat or newer)** with **JDK 17**.
2. In Android Studio **Settings → Languages & Frameworks → Android SDK**, ensure **Android SDK Platform 36** (and Command-line Tools) is installed.
3. Let Gradle sync (`Gradle 9.3.1`).
4. Run unit tests: `gradle testDebugUnitTest` (or via Android Studio Gradle panel).
5. Generate Debug APK: **Build → Build Bundle(s) / APK(s) → Build APK(s)** (output at `app/build/outputs/apk/debug/app-debug.apk`).

### Troubleshooting "Set up Android SDK" Errors
- **GitHub Actions**: `.github/workflows/main.yml` uses `android-actions/setup-android@v4` (replacing legacy `@v3` which can fail on newer `ubuntu-latest` runners) and automatically accepts SDK licenses so AGP 9.1.1 can download `compileSdk 36.1`.
- **Android Studio / Local CLI**: If Gradle reports SDK not found, install Android SDK via Android Studio's SDK Manager and ensure `ANDROID_HOME` points to your SDK folder.

---

## Device Compatibility Testing Instructions

1. **Permissions Check**: Open the app → tap **Compatibility Report** on the Dashboard → verify `RECORD_AUDIO`, `READ_PHONE_STATE`, `READ_CALL_LOG`, `READ_CONTACTS`, and `POST_NOTIFICATIONS` status.
2. **Two-Way Audio Verification**:
   - On standard Android 10–15 devices, verify the banner accurately displays **Microphone Mode Only (Android 10+ Restriction)** and that `CAPTURE_AUDIO_OUTPUT` is reported as **Missing** (never faked).
   - Place a test call with speakerphone **OFF** vs speakerphone **ON**: your microphone captures your voice cleanly, and speakerphone audio is captured when audible.
3. **Format & Playback Verification**:
   - Record short clips in **M4A**, **AAC**, and **WAV** from **Settings → Audio Format & Quality**.
   - Verify Pause/Resume works mid-recording, play each recording in the **Recordings** tab, test **Rename**, **Share**, and **Export ZIP / Restore ZIP**.
4. **Background Call Start Verification (Android 14+)**:
   - Enable **Automatic Call Recording** and lock the screen or place the app in background during a test call.
   - If the OS blocks silent background `microphone` foreground service startup (`ForegroundServiceStartNotAllowedException`), verify the high-priority notification **"Tap to Start Call Recording"** appears so you can start recording with one tap.

package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.AppSettings
import com.example.data.AudioFormatOption
import com.example.data.AudioSourceMode
import com.example.data.CallDirection
import com.example.data.CompatibilityStatus
import com.example.data.RecordingEntity
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface RecordingPhase {
    data object Idle : RecordingPhase
    data object Recording : RecordingPhase
    data object Paused : RecordingPhase
}

data class ActiveRecordingSession(
    val phase: RecordingPhase = RecordingPhase.Idle,
    val direction: CallDirection = CallDirection.MANUAL,
    val phoneNumber: String = "",
    val contactName: String = "",
    val format: AudioFormatOption = AudioFormatOption.M4A,
    val audioSourceMode: AudioSourceMode = AudioSourceMode.VOICE_COMMUNICATION,
    val isTwoWayVerified: Boolean = false,
    val elapsedMs: Long = 0L,
    val amplitude: Float = 0f,
    val warningBanner: String? = null
)

class RecordingManager(private val context: Context) {

    private val _session = MutableStateFlow(ActiveRecordingSession())
    val session: StateFlow<ActiveRecordingSession> = _session.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var wavRecorder: WavAudioRecorder? = null
    private var currentFile: File? = null
    private var currentSampleRate: Int = 44100
    private var currentBitRate: Int = 128000
    private var currentStartedAt: Long = 0L
    private var accumulatedActiveMs: Long = 0L
    private var lastResumedAtMs: Long = 0L
    private var tickerJob: Job? = null

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording(
        scope: CoroutineScope,
        outputFile: File,
        direction: CallDirection,
        phoneNumber: String,
        contactName: String,
        settings: AppSettings
    ): Result<ActiveRecordingSession> {
        if (_session.value.phase != RecordingPhase.Idle) {
            return Result.failure(IllegalStateException("A recording is already active."))
        }
        if (!hasRecordAudioPermission()) {
            return Result.failure(SecurityException("Microphone permission (RECORD_AUDIO) is required."))
        }

        val compat = detectCompatibility(context)
        val attemptVoiceCall = settings.preferTwoWayAttempt && compat.hasPrivilegedCaptureAudioOutput

        val (androidSource, resolvedMode, twoWayVerified, warningText) =
            resolveAudioSource(attemptVoiceCall, direction, compat)

        currentFile = outputFile
        currentSampleRate = settings.audioQuality.sampleRateHz
        currentBitRate = settings.audioQuality.bitRateBps
        currentStartedAt = System.currentTimeMillis()
        accumulatedActiveMs = 0L
        lastResumedAtMs = System.currentTimeMillis()

        return runCatching {
            when (settings.audioFormat) {
                AudioFormatOption.WAV -> {
                    val sources = listOf(
                        androidSource,
                        MediaRecorder.AudioSource.MIC,
                        MediaRecorder.AudioSource.VOICE_RECOGNITION
                    ).distinct()
                    var startedWav: WavAudioRecorder? = null
                    var lastErr: Throwable? = null
                    for (src in sources) {
                        val wav = WavAudioRecorder(
                            outputFile = outputFile,
                            sampleRateHz = currentSampleRate,
                            audioSource = src,
                            onAmplitude = { amp ->
                                _session.value = _session.value.copy(amplitude = amp)
                            }
                        )
                        try {
                            wav.start(scope)
                            startedWav = wav
                            break
                        } catch (t: Throwable) {
                            lastErr = t
                            wav.releaseImmediate()
                        }
                    }
                    wavRecorder = startedWav ?: throw (lastErr ?: IllegalStateException("WAV AudioRecord failed"))
                }
                AudioFormatOption.M4A, AudioFormatOption.AAC -> {
                    startMediaRecorderWithFallback(
                        outputFile = outputFile,
                        format = settings.audioFormat,
                        sampleRateHz = currentSampleRate,
                        bitRateBps = currentBitRate,
                        primarySource = androidSource
                    )
                }
            }

            val newSession = ActiveRecordingSession(
                phase = RecordingPhase.Recording,
                direction = direction,
                phoneNumber = phoneNumber,
                contactName = contactName,
                format = settings.audioFormat,
                audioSourceMode = resolvedMode,
                isTwoWayVerified = twoWayVerified,
                elapsedMs = 0L,
                amplitude = 0f,
                warningBanner = warningText
            )
            _session.value = newSession
            startTicker(scope)
            newSession
        }.onFailure {
            cleanupFailedStart(outputFile)
        }
    }

    fun pauseRecording(): Boolean {
        val current = _session.value
        if (current.phase != RecordingPhase.Recording) return false

        return runCatching {
            accumulatedActiveMs += (System.currentTimeMillis() - lastResumedAtMs).coerceAtLeast(0L)
            if (current.format == AudioFormatOption.WAV) {
                wavRecorder?.pause()
            } else {
                mediaRecorder?.pause()
            }
            _session.value = current.copy(
                phase = RecordingPhase.Paused,
                elapsedMs = accumulatedActiveMs,
                amplitude = 0f
            )
            true
        }.getOrDefault(false)
    }

    fun resumeRecording(): Boolean {
        val current = _session.value
        if (current.phase != RecordingPhase.Paused) return false

        return runCatching {
            if (current.format == AudioFormatOption.WAV) {
                wavRecorder?.resume()
            } else {
                mediaRecorder?.resume()
            }
            lastResumedAtMs = System.currentTimeMillis()
            _session.value = current.copy(phase = RecordingPhase.Recording)
            true
        }.getOrDefault(false)
    }

    suspend fun stopAndFinalizeRecording(): Result<RecordingEntity> {
        val current = _session.value
        if (current.phase == RecordingPhase.Idle) {
            return Result.failure(IllegalStateException("No active recording to stop."))
        }

        tickerJob?.cancel()
        tickerJob = null

        val finalDurationMs = if (current.phase == RecordingPhase.Recording) {
            accumulatedActiveMs + (System.currentTimeMillis() - lastResumedAtMs).coerceAtLeast(0L)
        } else {
            accumulatedActiveMs
        }.coerceAtLeast(500L)

        val file = currentFile ?: return Result.failure(IllegalStateException("Missing output file."))

        return runCatching {
            if (current.format == AudioFormatOption.WAV) {
                wavRecorder?.stop()
                wavRecorder = null
            } else {
                runCatching { mediaRecorder?.stop() }
                runCatching { mediaRecorder?.release() }
                mediaRecorder = null
            }

            if (!file.exists() || file.length() <= 44L) {
                runCatching { file.delete() }
                error("Recording captured no audio data on this device configuration.")
            }

            val displayCaller = current.contactName.ifBlank {
                current.phoneNumber.ifBlank {
                    if (current.direction == CallDirection.MANUAL) "Manual Voice Memo" else "Phone Call"
                }
            }
            val title = if (current.direction == CallDirection.MANUAL) {
                "Voice Note · ${file.nameWithoutExtension}"
            } else {
                "${current.direction.label} · $displayCaller"
            }

            RecordingEntity(
                title = title,
                phoneNumber = current.phoneNumber,
                contactName = current.contactName,
                callDirection = current.direction,
                filePath = file.absolutePath,
                format = current.format,
                sampleRateHz = currentSampleRate,
                bitRateBps = if (current.format == AudioFormatOption.WAV) {
                    currentSampleRate * 16
                } else {
                    currentBitRate
                },
                durationMs = finalDurationMs,
                fileSizeBytes = file.length(),
                createdAtMillis = currentStartedAt,
                audioSourceMode = current.audioSourceMode,
                isTwoWayVerified = current.isTwoWayVerified
            )
        }.also {
            currentFile = null
            _session.value = ActiveRecordingSession()
        }
    }

    fun abortAndRelease() {
        tickerJob?.cancel()
        tickerJob = null
        wavRecorder?.releaseImmediate()
        wavRecorder = null
        runCatching { mediaRecorder?.stop() }
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        currentFile?.let { runCatching { if (it.exists()) it.delete() } }
        currentFile = null
        _session.value = ActiveRecordingSession()
    }

    private fun startMediaRecorderWithFallback(
        outputFile: File,
        format: AudioFormatOption,
        sampleRateHz: Int,
        bitRateBps: Int,
        primarySource: Int
    ) {
        val candidateSources = listOf(
            primarySource,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        ).distinct()
        var lastError: Throwable? = null

        for (source in candidateSources) {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            try {
                recorder.setAudioSource(source)
                when (format) {
                    AudioFormatOption.M4A -> {
                        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    }
                    AudioFormatOption.AAC -> {
                        recorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    }
                    AudioFormatOption.WAV -> Unit
                }
                recorder.setAudioSamplingRate(sampleRateHz)
                recorder.setAudioEncodingBitRate(bitRateBps)
                recorder.setAudioChannels(1)
                recorder.setOutputFile(outputFile.absolutePath)
                recorder.prepare()
                recorder.start()
                mediaRecorder = recorder
                return
            } catch (t: Throwable) {
                lastError = t
                runCatching { recorder.release() }
            }
        }
        throw lastError ?: IllegalStateException("Unable to initialize MediaRecorder.")
    }

    private fun startTicker(scope: CoroutineScope) {
        tickerJob?.cancel()
        tickerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val cur = _session.value
                if (cur.phase == RecordingPhase.Recording) {
                    val elapsed = accumulatedActiveMs +
                        (System.currentTimeMillis() - lastResumedAtMs).coerceAtLeast(0L)
                    val amp = if (cur.format == AudioFormatOption.WAV) {
                        cur.amplitude
                    } else {
                        val rawMax = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                        (rawMax / 24000f).coerceIn(0f, 1f)
                    }
                    _session.value = cur.copy(elapsedMs = elapsed, amplitude = amp)
                }
                delay(80L)
            }
        }
    }

    private fun cleanupFailedStart(outputFile: File) {
        wavRecorder?.releaseImmediate()
        wavRecorder = null
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        runCatching { if (outputFile.exists()) outputFile.delete() }
        currentFile = null
        _session.value = ActiveRecordingSession()
    }

    private data class ResolvedSource(
        val androidAudioSource: Int,
        val mode: AudioSourceMode,
        val isTwoWayVerified: Boolean,
        val warning: String?
    )

    private fun resolveAudioSource(
        attemptVoiceCall: Boolean,
        direction: CallDirection,
        compat: CompatibilityStatus
    ): ResolvedSource {
        if (attemptVoiceCall && compat.hasPrivilegedCaptureAudioOutput) {
            return ResolvedSource(
                androidAudioSource = MediaRecorder.AudioSource.VOICE_CALL,
                mode = AudioSourceMode.VOICE_CALL_SYSTEM,
                isTwoWayVerified = true,
                warning = null
            )
        }

        val source = if (direction == CallDirection.MANUAL) {
            MediaRecorder.AudioSource.MIC
        } else {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        }
        val mode = if (source == MediaRecorder.AudioSource.MIC) {
            AudioSourceMode.MICROPHONE
        } else {
            AudioSourceMode.VOICE_COMMUNICATION
        }
        val warning = if (direction != CallDirection.MANUAL && !compat.isTwoWaySupported) {
            "Two-way cellular line capture is restricted on this Android ${compat.androidVersionName} device. Recording microphone stream only."
        } else {
            null
        }
        return ResolvedSource(
            androidAudioSource = source,
            mode = mode,
            isTwoWayVerified = false,
            warning = warning
        )
    }

    companion object {
        fun detectCompatibility(context: Context): CompatibilityStatus {
            val apiLevel = Build.VERSION.SDK_INT
            val versionName = Build.VERSION.RELEASE ?: "Unknown"

            val hasRecordAudio = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            val hasPhoneState = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            val hasCallLog = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED

            val hasContacts = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED

            val hasNotification = if (apiLevel >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val hasCaptureAudioOutput = ContextCompat.checkSelfPermission(
                context,
                "android.permission.CAPTURE_AUDIO_OUTPUT"
            ) == PackageManager.PERMISSION_GRANTED

            val isTwoWay = hasCaptureAudioOutput

            val title = when {
                isTwoWay -> "Two-Way System Capture Permitted"
                apiLevel >= Build.VERSION_CODES.Q -> "Microphone Mode Only (Android 10+ Restriction)"
                else -> "Standard Microphone Mode (Legacy Android)"
            }

            val summary = when {
                isTwoWay ->
                    "System-level CAPTURE_AUDIO_OUTPUT permission is present. VOICE_CALL audio source is supported."
                apiLevel >= Build.VERSION_CODES.Q ->
                    "Android $versionName (API $apiLevel) blocks third-party apps from capturing the remote cellular downlink without privileged OEM permissions. This app honestly records your microphone (and speakerphone audio when audible) and never fakes two-way capture."
                else ->
                    "Android $versionName (API $apiLevel) lacks CAPTURE_AUDIO_OUTPUT permission on this device. Recording uses the standard microphone path; remote voice requires speakerphone."
            }

            val bgNote = if (apiLevel >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                "Android 14+ restricts starting microphone foreground services directly from background broadcasts. Keep the app open or tap the call notification prompt if auto-start is blocked by the OS."
            } else if (apiLevel >= Build.VERSION_CODES.Q) {
                "Android 10+ enforces strict background microphone access rules. Auto-recording displays a persistent foreground notification when active."
            } else {
                "Background call monitoring operates via standard telephony state broadcasts."
            }

            return CompatibilityStatus(
                androidVersionName = versionName,
                apiLevel = apiLevel,
                hasRecordAudioPermission = hasRecordAudio,
                hasPhoneStatePermission = hasPhoneState,
                hasCallLogPermission = hasCallLog,
                hasContactsPermission = hasContacts,
                hasNotificationPermission = hasNotification,
                hasPrivilegedCaptureAudioOutput = hasCaptureAudioOutput,
                isTwoWaySupported = isTwoWay,
                recommendedAudioSource = if (isTwoWay) {
                    AudioSourceMode.VOICE_CALL_SYSTEM
                } else {
                    AudioSourceMode.VOICE_COMMUNICATION
                },
                compatibilityTitle = title,
                compatibilitySummary = summary,
                backgroundRestrictionNote = bgNote
            )
        }
    }
}

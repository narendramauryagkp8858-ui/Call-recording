package com.example.service

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.AppContainer
import com.example.MainActivity
import com.example.R
import com.example.audio.RecordingPhase
import com.example.data.CallDirection
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecordingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: AppContainer
    private var sessionObserverJob: Job? = null
    private var isAutoCallRecording: Boolean = false

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.get(applicationContext)
        ensureNotificationChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_MANUAL

        when (action) {
            ACTION_START_MANUAL -> {
                isAutoCallRecording = false
                startForegroundWithNotification(
                    title = getString(R.string.notif_recording_title),
                    body = "Starting manual studio capture...",
                    isPaused = false
                )
                startRecordingInternal(
                    direction = CallDirection.MANUAL,
                    phoneNumber = "",
                    contactName = ""
                )
            }

            ACTION_START_AUTO_CALL -> {
                isAutoCallRecording = true
                val dirName = intent?.getStringExtra(EXTRA_DIRECTION) ?: CallDirection.INCOMING.name
                val direction = runCatching { CallDirection.valueOf(dirName) }
                    .getOrDefault(CallDirection.INCOMING)
                val phoneNumber = intent?.getStringExtra(EXTRA_PHONE_NUMBER).orEmpty()
                val contactName = intent?.getStringExtra(EXTRA_CONTACT_NAME).orEmpty()

                startForegroundWithNotification(
                    title = getString(R.string.notif_recording_title),
                    body = "${direction.label} · ${contactName.ifBlank { phoneNumber.ifBlank { "Phone Call" } }}",
                    isPaused = false
                )
                startRecordingInternal(
                    direction = direction,
                    phoneNumber = phoneNumber,
                    contactName = contactName
                )
            }

            ACTION_PAUSE -> {
                container.recordingManager.pauseRecording()
                updateOngoingNotification(isPaused = true)
            }

            ACTION_RESUME -> {
                container.recordingManager.resumeRecording()
                updateOngoingNotification(isPaused = false)
            }

            ACTION_STOP -> {
                stopAndSaveInternal()
            }

            ACTION_STOP_AUTO_ONLY -> {
                if (isAutoCallRecording) {
                    stopAndSaveInternal()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startRecordingInternal(
        direction: CallDirection,
        phoneNumber: String,
        contactName: String
    ) {
        serviceScope.launch {
            val settings = container.settingsDataStore.getSettingsOnce()
            if (container.recordingManager.session.value.phase != RecordingPhase.Idle) {
                return@launch
            }
            val outputFile = container.repository.createOutputFile(settings.audioFormat, direction)
            val result = container.recordingManager.startRecording(
                scope = serviceScope,
                outputFile = outputFile,
                direction = direction,
                phoneNumber = phoneNumber,
                contactName = contactName,
                settings = settings
            )

            result.onSuccess {
                _lastServiceError.value = null
                observeSessionUpdates()
            }.onFailure { err ->
                _lastServiceError.value = err.localizedMessage ?: "Failed to start recording"
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private fun observeSessionUpdates() {
        sessionObserverJob?.cancel()
        sessionObserverJob = serviceScope.launch {
            var lastSecond = -1L
            container.recordingManager.session.collect { session ->
                if (session.phase == RecordingPhase.Idle) return@collect
                val sec = session.elapsedMs / 1000L
                if (sec != lastSecond || session.phase == RecordingPhase.Paused) {
                    lastSecond = sec
                    updateOngoingNotification(isPaused = session.phase == RecordingPhase.Paused)
                }
            }
        }
    }

    private fun stopAndSaveInternal() {
        serviceScope.launch {
            sessionObserverJob?.cancel()
            sessionObserverJob = null
            val settings = container.settingsDataStore.getSettingsOnce()
            val result = container.recordingManager.stopAndFinalizeRecording()
            result.onSuccess { entity ->
                container.repository.insertRecording(entity)
                if (settings.showSavedNotification) {
                    postSavedNotification(entity.title, formatDuration(entity.durationMs))
                }
            }.onFailure { err ->
                _lastServiceError.value = err.localizedMessage ?: "Unable to save recording"
            }
            isAutoCallRecording = false
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun startForegroundWithNotification(title: String, body: String, isPaused: Boolean) {
        val notification = buildRecordingNotification(title, body, isPaused)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIF_ID_RECORDING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID_RECORDING, notification)
        }
    }

    private fun updateOngoingNotification(isPaused: Boolean) {
        val session = container.recordingManager.session.value
        val label = session.contactName.ifBlank {
            session.phoneNumber.ifBlank {
                if (session.direction == CallDirection.MANUAL) "Manual Recording" else "${session.direction.label} Call"
            }
        }
        val modeTag = if (session.isTwoWayVerified) "Two-Way" else "Mic"
        val timeText = formatDuration(session.elapsedMs)
        val title = if (isPaused) getString(R.string.notif_recording_paused) else getString(R.string.notif_recording_title)
        val body = "$label · $timeText · ${session.format.displayName} ($modeTag)"
        val notification = buildRecordingNotification(title, body, isPaused)

        if (canPostNotifications(this)) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID_RECORDING, notification)
        }
    }

    private fun buildRecordingNotification(
        title: String,
        body: String,
        isPaused: Boolean
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseOrResumeIntent = PendingIntent.getService(
            this,
            101,
            Intent(this, RecordingService::class.java).apply {
                action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, RecordingService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_RECORDING)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(
                0,
                if (isPaused) getString(R.string.action_resume) else getString(R.string.action_pause),
                pauseOrResumeIntent
            )
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .build()
    }

    private fun postSavedNotification(recordingTitle: String, durationText: String) {
        if (!canPostNotifications(this)) return
        val openAppIntent = PendingIntent.getActivity(
            this,
            103,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(android.R.drawable.stat_sys_headset)
            .setContentTitle(getString(R.string.notif_saved_title))
            .setContentText("$recordingTitle ($durationText)")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_SAVED, notif)
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        sessionObserverJob?.cancel()
        if (container.recordingManager.session.value.phase != RecordingPhase.Idle) {
            container.recordingManager.abortAndRelease()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_RECORDING = "narendra_recording_active"
        const val CHANNEL_MONITOR = "narendra_call_monitor"

        private const val NOTIF_ID_RECORDING = 4101
        private const val NOTIF_ID_SAVED = 4102
        private const val NOTIF_ID_BG_PROMPT = 4103
        private const val NOTIF_ID_BOOT_READY = 4104

        const val ACTION_START_MANUAL = "com.narendra.callrecorder.action.START_MANUAL"
        const val ACTION_START_AUTO_CALL = "com.narendra.callrecorder.action.START_AUTO_CALL"
        const val ACTION_PAUSE = "com.narendra.callrecorder.action.PAUSE"
        const val ACTION_RESUME = "com.narendra.callrecorder.action.RESUME"
        const val ACTION_STOP = "com.narendra.callrecorder.action.STOP"
        const val ACTION_STOP_AUTO_ONLY = "com.narendra.callrecorder.action.STOP_AUTO_ONLY"

        const val EXTRA_DIRECTION = "extra_direction"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"

        private val _lastServiceError = MutableStateFlow<String?>(null)
        val lastServiceError: StateFlow<String?> = _lastServiceError.asStateFlow()

        fun clearServiceError() {
            _lastServiceError.value = null
        }

        fun startManualRecording(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START_MANUAL
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startAutoCallRecording(
            context: Context,
            direction: CallDirection,
            phoneNumber: String,
            contactName: String
        ) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START_AUTO_CALL
                putExtra(EXTRA_DIRECTION, direction.name)
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_CONTACT_NAME, contactName)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e is ForegroundServiceStartNotAllowedException
                ) {
                    postBackgroundRestrictedNotification(context, direction, phoneNumber, contactName)
                } else {
                    _lastServiceError.value = e.localizedMessage
                        ?: "OS restricted background microphone recording."
                }
            }
        }

        fun pauseRecording(context: Context) {
            context.startService(
                Intent(context, RecordingService::class.java).apply { action = ACTION_PAUSE }
            )
        }

        fun resumeRecording(context: Context) {
            context.startService(
                Intent(context, RecordingService::class.java).apply { action = ACTION_RESUME }
            )
        }

        fun stopRecording(context: Context) {
            context.startService(
                Intent(context, RecordingService::class.java).apply { action = ACTION_STOP }
            )
        }

        fun stopIfAutoCallActive(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, RecordingService::class.java).apply {
                        action = ACTION_STOP_AUTO_ONLY
                    }
                )
            }
        }

        fun postMonitorReadyNotification(context: Context) {
            ensureNotificationChannels(context)
            if (!canPostNotifications(context)) return
            val openAppIntent = PendingIntent.getActivity(
                context,
                104,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(context, CHANNEL_MONITOR)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(context.getString(R.string.status_monitoring))
                .setContentText("Call monitor restored after restart. Tap to review settings.")
                .setAutoCancel(true)
                .setContentIntent(openAppIntent)
                .build()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID_BOOT_READY, notif)
        }

        private fun postBackgroundRestrictedNotification(
            context: Context,
            direction: CallDirection,
            phoneNumber: String,
            contactName: String
        ) {
            ensureNotificationChannels(context)
            if (!canPostNotifications(context)) return

            val startIntent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START_AUTO_CALL
                putExtra(EXTRA_DIRECTION, direction.name)
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_CONTACT_NAME, contactName)
            }
            val pendingStart = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    105,
                    startIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    context,
                    105,
                    startIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val notif = NotificationCompat.Builder(context, CHANNEL_MONITOR)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(context.getString(R.string.notif_bg_restricted_title))
                .setContentText(context.getString(R.string.notif_bg_restricted_body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingStart)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID_BG_PROMPT, notif)
        }

        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val recChannel = NotificationChannel(
                    CHANNEL_RECORDING,
                    context.getString(R.string.notif_channel_recording_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notif_channel_recording_desc)
                }
                val monChannel = NotificationChannel(
                    CHANNEL_MONITOR,
                    context.getString(R.string.notif_channel_monitor_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notif_channel_monitor_desc)
                }
                nm.createNotificationChannel(recChannel)
                nm.createNotificationChannel(monChannel)
            }
        }

        private fun canPostNotifications(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        fun formatDuration(ms: Long): String {
            val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }
    }
}

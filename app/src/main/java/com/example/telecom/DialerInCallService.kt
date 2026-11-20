package com.example.telecom

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.AppContainer
import com.example.R
import com.example.audio.RecordingPhase
import com.example.data.CallDirection
import com.example.service.RecordingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class CallUiPhase(val label: String) {
    NONE("No Call"),
    DIALING("Dialing..."),
    RINGING("Incoming Call"),
    CONNECTING("Connecting..."),
    ACTIVE("Connected"),
    HOLDING("On Hold"),
    DISCONNECTING("Ending Call...")
}

data class ActiveCallSummary(
    val callId: String,
    val number: String,
    val contactName: String,
    val direction: CallDirection,
    val phase: CallUiPhase,
    val connectTimeMillis: Long,
    val canHold: Boolean,
    val canMerge: Boolean
)

data class InCallUiState(
    val primaryCall: ActiveCallSummary? = null,
    val secondaryCall: ActiveCallSummary? = null,
    val elapsedSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isBluetoothOn: Boolean = false,
    val isBluetoothAvailable: Boolean = false
) {
    val hasCalls: Boolean get() = primaryCall != null || secondaryCall != null
}

class DialerInCallService : InCallService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: AppContainer
    private val trackedCalls = linkedMapOf<Call, String>()
    private val autoRecordedCallIds = hashSetOf<String>()
    private var timerJob: Job? = null
    private var currentEndpoints: List<CallEndpoint> = emptyList()
    private var currentEndpoint: CallEndpoint? = null

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            handleCallStateUpdated(call, state)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            refreshUiState()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer.get(applicationContext)
        ensureCallNotificationChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ANSWER_FROM_NOTIF -> answerPrimaryOrRingingCall()
            ACTION_DECLINE_FROM_NOTIF -> rejectOrDisconnectRingingCall()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val callId = "${System.currentTimeMillis()}_${trackedCalls.size}"
        trackedCalls[call] = callId
        call.registerCallback(callCallback)

        refreshUiState()
        startTimerLoopIfNeeded()

        val state = getCallStateCompat(call)
        if (state == Call.STATE_RINGING) {
            postIncomingCallNotification(call)
        } else {
            postOngoingCallNotification(call)
        }

        launchInCallUi()
        if (state == Call.STATE_ACTIVE) {
            maybeAutoRecordOnPickup(call, callId)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        trackedCalls.remove(call)
        refreshUiState()

        if (trackedCalls.isEmpty()) {
            timerJob?.cancel()
            timerJob = null
            cancelCallNotifications()
            RecordingService.stopIfAutoCallActive(applicationContext)
            _inCallState.value = InCallUiState()
        } else {
            val top = trackedCalls.keys.firstOrNull()
            if (top != null) {
                if (getCallStateCompat(top) == Call.STATE_RINGING) {
                    postIncomingCallNotification(top)
                } else {
                    postOngoingCallNotification(top)
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        @Suppress("DEPRECATION")
        super.onCallAudioStateChanged(audioState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE && audioState != null) {
            _inCallState.value = _inCallState.value.copy(
                isMuted = audioState.isMuted,
                isSpeakerOn = audioState.route == CallAudioState.ROUTE_SPEAKER,
                isBluetoothOn = audioState.route == CallAudioState.ROUTE_BLUETOOTH,
                isBluetoothAvailable = (audioState.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH) != 0
            )
        }
    }

    override fun onMuteStateChanged(isMuted: Boolean) {
        super.onMuteStateChanged(isMuted)
        _inCallState.value = _inCallState.value.copy(isMuted = isMuted)
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            currentEndpoint = callEndpoint
            _inCallState.value = _inCallState.value.copy(
                isSpeakerOn = callEndpoint.endpointType == CallEndpoint.TYPE_SPEAKER,
                isBluetoothOn = callEndpoint.endpointType == CallEndpoint.TYPE_BLUETOOTH
            )
        }
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableEndpoints)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            currentEndpoints = availableEndpoints
            _inCallState.value = _inCallState.value.copy(
                isBluetoothAvailable = availableEndpoints.any { it.endpointType == CallEndpoint.TYPE_BLUETOOTH }
            )
        }
    }

    private fun handleCallStateUpdated(call: Call, state: Int) {
        val callId = trackedCalls[call] ?: return
        refreshUiState()

        when (state) {
            Call.STATE_ACTIVE -> {
                cancelIncomingNotification()
                postOngoingCallNotification(call)
                maybeAutoRecordOnPickup(call, callId)
            }
            Call.STATE_HOLDING, Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                postOngoingCallNotification(call)
            }
            Call.STATE_DISCONNECTED -> {
                if (trackedCalls.size <= 1) {
                    RecordingService.stopIfAutoCallActive(applicationContext)
                }
            }
        }
    }

    private fun maybeAutoRecordOnPickup(call: Call, callId: String) {
        if (!autoRecordedCallIds.add(callId)) return

        val direction = resolveDirection(call)
        val number = extractNumber(call)

        serviceScope.launch {
            val settings = container.settingsDataStore.getSettingsOnce()
            if (container.recordingManager.session.value.phase != RecordingPhase.Idle) {
                return@launch
            }
            if (container.repository.shouldRecordCall(direction, number, settings)) {
                val contactName = resolveDisplayName(call, number)
                RecordingService.startAutoCallRecording(
                    context = applicationContext,
                    direction = direction,
                    phoneNumber = number,
                    contactName = contactName
                )
            }
        }
    }

    fun startManualRecordingForActiveCall() {
        val primary = selectPrimaryCall() ?: return
        val direction = resolveDirection(primary)
        val number = extractNumber(primary)
        val contactName = resolveDisplayName(primary, number)
        RecordingService.startAutoCallRecording(
            context = applicationContext,
            direction = direction,
            phoneNumber = number,
            contactName = contactName
        )
    }

    fun answerPrimaryOrRingingCall() {
        val ringing = trackedCalls.keys.firstOrNull { getCallStateCompat(it) == Call.STATE_RINGING }
            ?: return
        ringing.answer(VideoProfile.STATE_AUDIO_ONLY)
        cancelIncomingNotification()
        launchInCallUi()
    }

    fun holdActiveAndAnswerWaitingCall() {
        val ringing = trackedCalls.keys.firstOrNull { getCallStateCompat(it) == Call.STATE_RINGING }
            ?: return
        val active = trackedCalls.keys.firstOrNull { getCallStateCompat(it) == Call.STATE_ACTIVE }
        active?.hold()
        ringing.answer(VideoProfile.STATE_AUDIO_ONLY)
        cancelIncomingNotification()
    }

    fun endActiveAndAnswerWaitingCall() {
        val ringing = trackedCalls.keys.firstOrNull { getCallStateCompat(it) == Call.STATE_RINGING }
            ?: return
        val active = trackedCalls.keys.firstOrNull { getCallStateCompat(it) == Call.STATE_ACTIVE }
        active?.disconnect()
        ringing.answer(VideoProfile.STATE_AUDIO_ONLY)
        cancelIncomingNotification()
    }

    fun rejectOrDisconnectRingingCall() {
        val ringing = trackedCalls.keys.firstOrNull { getCallStateCompat(it) == Call.STATE_RINGING }
        if (ringing != null) {
            ringing.reject(false, null)
            cancelIncomingNotification()
        } else {
            disconnectPrimaryCall()
        }
    }

    fun disconnectPrimaryCall() {
        val target = selectPrimaryCall() ?: trackedCalls.keys.firstOrNull() ?: return
        if (getCallStateCompat(target) == Call.STATE_RINGING) {
            target.reject(false, null)
        } else {
            target.disconnect()
        }
    }

    fun toggleHoldPrimaryCall() {
        val primary = selectPrimaryCall() ?: return
        if (getCallStateCompat(primary) == Call.STATE_HOLDING) {
            primary.unhold()
        } else if (getCallStateCompat(primary) == Call.STATE_ACTIVE) {
            primary.hold()
        }
    }

    fun swapCalls() {
        val held = trackedCalls.keys.firstOrNull { getCallStateCompat(it) == Call.STATE_HOLDING }
        val active = trackedCalls.keys.firstOrNull { getCallStateCompat(it) == Call.STATE_ACTIVE }
        if (held != null) {
            active?.hold()
            held.unhold()
        }
    }

    fun mergeCalls() {
        val primary = selectPrimaryCall() ?: return
        val other = trackedCalls.keys.firstOrNull { it != primary } ?: return
        if (primary.details?.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) == true) {
            primary.mergeConference()
        } else {
            primary.conference(other)
        }
    }

    fun toggleMute() {
        setMuted(!_inCallState.value.isMuted)
    }

    fun toggleSpeakerphone() {
        val enableSpeaker = !_inCallState.value.isSpeakerOn
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val targetType = if (enableSpeaker) {
                CallEndpoint.TYPE_SPEAKER
            } else {
                CallEndpoint.TYPE_EARPIECE
            }
            val endpoint = currentEndpoints.firstOrNull { it.endpointType == targetType }
            if (endpoint != null) {
                requestCallEndpointChange(endpoint, ContextCompat.getMainExecutor(this)) {}
            }
        } else {
            @Suppress("DEPRECATION")
            setAudioRoute(
                if (enableSpeaker) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
            )
        }
    }

    fun toggleBluetoothAudio() {
        val enableBt = !_inCallState.value.isBluetoothOn
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val targetType = if (enableBt) {
                CallEndpoint.TYPE_BLUETOOTH
            } else {
                CallEndpoint.TYPE_EARPIECE
            }
            val endpoint = currentEndpoints.firstOrNull { it.endpointType == targetType }
            if (endpoint != null) {
                requestCallEndpointChange(endpoint, ContextCompat.getMainExecutor(this)) {}
            }
        } else {
            @Suppress("DEPRECATION")
            setAudioRoute(
                if (enableBt) CallAudioState.ROUTE_BLUETOOTH else CallAudioState.ROUTE_EARPIECE
            )
        }
    }

    fun playDtmf(digit: Char) {
        val active = selectPrimaryCall() ?: return
        active.playDtmfTone(digit)
        serviceScope.launch {
            delay(180L)
            runCatching { active.stopDtmfTone() }
        }
    }

    private fun selectPrimaryCall(): Call? {
        val calls = trackedCalls.keys.toList()
        return calls.firstOrNull { getCallStateCompat(it) == Call.STATE_ACTIVE }
            ?: calls.firstOrNull { getCallStateCompat(it) == Call.STATE_DIALING }
            ?: calls.firstOrNull { getCallStateCompat(it) == Call.STATE_CONNECTING }
            ?: calls.firstOrNull { getCallStateCompat(it) == Call.STATE_RINGING }
            ?: calls.firstOrNull { getCallStateCompat(it) == Call.STATE_HOLDING }
            ?: calls.firstOrNull()
    }

    private fun refreshUiState() {
        val primary = selectPrimaryCall()
        val secondary = trackedCalls.keys.firstOrNull { it != primary }

        val primarySummary = primary?.let { buildSummary(it) }
        val secondarySummary = secondary?.let { buildSummary(it) }

        val elapsed = if (primarySummary?.phase == CallUiPhase.ACTIVE && primarySummary.connectTimeMillis > 0L) {
            ((System.currentTimeMillis() - primarySummary.connectTimeMillis) / 1000L).coerceAtLeast(0L)
        } else {
            0L
        }

        _inCallState.value = _inCallState.value.copy(
            primaryCall = primarySummary,
            secondaryCall = secondarySummary,
            elapsedSeconds = elapsed
        )
    }

    private fun startTimerLoopIfNeeded() {
        if (timerJob?.isActive == true) return
        timerJob = serviceScope.launch {
            while (isActive) {
                refreshUiState()
                delay(1000L)
            }
        }
    }

    private fun buildSummary(call: Call): ActiveCallSummary {
        val number = extractNumber(call)
        val contactName = resolveDisplayName(call, number)
        val state = getCallStateCompat(call)
        val phase = when (state) {
            Call.STATE_DIALING -> CallUiPhase.DIALING
            Call.STATE_RINGING -> CallUiPhase.RINGING
            Call.STATE_CONNECTING -> CallUiPhase.CONNECTING
            Call.STATE_ACTIVE -> CallUiPhase.ACTIVE
            Call.STATE_HOLDING -> CallUiPhase.HOLDING
            Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> CallUiPhase.DISCONNECTING
            else -> CallUiPhase.CONNECTING
        }
        val caps = call.details?.callCapabilities ?: 0
        return ActiveCallSummary(
            callId = trackedCalls[call].orEmpty(),
            number = number,
            contactName = contactName,
            direction = resolveDirection(call),
            phase = phase,
            connectTimeMillis = call.details?.connectTimeMillis ?: 0L,
            canHold = (caps and Call.Details.CAPABILITY_HOLD) != 0 ||
                (caps and Call.Details.CAPABILITY_SUPPORT_HOLD) != 0,
            canMerge = (caps and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0
        )
    }

    private fun extractNumber(call: Call): String {
        return call.details?.handle?.schemeSpecificPart?.trim().orEmpty()
    }

    private fun resolveDisplayName(call: Call, number: String): String {
        val callerDisplay = call.details?.callerDisplayName?.trim().orEmpty()
        if (callerDisplay.isNotEmpty()) return callerDisplay
        val contactDisplay = call.details?.contactDisplayName?.trim().orEmpty()
        if (contactDisplay.isNotEmpty()) return contactDisplay
        return container.repository.resolveContactName(number)
    }

    private fun resolveDirection(call: Call): CallDirection {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (call.details?.callDirection) {
                Call.Details.DIRECTION_INCOMING -> CallDirection.INCOMING
                Call.Details.DIRECTION_OUTGOING -> CallDirection.OUTGOING
                else -> if (getCallStateCompat(call) == Call.STATE_RINGING) {
                    CallDirection.INCOMING
                } else {
                    CallDirection.OUTGOING
                }
            }
        } else {
            if (getCallStateCompat(call) == Call.STATE_RINGING) {
                CallDirection.INCOMING
            } else {
                CallDirection.OUTGOING
            }
        }
    }

    private fun getCallStateCompat(call: Call): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details?.state ?: Call.STATE_NEW
        } else {
            @Suppress("DEPRECATION")
            call.state
        }
    }

    private fun launchInCallUi() {
        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { startActivity(intent) }
    }

    @SuppressLint("MissingPermission")
    private fun postIncomingCallNotification(call: Call) {
        if (!canPostNotif()) return
        val number = extractNumber(call)
        val caller = resolveDisplayName(call, number).ifBlank { number.ifBlank { "Incoming Call" } }

        val fullScreenIntent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val fullScreenPending = PendingIntent.getActivity(
            this,
            301,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val answerPending = PendingIntent.getService(
            this,
            302,
            Intent(this, DialerInCallService::class.java).apply { action = ACTION_ANSWER_FROM_NOTIF },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val declinePending = PendingIntent.getService(
            this,
            303,
            Intent(this, DialerInCallService::class.java).apply { action = ACTION_DECLINE_FROM_NOTIF },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_INCOMING_CALL)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Incoming Call")
            .setContentText(caller)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPending)
            .setFullScreenIntent(fullScreenPending, true)
            .addAction(0, "Decline", declinePending)
            .addAction(0, "Answer", answerPending)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_INCOMING_CALL, notif)
    }

    private fun postOngoingCallNotification(call: Call) {
        if (!canPostNotif()) return
        val number = extractNumber(call)
        val caller = resolveDisplayName(call, number).ifBlank { number.ifBlank { "Active Call" } }

        val openPending = PendingIntent.getActivity(
            this,
            304,
            Intent(this, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hangupPending = PendingIntent.getService(
            this,
            305,
            Intent(this, DialerInCallService::class.java).apply { action = ACTION_DECLINE_FROM_NOTIF },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ONGOING_CALL)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(caller)
            .setContentText("Tap to return to call & recorder controls")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(openPending)
            .addAction(0, "End Call", hangupPending)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_ONGOING_CALL, notif)
    }

    private fun cancelIncomingNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID_INCOMING_CALL)
    }

    private fun cancelCallNotifications() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID_INCOMING_CALL)
        nm.cancel(NOTIF_ID_ONGOING_CALL)
    }

    private fun canPostNotif(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun onDestroy() {
        timerJob?.cancel()
        trackedCalls.keys.forEach { runCatching { it.unregisterCallback(callCallback) } }
        trackedCalls.clear()
        cancelCallNotifications()
        if (instance === this) instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_INCOMING_CALL = "narendra_incoming_calls"
        const val CHANNEL_ONGOING_CALL = "narendra_ongoing_calls"
        private const val NOTIF_ID_INCOMING_CALL = 4201
        private const val NOTIF_ID_ONGOING_CALL = 4202
        private const val ACTION_ANSWER_FROM_NOTIF = "com.narendra.callrecorder.ANSWER_CALL"
        private const val ACTION_DECLINE_FROM_NOTIF = "com.narendra.callrecorder.DECLINE_CALL"

        private val _inCallState = MutableStateFlow(InCallUiState())
        val inCallState: StateFlow<InCallUiState> = _inCallState.asStateFlow()

        @Volatile
        var instance: DialerInCallService? = null
            private set

        fun ensureCallNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val incomingChannel = NotificationChannel(
                    CHANNEL_INCOMING_CALL,
                    context.getString(R.string.notif_channel_incoming_call_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_incoming_call_desc)
                    setSound(
                        ringtoneUri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }

                val ongoingChannel = NotificationChannel(
                    CHANNEL_ONGOING_CALL,
                    context.getString(R.string.notif_channel_ongoing_call_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notif_channel_ongoing_call_desc)
                }

                nm.createNotificationChannel(incomingChannel)
                nm.createNotificationChannel(ongoingChannel)
            }
        }
    }
}

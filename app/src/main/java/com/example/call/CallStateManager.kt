package com.example.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.data.CallDirection
import com.example.data.RecordingRepository
import com.example.data.SettingsDataStore
import com.example.service.RecordingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CallLifecycleState(val label: String) {
    IDLE("No Active Call"),
    RINGING("Incoming Call Ringing"),
    OFFHOOK("Call In Progress")
}

data class CallMonitorStatus(
    val callState: CallLifecycleState = CallLifecycleState.IDLE,
    val lastNumber: String = "",
    val lastDirection: CallDirection? = null,
    val statusDetail: String = "Ready for call monitoring"
)

class CallStateManager(
    private val context: Context,
    private val repository: RecordingRepository,
    private val settingsDataStore: SettingsDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _monitorStatus = MutableStateFlow(CallMonitorStatus())
    val monitorStatus: StateFlow<CallMonitorStatus> = _monitorStatus.asStateFlow()

    private var lastTelephonyState: Int = TelephonyManager.CALL_STATE_IDLE
    private var offhookTriggeredForCurrentCall: Boolean = false
    private var pendingOutgoingNumber: String = ""
    private var lastRingingNumber: String = ""
    private var registeredCallback: Any? = null
    private var isRegistered = false

    fun registerTelephonyObserver() {
        if (isRegistered) return
        val hasPhoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPhoneState) return

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handlePhoneStateChanged(state, incomingNumber = null)
                    }
                }
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
                registeredCallback = callback
                isRegistered = true
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handlePhoneStateChanged(state, incomingNumber = phoneNumber)
                    }
                }
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                registeredCallback = listener
                isRegistered = true
            }
        }
    }

    fun unregisterTelephonyObserver() {
        if (!isRegistered) return
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (registeredCallback as? TelephonyCallback)?.let {
                    telephonyManager.unregisterTelephonyCallback(it)
                }
            } else {
                @Suppress("DEPRECATION")
                (registeredCallback as? PhoneStateListener)?.let {
                    telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
                }
            }
        }
        registeredCallback = null
        isRegistered = false
    }

    fun onOutgoingCallPlaced(number: String?) {
        val cleaned = number?.trim().orEmpty()
        pendingOutgoingNumber = cleaned
        scope.launch {
            settingsDataStore.setPendingCallInfo(CallDirection.OUTGOING, cleaned)
        }
        _monitorStatus.value = _monitorStatus.value.copy(
            lastNumber = cleaned,
            lastDirection = CallDirection.OUTGOING,
            statusDetail = "Outgoing call detected"
        )
    }

    fun onPhoneStateBroadcast(stateExtra: String?, incomingNumber: String?) {
        val mapped = when (stateExtra) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            else -> return
        }
        handlePhoneStateChanged(mapped, incomingNumber)
    }

    private fun handlePhoneStateChanged(newState: Int, incomingNumber: String?) {
        val cleanedNumber = incomingNumber?.trim().orEmpty()

        when (newState) {
            TelephonyManager.CALL_STATE_RINGING -> {
                if (cleanedNumber.isNotEmpty()) {
                    lastRingingNumber = cleanedNumber
                }
                pendingOutgoingNumber = ""
                offhookTriggeredForCurrentCall = false
                scope.launch {
                    settingsDataStore.setPendingCallInfo(CallDirection.INCOMING, lastRingingNumber)
                }
                _monitorStatus.value = CallMonitorStatus(
                    callState = CallLifecycleState.RINGING,
                    lastNumber = lastRingingNumber,
                    lastDirection = CallDirection.INCOMING,
                    statusDetail = "Incoming call ringing"
                )
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // On Android 9+, PHONE_STATE_CHANGED often fires twice (first with null number, then with number).
                // Allow a second pass if we haven't triggered recording yet and a number just arrived.
                if (offhookTriggeredForCurrentCall) {
                    return
                }
                if (lastTelephonyState == TelephonyManager.CALL_STATE_OFFHOOK && cleanedNumber.isEmpty()) {
                    return
                }

                val wasRinging = lastTelephonyState == TelephonyManager.CALL_STATE_RINGING ||
                    lastRingingNumber.isNotEmpty()
                val direction = if (wasRinging) CallDirection.INCOMING else CallDirection.OUTGOING

                scope.launch {
                    val settings = settingsDataStore.getSettingsOnce()
                    val resolvedNumber = when {
                        cleanedNumber.isNotEmpty() -> cleanedNumber
                        direction == CallDirection.INCOMING && lastRingingNumber.isNotEmpty() -> lastRingingNumber
                        direction == CallDirection.OUTGOING && pendingOutgoingNumber.isNotEmpty() -> pendingOutgoingNumber
                        else -> settings.lastPendingNumber
                    }
                    val effectiveDirection = if (!wasRinging && settings.lastPendingCallDirection == CallDirection.INCOMING) {
                        CallDirection.INCOMING
                    } else {
                        direction
                    }

                    _monitorStatus.value = CallMonitorStatus(
                        callState = CallLifecycleState.OFFHOOK,
                        lastNumber = resolvedNumber,
                        lastDirection = effectiveDirection,
                        statusDetail = "Call active (${effectiveDirection.label})"
                    )

                    val shouldRecord = repository.shouldRecordCall(effectiveDirection, resolvedNumber, settings)
                    if (shouldRecord) {
                        offhookTriggeredForCurrentCall = true
                        val contactName = repository.resolveContactName(resolvedNumber)
                        RecordingService.startAutoCallRecording(
                            context = context,
                            direction = effectiveDirection,
                            phoneNumber = resolvedNumber,
                            contactName = contactName
                        )
                    } else if (settings.autoRecordEnabled) {
                        _monitorStatus.value = _monitorStatus.value.copy(
                            statusDetail = if (resolvedNumber.isEmpty() && settings.filterMode != com.example.data.AutoRecordFilterMode.ALL_CALLS) {
                                "Waiting for caller ID or Call Log permission for contact filter"
                            } else {
                                "Call skipped by contact/direction rule"
                            }
                        )
                    }
                }
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                RecordingService.dismissBackgroundPromptNotification(context)
                if (lastTelephonyState != TelephonyManager.CALL_STATE_IDLE || offhookTriggeredForCurrentCall) {
                    RecordingService.stopIfAutoCallActive(context)
                }
                offhookTriggeredForCurrentCall = false
                lastRingingNumber = ""
                pendingOutgoingNumber = ""
                scope.launch {
                    settingsDataStore.setPendingCallInfo(null, "")
                }
                _monitorStatus.value = CallMonitorStatus(
                    callState = CallLifecycleState.IDLE,
                    lastNumber = "",
                    lastDirection = null,
                    statusDetail = "Call ended · Standby"
                )
            }
        }

        lastTelephonyState = newState
    }
}

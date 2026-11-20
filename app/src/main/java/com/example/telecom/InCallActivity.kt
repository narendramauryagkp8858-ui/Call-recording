package com.example.telecom

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.AppContainer
import com.example.audio.ActiveRecordingSession
import com.example.audio.RecordingPhase
import com.example.service.RecordingService
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.NarendraCallRecorderTheme
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.WarningAmber

class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureLockScreenVisibility()

        val container = AppContainer.get(applicationContext)

        setContent {
            val settings by container.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(
                initialValue = com.example.data.AppSettings()
            )
            val callState by DialerInCallService.inCallState.collectAsStateWithLifecycle()
            val recordingSession by container.recordingManager.session.collectAsStateWithLifecycle()

            LaunchedEffect(callState.hasCalls) {
                if (!callState.hasCalls) {
                    // Wait briefly in case telecom is still binding the call on launch
                    kotlinx.coroutines.delay(900L)
                    if (!DialerInCallService.inCallState.value.hasCalls) {
                        finishAndRemoveTask()
                    }
                }
            }

            NarendraCallRecorderTheme(darkTheme = settings.darkModeEnabled) {
                InCallScreenContent(
                    callState = callState,
                    recordingSession = recordingSession,
                    onAnswer = {
                        requestKeyguardDismiss()
                        DialerInCallService.instance?.answerPrimaryOrRingingCall()
                    },
                    onDecline = { DialerInCallService.instance?.rejectOrDisconnectRingingCall() },
                    onHangup = { DialerInCallService.instance?.disconnectPrimaryCall() },
                    onHoldAndAnswer = {
                        requestKeyguardDismiss()
                        DialerInCallService.instance?.holdActiveAndAnswerWaitingCall()
                    },
                    onEndAndAnswer = {
                        requestKeyguardDismiss()
                        DialerInCallService.instance?.endActiveAndAnswerWaitingCall()
                    },
                    onToggleMute = { DialerInCallService.instance?.toggleMute() },
                    onToggleSpeaker = { DialerInCallService.instance?.toggleSpeakerphone() },
                    onToggleBluetooth = { DialerInCallService.instance?.toggleBluetoothAudio() },
                    onToggleHold = { DialerInCallService.instance?.toggleHoldPrimaryCall() },
                    onSwapCalls = { DialerInCallService.instance?.swapCalls() },
                    onMergeCalls = { DialerInCallService.instance?.mergeCalls() },
                    onDtmfTone = { digit -> DialerInCallService.instance?.playDtmf(digit) },
                    onStartRecording = { DialerInCallService.instance?.startManualRecordingForActiveCall() },
                    onPauseRecording = { RecordingService.pauseRecording(this) },
                    onResumeRecording = { RecordingService.resumeRecording(this) },
                    onStopRecording = { RecordingService.stopRecording(this) }
                )
            }
        }
    }

    private fun configureLockScreenVisibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun requestKeyguardDismiss() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.requestDismissKeyguard(this, null)
        }
    }
}

@Composable
private fun InCallScreenContent(
    callState: InCallUiState,
    recordingSession: ActiveRecordingSession,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onHangup: () -> Unit,
    onHoldAndAnswer: () -> Unit,
    onEndAndAnswer: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleBluetooth: () -> Unit,
    onToggleHold: () -> Unit,
    onSwapCalls: () -> Unit,
    onMergeCalls: () -> Unit,
    onDtmfTone: (Char) -> Unit,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    var showKeypad by remember { mutableStateOf(false) }
    val primary = callState.primaryCall
    val secondary = callState.secondaryCall

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ObsidianBlack, MidnightNavy, ObsidianBlack)
                )
            )
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Caller info & Call Waiting Banner
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                AnimatedVisibility(visible = secondary != null && secondary.phase == CallUiPhase.RINGING) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        border = BorderStroke(1.dp, CyanGlow.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Call Waiting · ${secondary?.contactName?.ifBlank { secondary.number }.orEmpty()}",
                                style = MaterialTheme.typography.titleMedium,
                                color = CyanGlow
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onHoldAndAnswer,
                                    colors = ButtonDefaults.buttonColors(containerColor = VerifiedEmerald),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Hold & Answer", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = onEndAndAnswer,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("End & Answer", maxLines = 1)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = CircleShape,
                    color = ElectricBlue.copy(alpha = 0.18f),
                    border = BorderStroke(2.dp, CyanGlow.copy(alpha = 0.5f)),
                    modifier = Modifier.size(88.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Caller",
                            tint = CyanGlow,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = primary?.contactName?.ifBlank {
                        primary.number.ifBlank { "Phone Call" }
                    } ?: "Connecting...",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (primary != null && primary.contactName.isNotBlank() && primary.number.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = primary.number,
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = CyanGlow.copy(alpha = 0.85f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val statusText = when {
                    primary == null -> "Connecting..."
                    primary.phase == CallUiPhase.ACTIVE -> formatDurationMs(callState.elapsedSeconds * 1000L)
                    else -> primary.phase.label
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = if (primary?.phase == CallUiPhase.ACTIVE) VerifiedEmerald else Color.White.copy(alpha = 0.8f)
                )

                if (secondary != null && secondary.phase == CallUiPhase.HOLDING) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WarningAmber.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "On Hold: ${secondary.contactName.ifBlank { secondary.number }}",
                            style = MaterialTheme.typography.labelLarge,
                            color = WarningAmber,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Middle: Live In-Call Recording Bar + Optional DTMF Keypad
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (primary != null && primary.phase != CallUiPhase.RINGING) {
                    InCallRecordingStatusCard(
                        session = recordingSession,
                        isSpeakerOn = callState.isSpeakerOn,
                        onStartRecording = onStartRecording,
                        onPauseRecording = onPauseRecording,
                        onResumeRecording = onResumeRecording,
                        onStopRecording = onStopRecording,
                        onToggleSpeaker = onToggleSpeaker
                    )
                }

                AnimatedVisibility(visible = showKeypad && primary?.phase != CallUiPhase.RINGING) {
                    InCallDtmfPad(
                        onDigit = onDtmfTone,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            // Bottom: Answer/Decline OR Active Call Controls + Hangup
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (primary?.phase == CallUiPhase.RINGING) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onDecline,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(RecordingRed)
                                    .testTag("incall_decline_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "Decline call",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Decline", color = Color.White)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onAnswer,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(VerifiedEmerald)
                                    .testTag("incall_answer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Answer call",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Answer", color = Color.White)
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            InCallGridButton(
                                icon = Icons.Default.MicOff,
                                label = "Mute",
                                active = callState.isMuted,
                                onClick = onToggleMute
                            )
                            InCallGridButton(
                                icon = Icons.Default.Dialpad,
                                label = "Keypad",
                                active = showKeypad,
                                onClick = { showKeypad = !showKeypad }
                            )
                            InCallGridButton(
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                label = "Speaker",
                                active = callState.isSpeakerOn,
                                onClick = onToggleSpeaker
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            InCallGridButton(
                                icon = if (secondary != null) Icons.Default.SwapCalls else Icons.Default.Pause,
                                label = if (secondary != null) "Swap" else if (primary?.phase == CallUiPhase.HOLDING) "Resume" else "Hold",
                                active = primary?.phase == CallUiPhase.HOLDING,
                                onClick = if (secondary != null) onSwapCalls else onToggleHold
                            )
                            InCallGridButton(
                                icon = Icons.Default.FiberManualRecord,
                                label = if (recordingSession.phase == RecordingPhase.Idle) "Record" else "Stop Rec",
                                active = recordingSession.phase != RecordingPhase.Idle,
                                activeTint = RecordingRed,
                                onClick = {
                                    if (recordingSession.phase == RecordingPhase.Idle) {
                                        onStartRecording()
                                    } else {
                                        onStopRecording()
                                    }
                                }
                            )
                            InCallGridButton(
                                icon = if (callState.isBluetoothAvailable) Icons.Default.BluetoothAudio else Icons.Default.CallMerge,
                                label = if (callState.isBluetoothAvailable) "Bluetooth" else "Merge",
                                active = callState.isBluetoothOn,
                                onClick = if (callState.isBluetoothAvailable) onToggleBluetooth else onMergeCalls
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    IconButton(
                        onClick = onHangup,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(RecordingRed)
                            .testTag("incall_hangup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End call",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun InCallRecordingStatusCard(
    session: ActiveRecordingSession,
    isSpeakerOn: Boolean,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    val isActive = session.phase != RecordingPhase.Idle
    val isPaused = session.phase == RecordingPhase.Paused

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isActive) RecordingRed.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isActive && !isPaused) RecordingRed else if (isPaused) WarningAmber else CyanGlow)
                    )
                    Text(
                        text = when {
                            isPaused -> "REC PAUSED · ${formatDurationMs(session.elapsedMs)}"
                            isActive -> "REC LIVE · ${formatDurationMs(session.elapsedMs)}"
                            else -> "Call Recorder Ready"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = if (isActive) RecordingRed else Color.White
                    )
                }

                if (!isActive) {
                    Button(
                        onClick = onStartRecording,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        Text("Record")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = if (isPaused) onResumeRecording else onPauseRecording
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "Resume" else "Pause",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Button(
                            onClick = onStopRecording,
                            colors = ButtonDefaults.buttonColors(containerColor = RecordingRed)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (isActive && !session.isTwoWayVerified) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSpeakerOn) {
                            "Mic Capture · Speaker ON (hearing both voices)"
                        } else {
                            "Mic Capture · Turn Speaker ON to hear caller"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSpeakerOn) VerifiedEmerald else WarningAmber,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isSpeakerOn) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = onToggleSpeaker) {
                            Text("Speaker")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InCallGridButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeTint: Color = ElectricBlue,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (active) activeTint else Color.White.copy(alpha = 0.1f),
            modifier = Modifier.size(60.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Composable
private fun InCallDtmfPad(
    onDigit: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#')
    )
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            rows.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { ch ->
                        OutlinedButton(
                            onClick = { onDigit(ch) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = ch.toString(),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

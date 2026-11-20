package com.example.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.ActiveRecordingSession
import com.example.audio.RecordingPhase
import com.example.data.AppSettings
import com.example.data.CompatibilityStatus
import com.example.telecom.DeviceContact
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.VerifiedEmerald

private data class DialPadKey(
    val digit: String,
    val letters: String,
    val tone: Int
)

private val DIAL_KEYS = listOf(
    listOf(
        DialPadKey("1", "", ToneGenerator.TONE_DTMF_1),
        DialPadKey("2", "ABC", ToneGenerator.TONE_DTMF_2),
        DialPadKey("3", "DEF", ToneGenerator.TONE_DTMF_3)
    ),
    listOf(
        DialPadKey("4", "GHI", ToneGenerator.TONE_DTMF_4),
        DialPadKey("5", "JKL", ToneGenerator.TONE_DTMF_5),
        DialPadKey("6", "MNO", ToneGenerator.TONE_DTMF_6)
    ),
    listOf(
        DialPadKey("7", "PQRS", ToneGenerator.TONE_DTMF_7),
        DialPadKey("8", "TUV", ToneGenerator.TONE_DTMF_8),
        DialPadKey("9", "WXYZ", ToneGenerator.TONE_DTMF_9)
    ),
    listOf(
        DialPadKey("*", "", ToneGenerator.TONE_DTMF_S),
        DialPadKey("0", "+", ToneGenerator.TONE_DTMF_0),
        DialPadKey("#", "", ToneGenerator.TONE_DTMF_P)
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerScreen(
    dialNumber: String,
    suggestedContacts: List<DeviceContact>,
    settings: AppSettings,
    compatibility: CompatibilityStatus,
    activeSession: ActiveRecordingSession,
    onDigitPress: (String) -> Unit,
    onZeroLongPress: () -> Unit,
    onBackspace: () -> Unit,
    onClearAll: () -> Unit,
    onSelectContact: (DeviceContact) -> Unit,
    onCallClick: () -> Unit,
    onRequestDefaultDialer: () -> Unit,
    onToggleAutoRecord: (Boolean) -> Unit,
    onStartManualRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val toneGenerator = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 65) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { toneGenerator?.release() }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Status / Default Dialer + Live Manual Recorder Strip
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!compatibility.isDefaultDialer) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = CyanGlow,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Set Default Phone App",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Enable full In-Call dialer & pickup recording",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = onRequestDefaultDialer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("set_default_dialer_button")
                            ) {
                                Text("Set")
                            }
                        }
                    }
                }

                // Compact Recorder Status Strip
                val isRecIdle = activeSession.phase == RecordingPhase.Idle
                val isRecPaused = activeSession.phase == RecordingPhase.Paused
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isRecIdle) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        RecordingRed.copy(alpha = 0.14f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isRecIdle) MaterialTheme.colorScheme.outlineVariant else RecordingRed.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isRecIdle) Icons.Default.RecordVoiceOver else Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isRecIdle) {
                                    if (settings.autoRecordEnabled) VerifiedEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    RecordingRed
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isRecIdle) {
                                    if (settings.autoRecordEnabled) {
                                        "Auto-Record ON · ${settings.audioFormat.displayName}"
                                    } else {
                                        "Auto-Record OFF · Tap Mic to record"
                                    }
                                } else {
                                    "${if (isRecPaused) "Paused" else "Recording"} · ${formatDurationMs(activeSession.elapsedMs)}"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isRecIdle) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalButton(
                                    onClick = { onToggleAutoRecord(!settings.autoRecordEnabled) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(if (settings.autoRecordEnabled) "Auto ON" else "Auto OFF")
                                }
                                FilledTonalButton(
                                    onClick = onStartManualRecording,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .testTag("dialer_manual_record_button")
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = "Start voice recording", modifier = Modifier.size(16.dp))
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(
                                    onClick = if (isRecPaused) onResumeRecording else onPauseRecording,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isRecPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = if (isRecPaused) "Resume recording" else "Pause recording"
                                    )
                                }
                                IconButton(
                                    onClick = onStopRecording,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop recording",
                                        tint = RecordingRed
                                    )
                                }
                            }
                        }
                    }
                }

                // T9 / Contact Match Suggestions
                AnimatedVisibility(visible = suggestedContacts.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(suggestedContacts, key = { "${it.id}_${it.number}" }) { contact ->
                            Surface(
                                onClick = { onSelectContact(contact) },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        text = contact.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = contact.number,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyanGlow,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Middle: Number Display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dialNumber.ifEmpty { "Enter number" },
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = if (dialNumber.length > 13) 24.sp else 32.sp
                        ),
                        color = if (dialNumber.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                        maxLines = 1,
                        modifier = Modifier.testTag("dialer_number_display")
                    )
                }

                if (dialNumber.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onBackspace()
                                },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onClearAll()
                                }
                            )
                            .testTag("dialer_backspace_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Bottom: 4x3 Keypad + Call Button
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                DIAL_KEYS.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { key ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(62.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .combinedClickable(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            runCatching { toneGenerator?.startTone(key.tone, 90) }
                                            onDigitPress(key.digit)
                                        },
                                        onLongClick = {
                                            if (key.digit == "0") {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onZeroLongPress()
                                            }
                                        }
                                    )
                                    .testTag("dial_key_${key.digit}")
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = key.digit,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (key.letters.isNotEmpty()) {
                                        Text(
                                            text = key.letters,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCallClick()
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VerifiedEmerald,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.58f)
                        .height(58.dp)
                        .testTag("dial_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Place Call",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (dialNumber.isEmpty()) "Redial" else "Call",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

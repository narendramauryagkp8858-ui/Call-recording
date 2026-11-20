package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.ActiveRecordingSession
import com.example.audio.PlaybackState
import com.example.audio.RecordingPhase
import com.example.call.CallMonitorStatus
import com.example.data.AppSettings
import com.example.data.CompatibilityStatus
import com.example.data.RecordingEntity
import com.example.ui.DashboardStats
import com.example.ui.components.RecordingCard
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatFileSize
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.WarningAmber

@Composable
fun HomeScreen(
    stats: DashboardStats,
    settings: AppSettings,
    activeSession: ActiveRecordingSession,
    playbackState: PlaybackState,
    compatibility: CompatibilityStatus,
    callMonitorStatus: CallMonitorStatus,
    onStartManual: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onToggleAutoRecord: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit,
    onShowCompatibilityDialog: () -> Unit,
    onViewAllRecordings: () -> Unit,
    onPlayToggle: (RecordingEntity) -> Unit,
    onSeekPlayback: (Long) -> Unit,
    onRenameRecording: (RecordingEntity) -> Unit,
    onShareRecording: (RecordingEntity) -> Unit,
    onDeleteRecording: (RecordingEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StudioRecorderHeroCard(
                    activeSession = activeSession,
                    settings = settings,
                    compatibility = compatibility,
                    onStartManual = onStartManual,
                    onPauseRecording = onPauseRecording,
                    onResumeRecording = onResumeRecording,
                    onStopRecording = onStopRecording
                )
            }

            item {
                CompatibilityStatusCard(
                    compatibility = compatibility,
                    onRequestPermissions = onRequestPermissions,
                    onShowDetails = onShowCompatibilityDialog
                )
            }

            item {
                AutoRecordStatusCard(
                    settings = settings,
                    callMonitorStatus = callMonitorStatus,
                    onToggleAutoRecord = onToggleAutoRecord
                )
            }

            item {
                DashboardMetricsGrid(stats = stats)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Recordings",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (stats.totalRecordings > 0) {
                        TextButton(
                            onClick = onViewAllRecordings,
                            modifier = Modifier.testTag("view_all_recordings_button")
                        ) {
                            Text("View All")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (stats.recentRecordings.isEmpty()) {
                item {
                    EmptyRecordingsBanner()
                }
            } else {
                items(stats.recentRecordings, key = { it.id }) { recording ->
                    RecordingCard(
                        recording = recording,
                        playbackState = playbackState,
                        onPlayToggle = { onPlayToggle(recording) },
                        onSeek = onSeekPlayback,
                        onRename = { onRenameRecording(recording) },
                        onShare = { onShareRecording(recording) },
                        onDelete = { onDeleteRecording(recording) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun StudioRecorderHeroCard(
    activeSession: ActiveRecordingSession,
    settings: AppSettings,
    compatibility: CompatibilityStatus,
    onStartManual: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val isRecording = activeSession.phase == RecordingPhase.Recording
    val isPaused = activeSession.phase == RecordingPhase.Paused
    val isActive = activeSession.phase != RecordingPhase.Idle

    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = when {
                isRecording -> RecordingRed.copy(alpha = 0.7f)
                isPaused -> WarningAmber.copy(alpha = 0.7f)
                else -> MaterialTheme.colorScheme.outline
            }
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_audio_banner_1795164980800),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(if (isActive) 0.24f else 0.18f)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = when {
                            isRecording -> RecordingRed.copy(alpha = 0.16f)
                            isPaused -> WarningAmber.copy(alpha = 0.16f)
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isRecording -> RecordingRed.copy(alpha = pulseAlpha)
                                            isPaused -> WarningAmber
                                            else -> CyanGlow
                                        }
                                    )
                            )
                            Text(
                                text = when {
                                    isRecording -> "RECORDING LIVE"
                                    isPaused -> "RECORDING PAUSED"
                                    else -> "STUDIO STANDBY"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = when {
                                    isRecording -> RecordingRed
                                    isPaused -> WarningAmber
                                    else -> CyanGlow
                                }
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "${settings.audioFormat.displayName} · ${settings.audioQuality.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = formatDurationMs(activeSession.elapsedMs),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("recording_timer_text")
                )

                Spacer(modifier = Modifier.height(6.dp))

                val subtitleText = if (isActive) {
                    val mode = if (activeSession.isTwoWayVerified) "Two-Way Line" else "Microphone Capture"
                    val target = activeSession.contactName.ifBlank {
                        activeSession.phoneNumber.ifBlank { activeSession.direction.label }
                    }
                    "$target · $mode"
                } else {
                    if (compatibility.isTwoWaySupported) {
                        "Ready · Two-way system capture available"
                    } else {
                        "Ready · Local microphone & speakerphone capture"
                    }
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                WaveformBars(
                    isActive = isRecording,
                    amplitude = activeSession.amplitude,
                    wavePhase = wavePhase,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                )

                AnimatedVisibility(visible = !activeSession.warningBanner.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = WarningAmber.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = activeSession.warningBanner.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = WarningAmber
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (!isActive) {
                    Button(
                        onClick = onStartManual,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("start_manual_recording_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Start Manual Recording",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = if (isPaused) onResumeRecording else onPauseRecording,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("pause_resume_recording_button")
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isPaused) "Resume" else "Pause")
                        }

                        Button(
                            onClick = onStopRecording,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RecordingRed,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("stop_recording_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop & Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaveformBars(
    isActive: Boolean,
    amplitude: Float,
    wavePhase: Float,
    modifier: Modifier = Modifier
) {
    val activeColor = CyanGlow
    val idleColor = ElectricBlue.copy(alpha = 0.28f)

    Canvas(modifier = modifier) {
        val barCount = 34
        val gap = 6.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(3f)
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val normPos = i.toFloat() / barCount
            val barFraction = if (isActive) {
                val harmonic = (kotlin.math.sin(wavePhase + normPos * 10f) + 1f) * 0.5f
                (0.18f + amplitude * 0.72f * (0.4f + 0.6f * harmonic)).coerceIn(0.14f, 1f)
            } else {
                0.15f + 0.08f * kotlin.math.sin(normPos * 8f).let { kotlin.math.abs(it) }
            }
            val barHeight = size.height * barFraction
            val x = i * (barWidth + gap)
            drawRoundRect(
                color = if (isActive) activeColor else idleColor,
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
private fun CompatibilityStatusCard(
    compatibility: CompatibilityStatus,
    onRequestPermissions: () -> Unit,
    onShowDetails: () -> Unit
) {
    val missingCorePerms = !compatibility.hasRecordAudioPermission || !compatibility.hasPhoneStatePermission
    val accent = when {
        missingCorePerms -> RecordingRed
        compatibility.isTwoWaySupported -> VerifiedEmerald
        else -> WarningAmber
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShowDetails)
            .testTag("compatibility_status_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (compatibility.isTwoWaySupported && !missingCorePerms) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.WarningAmber
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (missingCorePerms) {
                            "Permissions Required for Recording"
                        } else {
                            compatibility.compatibilityTitle
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (missingCorePerms) {
                            "Grant Microphone and Phone State permissions to enable recording and call detection."
                        } else {
                            compatibility.compatibilitySummary
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onShowDetails) {
                    Text("Compatibility Report")
                }
                if (missingCorePerms) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRequestPermissions,
                        modifier = Modifier.testTag("grant_permissions_button")
                    ) {
                        Text("Grant Access")
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoRecordStatusCard(
    settings: AppSettings,
    callMonitorStatus: CallMonitorStatus,
    onToggleAutoRecord: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (settings.autoRecordEnabled) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = if (settings.autoRecordEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Automatic Call Recording",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (settings.autoRecordEnabled) {
                        "${settings.filterMode.displayName} · ${callMonitorStatus.callState.label}"
                    } else {
                        "Disabled · Manual recording only"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = settings.autoRecordEnabled,
                onCheckedChange = onToggleAutoRecord,
                modifier = Modifier.testTag("auto_record_home_switch")
            )
        }
    }
}

@Composable
private fun DashboardMetricsGrid(stats: DashboardStats) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                icon = Icons.Default.GraphicEq,
                label = "Total Recordings",
                value = stats.totalRecordings.toString(),
                accent = ElectricBlue,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                icon = Icons.Default.Today,
                label = "Today",
                value = stats.todaysRecordings.toString(),
                accent = CyanGlow,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                icon = Icons.AutoMirrored.Filled.CallReceived,
                label = "Incoming",
                value = stats.incomingCalls.toString(),
                accent = CyanGlow,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                icon = Icons.AutoMirrored.Filled.CallMade,
                label = "Outgoing",
                value = stats.outgoingCalls.toString(),
                accent = ElectricBlue,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                icon = Icons.Default.SdStorage,
                label = "Storage",
                value = formatFileSize(stats.storageUsedBytes),
                accent = VerifiedEmerald,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptyRecordingsBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No Recordings Yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap Start Manual Recording above or enable Automatic Call Recording to save your first audio file.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.RecordingEntity
import com.example.data.RecordingRepository
import com.example.telecom.CallLogEntryType
import com.example.telecom.CallLogItem
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.VerifiedEmerald
import kotlin.math.abs

@Composable
fun CallHistoryScreen(
    callLogs: List<CallLogItem>,
    allRecordings: List<RecordingEntity>,
    hasCallLogPermission: Boolean,
    filterType: CallLogEntryType?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onFilterChange: (CallLogEntryType?) -> Unit,
    onRequestCallLogPermission: () -> Unit,
    onRefresh: () -> Unit,
    onCallNumber: (String) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onClearAllLogs: () -> Unit,
    onOpenRecording: (RecordingEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            if (!hasCallLogPermission) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Call Log Access Required",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Allow Call Log permission to view real incoming, outgoing, and missed call history and link saved call recordings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(onClick = onRequestCallLogPermission) {
                            Text("Grant Call Log Access")
                        }
                    }
                }
                return@Column
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search call history...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("call_history_search")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterType == null,
                        onClick = { onFilterChange(null) },
                        label = { Text("All") }
                    )
                    listOf(
                        CallLogEntryType.MISSED,
                        CallLogEntryType.INCOMING,
                        CallLogEntryType.OUTGOING
                    ).forEach { type ->
                        FilterChip(
                            selected = filterType == type,
                            onClick = { onFilterChange(if (filterType == type) null else type) },
                            label = { Text(type.label) }
                        )
                    }
                }

                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh call history")
                }
                if (callLogs.isNotEmpty()) {
                    IconButton(onClick = onClearAllLogs) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear call history",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (callLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No call history entries found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(callLogs, key = { it.id }) { item ->
                        val matchedRecording = allRecordings.firstOrNull { rec ->
                            RecordingRepository.numbersMatch(rec.phoneNumber, item.number) &&
                                abs(rec.createdAtMillis - item.dateMillis) < 120_000L
                        }
                        CallLogCard(
                            item = item,
                            matchedRecording = matchedRecording,
                            onCallClick = { onCallNumber(item.number) },
                            onDeleteClick = { onDeleteEntry(item.id) },
                            onRecordingClick = { matchedRecording?.let(onOpenRecording) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallLogCard(
    item: CallLogItem,
    matchedRecording: RecordingEntity?,
    onCallClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRecordingClick: () -> Unit
) {
    val (icon, tint) = when (item.type) {
        CallLogEntryType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to CyanGlow
        CallLogEntryType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to ElectricBlue
        CallLogEntryType.MISSED -> Icons.AutoMirrored.Filled.CallMissed to RecordingRed
        CallLogEntryType.REJECTED, CallLogEntryType.BLOCKED -> Icons.Default.Block to RecordingRed
        CallLogEntryType.UNKNOWN -> Icons.Default.Call to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = item.type.label,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.cachedName.ifBlank { item.number.ifBlank { "Private / Unknown" } },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (item.type == CallLogEntryType.MISSED) RecordingRed else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.cachedName.isNotBlank() && item.number.isNotBlank()) {
                        Text(
                            text = item.number,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = buildString {
                            append(formatTimestamp(item.dateMillis))
                            if (item.durationSeconds > 0L) {
                                append(" · ")
                                append(formatDurationMs(item.durationSeconds * 1000L))
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (item.number.isNotBlank()) {
                    IconButton(
                        onClick = onCallClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call ${item.number}",
                            tint = VerifiedEmerald
                        )
                    }
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete log entry",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (matchedRecording != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    onClick = onRecordingClick,
                    shape = RoundedCornerShape(10.dp),
                    color = ElectricBlue.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = CyanGlow,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Play Recording (${formatDurationMs(matchedRecording.durationMs)})",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = CyanGlow
                        )
                    }
                }
            }
        }
    }
}

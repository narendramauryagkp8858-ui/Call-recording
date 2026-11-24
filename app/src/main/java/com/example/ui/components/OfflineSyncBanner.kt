package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OfflineSyncBanner(
    isOnline: Boolean,
    pendingSyncCount: Int,
    isSyncing: Boolean,
    onBannerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = !isOnline || pendingSyncCount > 0 || isSyncing) {
        val containerColor = when {
            !isOnline -> MaterialTheme.colorScheme.secondaryContainer
            isSyncing || pendingSyncCount > 0 -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.tertiaryContainer
        }
        val contentColor = when {
            !isOnline -> MaterialTheme.colorScheme.onSecondaryContainer
            isSyncing || pendingSyncCount > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onTertiaryContainer
        }

        val headline = when {
            !isOnline && pendingSyncCount > 0 -> "Offline Mode • $pendingSyncCount pending sync"
            !isOnline -> "Offline Mode • Bills & PDFs save on device"
            isSyncing -> "Internet Connected • Syncing account..."
            else -> "$pendingSyncCount offline change(s) ready to sync"
        }

        val subtext = when {
            !isOnline -> "Keep billing offline. Everything syncs to your account automatically once internet returns."
            isSyncing -> "Uploading offline bills, stock updates & customers to your cloud account..."
            else -> "Tap Sync to upload pending changes to your account now."
        }

        Surface(
            onClick = onBannerClick,
            color = containerColor,
            modifier = modifier
                .fillMaxWidth()
                .testTag("offline_sync_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = contentColor,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (!isOnline) Icons.Default.CloudOff else Icons.Default.CloudSync,
                        contentDescription = if (!isOnline) "Offline mode" else "Cloud sync pending",
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = subtext,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor
                    )
                }
                if (isOnline && pendingSyncCount > 0 && !isSyncing) {
                    Text(
                        text = "Sync",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun AccountSyncStatusDialog(
    userEmail: String?,
    isOnline: Boolean,
    pendingSyncCount: Int,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when {
                        !isOnline -> Icons.Default.CloudOff
                        pendingSyncCount > 0 -> Icons.Default.CloudSync
                        else -> Icons.Default.CloudDone
                    },
                    contentDescription = null,
                    tint = if (!isOnline) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
                Text("Offline & Account Sync")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusLine(
                            label = "Account",
                            value = userEmail?.takeIf { it.isNotBlank() } ?: "Google Account Active"
                        )
                        StatusLine(
                            label = "Connection",
                            value = if (isOnline) "Online" else "Offline (Local Cache Active)"
                        )
                        StatusLine(
                            label = "Pending Sync",
                            value = if (pendingSyncCount == 0) "All synced (0 pending)" else "$pendingSyncCount item(s) waiting"
                        )
                    }
                }

                Text(
                    text = "You can create GST bills with IMEI & Warranty, manage stock, export A4 PDFs, and print Bluetooth receipts even without internet. As soon as internet reconnects, all saved bills and stock changes automatically sync to your account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (isOnline) {
                Button(
                    onClick = onSyncNow,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("sync_now_dialog_button")
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Syncing...")
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sync Now")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_sync_status_button")
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

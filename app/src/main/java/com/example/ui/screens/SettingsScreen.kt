package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import com.example.data.AudioFormatOption
import com.example.data.AudioQualityOption
import com.example.data.AutoRecordFilterMode
import com.example.data.CompatibilityStatus
import com.example.data.ContactRuleEntity
import com.example.data.ContactRuleType
import com.example.ui.components.formatFileSize
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.WarningAmber

@Composable
fun SettingsScreen(
    settings: AppSettings,
    contactRules: List<ContactRuleEntity>,
    compatibility: CompatibilityStatus,
    carrierStatusMessage: String?,
    storageUsedBytes: Long,
    isBackupBusy: Boolean,
    isBiometricHardwareAvailable: Boolean,
    onRequestDefaultDialer: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onOpenOemAutoStart: () -> Unit,
    onOpenSystemCallSettings: () -> Unit,
    onCallWaitingChange: (Boolean) -> Unit,
    onCheckCallWaitingStatus: () -> Unit,
    onOpenCallForwardingDialog: () -> Unit,
    onCancelAllCallForwarding: () -> Unit,
    onToggleAutoRecord: (Boolean) -> Unit,
    onToggleAutoIncoming: (Boolean) -> Unit,
    onToggleAutoOutgoing: (Boolean) -> Unit,
    onSelectFilterMode: (AutoRecordFilterMode) -> Unit,
    onAddContactRuleClick: (ContactRuleType) -> Unit,
    onDeleteContactRule: (Long) -> Unit,
    onSelectFormat: (AudioFormatOption) -> Unit,
    onSelectQuality: (AudioQualityOption) -> Unit,
    onTogglePreferTwoWay: (Boolean) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onTogglePinLock: (Boolean) -> Unit,
    onToggleBiometricLock: (Boolean) -> Unit,
    onLockNow: () -> Unit,
    onToggleRecordingNotif: (Boolean) -> Unit,
    onToggleSavedNotif: (Boolean) -> Unit,
    onShowCompatibilityDialog: () -> Unit,
    onShowConsentDialog: () -> Unit,
    onRequestExportBackup: () -> Unit,
    onRequestRestoreBackup: () -> Unit,
    onRequestDeleteAll: () -> Unit,
    onRequestResetAppData: () -> Unit,
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Default Phone App & OPPO / Vivo / Xiaomi Compatibility
            item {
                SettingsSectionCard(title = "Phone Dialer & Device Compatibility") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Default Phone App", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (compatibility.isDefaultDialer) {
                                    "Active · Using Narendra Phone In-Call Dialer"
                                } else {
                                    "Not default · Set as default for instant pickup call recording"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (compatibility.isDefaultDialer) VerifiedEmerald else WarningAmber
                            )
                        }
                        if (!compatibility.isDefaultDialer) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onRequestDefaultDialer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("settings_set_default_dialer_button")
                            ) {
                                Text("Set Default")
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Background Battery Saver", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (compatibility.isBatteryOptimizationIgnored) {
                                    "Unrestricted · Safe for background calls on ${compatibility.manufacturer}"
                                } else {
                                    "Optimized · Disable battery limits for OPPO/Vivo/Xiaomi/MIUI"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (compatibility.isBatteryOptimizationIgnored) VerifiedEmerald else WarningAmber
                            )
                        }
                        if (!compatibility.isBatteryOptimizationIgnored) {
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = onOpenBatteryOptimization,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("settings_battery_opt_button")
                            ) {
                                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Allow")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenOemAutoStart,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_oem_autostart_button")
                        ) {
                            Icon(Icons.Default.SettingsApplications, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("OEM Auto-Start")
                        }
                        OutlinedButton(
                            onClick = onShowCompatibilityDialog,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_compatibility_button")
                        ) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compatibility")
                        }
                    }
                }
            }

            // 2. Carrier Call Settings: Call Waiting & Call Forwarding
            item {
                SettingsSectionCard(title = "Carrier Call Settings (Forwarding & Waiting)") {
                    Text(
                        text = "Call Waiting (GSM MMI / USSD)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Receive alerts when a second call arrives during an active call.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onCallWaitingChange(true) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("call_waiting_enable_button")
                        ) {
                            Text("Enable (*43#)")
                        }
                        OutlinedButton(
                            onClick = { onCallWaitingChange(false) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("call_waiting_disable_button")
                        ) {
                            Text("Disable (#43#)")
                        }
                        OutlinedButton(
                            onClick = onCheckCallWaitingStatus,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("call_waiting_status_button")
                        ) {
                            Text("Status")
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        text = "Call Forwarding",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Forward incoming calls Always (*21*), When Busy (*67*), Unanswered (*61*), or Unreachable (*62*).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenCallForwardingDialog,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_call_forwarding_button")
                        ) {
                            Icon(Icons.Default.PhoneForwarded, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Configure Forwarding")
                        }
                        OutlinedButton(
                            onClick = onCancelAllCallForwarding,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("cancel_all_forwarding_button")
                        ) {
                            Text("Cancel All")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onOpenSystemCallSettings,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_system_call_settings_button")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open System SIM & Network Call Settings")
                    }

                    if (!carrierStatusMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, CyanGlow.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = null,
                                    tint = CyanGlow,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = carrierStatusMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CyanGlow
                                )
                            }
                        }
                    }
                }
            }

            // 3. Automatic Call Recording
            item {
                SettingsSectionCard(title = "Automatic Call Recording") {
                    SettingsSwitchRow(
                        title = "Enable Automatic Recording",
                        subtitle = "Auto-record when incoming or outgoing calls connect",
                        checked = settings.autoRecordEnabled,
                        onCheckedChange = onToggleAutoRecord,
                        testTag = "settings_auto_record_switch"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsSwitchRow(
                        title = "Record Incoming Calls",
                        subtitle = "Automatically capture when incoming calls are answered",
                        checked = settings.autoRecordIncoming,
                        enabled = settings.autoRecordEnabled,
                        onCheckedChange = onToggleAutoIncoming
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsSwitchRow(
                        title = "Record Outgoing Calls",
                        subtitle = "Automatically capture when outgoing calls connect",
                        checked = settings.autoRecordOutgoing,
                        enabled = settings.autoRecordEnabled,
                        onCheckedChange = onToggleAutoOutgoing
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Contact Rule Mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    AutoRecordFilterMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectFilterMode(mode) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.filterMode == mode,
                                onClick = { onSelectFilterMode(mode) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mode.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    mode.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (settings.filterMode != AutoRecordFilterMode.ALL_CALLS) {
                        val activeRuleType = if (settings.filterMode == AutoRecordFilterMode.SELECTED_ONLY) {
                            ContactRuleType.INCLUDE
                        } else {
                            ContactRuleType.EXCLUDE
                        }
                        val filteredRules = contactRules.filter { it.ruleType == activeRuleType }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (activeRuleType == ContactRuleType.INCLUDE) {
                                    "Included Contacts (${filteredRules.size})"
                                } else {
                                    "Excluded Contacts (${filteredRules.size})"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            OutlinedButton(
                                onClick = { onAddContactRuleClick(activeRuleType) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Contact")
                            }
                        }

                        if (filteredRules.isEmpty()) {
                            Text(
                                text = "No contacts added yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            filteredRules.forEach { rule ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rule.displayName, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            rule.phoneNumber,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onDeleteContactRule(rule.id) }) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Remove contact rule",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Audio Format & Quality
            item {
                SettingsSectionCard(title = "Audio Format & Quality") {
                    Text(
                        text = "Container Format",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    AudioFormatOption.entries.forEach { format ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectFormat(format) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.audioFormat == format,
                                onClick = { onSelectFormat(format) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(format.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    format.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        text = "Sample Rate & Bitrate",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    AudioQualityOption.entries.forEach { quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectQuality(quality) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.audioQuality == quality,
                                onClick = { onSelectQuality(quality) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(quality.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    quality.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    SettingsSwitchRow(
                        title = "Prefer Two-Way Call Line When Permitted",
                        subtitle = if (compatibility.hasPrivilegedCaptureAudioOutput) {
                            "Device permits CAPTURE_AUDIO_OUTPUT for two-way line"
                        } else {
                            "Standard Android restricts VOICE_CALL; falls back cleanly to Microphone (use Speaker toggle on Call Screen for caller audio)"
                        },
                        checked = settings.preferTwoWayAttempt,
                        onCheckedChange = onTogglePreferTwoWay
                    )
                }
            }

            // 5. Privacy Lock & Appearance
            item {
                SettingsSectionCard(title = "Privacy Lock & Appearance") {
                    SettingsSwitchRow(
                        title = "Dark Mode (Blue & Black)",
                        subtitle = "Switch between Midnight Obsidian and Light Cobalt themes",
                        icon = Icons.Default.DarkMode,
                        checked = settings.darkModeEnabled,
                        onCheckedChange = onToggleDarkMode,
                        testTag = "settings_dark_mode_switch"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsSwitchRow(
                        title = "PIN Lock (4-Digit)",
                        subtitle = "Require PIN when opening Narendra Phone",
                        icon = Icons.Default.Lock,
                        checked = settings.pinLockEnabled,
                        onCheckedChange = onTogglePinLock,
                        testTag = "settings_pin_switch"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsSwitchRow(
                        title = "Fingerprint / Biometric Lock",
                        subtitle = if (isBiometricHardwareAvailable) {
                            "Use device fingerprint or biometric authentication"
                        } else {
                            "No biometric hardware enrolled on this device"
                        },
                        icon = Icons.Default.Fingerprint,
                        checked = settings.biometricLockEnabled,
                        enabled = isBiometricHardwareAvailable,
                        onCheckedChange = onToggleBiometricLock
                    )
                    if (settings.pinLockEnabled || settings.biometricLockEnabled) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = onLockNow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Lock App Now")
                        }
                    }
                }
            }

            // 6. Notifications
            item {
                SettingsSectionCard(title = "Notifications") {
                    SettingsSwitchRow(
                        title = "Live Recording Notification",
                        subtitle = "Required by Android during foreground microphone recording",
                        icon = Icons.Default.Notifications,
                        checked = settings.showRecordingNotification,
                        onCheckedChange = onToggleRecordingNotif
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsSwitchRow(
                        title = "Saved Recording Alert",
                        subtitle = "Show confirmation notification when a recording completes",
                        icon = Icons.Default.Notifications,
                        checked = settings.showSavedNotification,
                        onCheckedChange = onToggleSavedNotif
                    )
                }
            }

            // 7. Storage, Backup & Reset
            item {
                SettingsSectionCard(title = "Storage, Backup & Reset") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Local Recordings Storage", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Used: ${formatFileSize(storageUsedBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(onClick = onRequestDeleteAll) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Audio", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    if (isBackupBusy) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Processing backup archive...")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onRequestExportBackup,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_backup_button")
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export ZIP")
                            }
                            OutlinedButton(
                                onClick = onRequestRestoreBackup,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("restore_backup_button")
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore ZIP")
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onShowConsentDialog,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Consent Info")
                        }
                        OutlinedButton(
                            onClick = onRequestResetAppData,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reset_app_data_button")
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Data")
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Narendra Phone · v2.0",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Developed by Narendra · ${compatibility.manufacturer} · Android ${compatibility.androidVersionName} (API ${compatibility.apiLevel})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}

package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CompatibilityStatus
import com.example.data.ContactRuleType
import com.example.telecom.ForwardCondition
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.WarningAmber

@Composable
fun ConsentDialog(
    onAccept: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.consent_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.consent_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                modifier = Modifier.testTag("consent_accept_button")
            ) {
                Text(stringResource(R.string.consent_accept))
            }
        },
        dismissButton = if (onDismiss != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        } else {
            null
        }
    )
}

@Composable
fun CompatibilityDetailsDialog(
    status: CompatibilityStatus,
    onRequestPermissions: () -> Unit,
    onRequestDefaultDialer: () -> Unit,
    onOpenAutoStart: () -> Unit,
    onDismiss: () -> Unit
) {
    val missingCorePermissions = !status.hasRecordAudioPermission ||
        !status.hasPhoneStatePermission ||
        !status.hasCallPhonePermission ||
        !status.hasCallLogPermission

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (status.isTwoWaySupported || status.isDefaultDialer) {
                    Icons.Default.CheckCircle
                } else {
                    Icons.Default.WarningAmber
                },
                contentDescription = null,
                tint = if (status.isTwoWaySupported) VerifiedEmerald else WarningAmber
            )
        },
        title = {
            Text(
                text = "Device Compatibility Report",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${status.manufacturer} · Android ${status.androidVersionName} (API ${status.apiLevel})",
                    style = MaterialTheme.typography.labelLarge,
                    color = CyanGlow
                )
                Text(
                    text = status.compatibilityTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (status.isTwoWaySupported) VerifiedEmerald else WarningAmber
                )
                Text(
                    text = status.compatibilitySummary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "OEM & Background Behavior",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = status.backgroundRestrictionNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))
                PermissionStatusRow("Default Phone App (ROLE_DIALER)", status.isDefaultDialer)
                PermissionStatusRow("Battery Unrestricted", status.isBatteryOptimizationIgnored)
                PermissionStatusRow("Microphone (RECORD_AUDIO)", status.hasRecordAudioPermission)
                PermissionStatusRow("Place Calls (CALL_PHONE)", status.hasCallPhonePermission)
                PermissionStatusRow("Phone State (READ_PHONE_STATE)", status.hasPhoneStatePermission)
                PermissionStatusRow("Call History Read (READ_CALL_LOG)", status.hasCallLogPermission)
                PermissionStatusRow("Call History Manage (WRITE_CALL_LOG)", status.hasWriteCallLogPermission)
                PermissionStatusRow("Contacts Lookup (READ_CONTACTS)", status.hasContactsPermission)
                PermissionStatusRow("Notifications (POST_NOTIFICATIONS)", status.hasNotificationPermission)
                PermissionStatusRow("System Two-Way (CAPTURE_AUDIO_OUTPUT)", status.hasPrivilegedCaptureAudioOutput)

                if (!status.isDefaultDialer) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onRequestDefaultDialer()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set Narendra Phone as Default Dialer")
                    }
                }
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenAutoStart()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open OPPO / Vivo / Xiaomi Auto-Start")
                }
            }
        },
        confirmButton = {
            if (missingCorePermissions) {
                Button(onClick = {
                    onDismiss()
                    onRequestPermissions()
                }) {
                    Text("Grant Permissions")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("OK")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun PermissionStatusRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (granted) "Ready" else "Missing",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (granted) VerifiedEmerald else WarningAmber
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallForwardingDialog(
    carrierStatusMessage: String?,
    onActivate: (ForwardCondition, String) -> Unit,
    onDeactivate: (ForwardCondition) -> Unit,
    onCheckStatus: (ForwardCondition) -> Unit,
    onCancelAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCondition by remember { mutableStateOf(ForwardCondition.ALWAYS) }
    var forwardNumber by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.PhoneForwarded,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Call Forwarding (Carrier MMI)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Select a forwarding condition and enter the target phone number. Commands are executed via GSM MMI/USSD codes directly with your carrier.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ForwardCondition.entries.forEach { cond ->
                        FilterChip(
                            selected = selectedCondition == cond,
                            onClick = { selectedCondition = cond },
                            label = { Text(cond.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = forwardNumber,
                    onValueChange = { input ->
                        forwardNumber = input.filter { it.isDigit() || it in setOf('+', ' ', '-') }
                    },
                    label = { Text("Forward To Number") },
                    placeholder = { Text("+919876543210") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("call_forward_number_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onCheckStatus(selectedCondition) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Check (${selectedCondition.statusCode})")
                    }
                    OutlinedButton(
                        onClick = { onDeactivate(selectedCondition) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Turn Off")
                    }
                }

                OutlinedButton(
                    onClick = onCancelAll,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel All Forwarding (##002#)")
                }

                if (!carrierStatusMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = carrierStatusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CyanGlow,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onActivate(selectedCondition, forwardNumber) },
                enabled = forwardNumber.any { it.isDigit() },
                modifier = Modifier.testTag("call_forward_activate_button")
            ) {
                Text("Activate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun RenameRecordingDialog(
    initialTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Recording") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Recording title") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title) },
                enabled = title.trim().isNotEmpty(),
                modifier = Modifier.testTag("rename_confirm_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.testTag("delete_confirm_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddContactRuleDialog(
    ruleType: ContactRuleType,
    onPickFromContacts: () -> Unit,
    onAddManual: (number: String, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (ruleType == ContactRuleType.INCLUDE) {
                    "Add Included Contact"
                } else {
                    "Add Excluded Contact"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onPickFromContacts()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick from Phone Contacts")
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Contact Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAddManual(phone, name) },
                enabled = phone.any { it.isDigit() }
            ) {
                Text("Add Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SetPinDialog(
    onConfirmPin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val valid = pin.length == 4 && pin == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set 4-Digit App PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                    label = { Text("Enter 4-digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) confirm = it },
                    label = { Text("Confirm 4-digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (confirm.length == 4 && pin != confirm) {
                    Text(
                        text = "PINs do not match",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirmPin(pin) }, enabled = valid) {
                Text("Enable PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BackupConsentDialog(
    isExport: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text(if (isExport) "Export Recordings Backup" else "Restore Recordings Backup")
        },
        text = {
            Text(
                text = if (isExport) {
                    "You are about to export your call recordings and metadata into a local ZIP file at a location you choose. No data is uploaded automatically to any server."
                } else {
                    "Select a previously exported Narendra Phone ZIP backup to restore audio files and metadata onto this device."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(if (isExport) "Choose Save Location" else "Select Backup ZIP")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

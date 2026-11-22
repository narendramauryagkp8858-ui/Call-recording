package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.billing.GstCalculator
import com.example.billing.WarrantyEvaluation
import com.example.billing.WarrantyManager
import com.example.data.Invoice

@Composable
fun WarrantyExpiryAlertBanner(
    expiring: List<Pair<Invoice, WarrantyEvaluation>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (expiring.isEmpty()) return
    val urgentCount = expiring.count { it.second.daysRemaining <= 2 }
    val containerColor = if (urgentCount > 0) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (urgentCount > 0) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    val preview = expiring.take(2).joinToString(" • ") { (inv, eval) ->
        val dayTxt = when (eval.daysRemaining) {
            0 -> "Today"
            1 -> "1d left"
            else -> "${eval.daysRemaining}d left"
        }
        "${inv.invoiceNumber} (${inv.customerName}: $dayTxt)"
    }
    val extra = if (expiring.size > 2) " +${expiring.size - 2} more" else ""

    Surface(
        onClick = onClick,
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .testTag("warranty_expiry_alert_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = "Warranty expiry notification",
                tint = contentColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Warranty Expiry Alert (${expiring.size} ${if (expiring.size == 1) "bill" else "bills"})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = preview + extra,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "View",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
fun WarrantyNotificationsDialog(
    expiring: List<Pair<Invoice, WarrantyEvaluation>>,
    hasSystemNotificationPermission: Boolean,
    onRequestSystemNotificationPermission: () -> Unit,
    onDismiss: () -> Unit,
    onPreviewPdf: (Invoice) -> Unit,
    onGoToBills: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Warranty Alerts (Next 7 Days)")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!hasSystemNotificationPermission) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Allow phone notifications for 7-day warranty reminders.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onRequestSystemNotificationPermission,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("enable_phone_notifications_button")
                            ) {
                                Text("Allow")
                            }
                        }
                    }
                }

                if (expiring.isEmpty()) {
                    Text(
                        "No bill warranties are expiring in the next 7 days. Expired warranties are automatically cleared from bills while keeping all bill history safe.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(expiring, key = { it.first.id }) { (invoice, eval) ->
                            val badgeBg = if (eval.daysRemaining <= 2) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                            val badgeFg = if (eval.daysRemaining <= 2) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                            val dayText = when (eval.daysRemaining) {
                                0 -> "Expires Today"
                                1 -> "1 Day Left"
                                else -> "${eval.daysRemaining} Days Left"
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("warranty_notification_item_${invoice.id}")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        MonoCodeText(
                                            text = "${invoice.invoiceNumber} • ${GstCalculator.formatRupees(invoice.grandTotal)}",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            color = badgeBg,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = dayText,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeFg,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = invoice.customerName + if (invoice.customerPhone.isNotBlank()) " (${invoice.customerPhone})" else "",
                                        style = MaterialTheme.typography.titleSmall
                                    )

                                    Text(
                                        text = "${eval.displayLabel} • Ends ${WarrantyManager.formatExpiryDate(eval.expiryDateMillis)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                onDismiss()
                                                onPreviewPdf(invoice)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("notif_view_pdf_${invoice.id}"),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("View Bill")
                                        }
                                        Button(
                                            onClick = {
                                                onDismiss()
                                                onGoToBills()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("notif_open_bills_${invoice.id}"),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Text("Bills History")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_warranty_notifications_button")
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
fun WarrantyOptionSection(
    enabled: Boolean,
    months: Int,
    days: Int,
    note: String,
    referenceStartMillis: Long = System.currentTimeMillis(),
    onToggle: (Boolean) -> Unit,
    onPresetSelected: (months: Int, days: Int) -> Unit,
    onCustomDurationChanged: (months: Int, days: Int) -> Unit,
    onNoteChanged: (String) -> Unit
) {
    var showCustomInputs by remember(enabled) {
        mutableStateOf(
            enabled && listOf(
                0 to 7,
                1 to 0,
                3 to 0,
                6 to 0,
                12 to 0,
                24 to 0
            ).none { it.first == months && it.second == days }
        )
    }

    var monthsText by remember(months) { mutableStateOf(if (months > 0) months.toString() else "") }
    var daysText by remember(days) { mutableStateOf(if (days > 0) days.toString() else "") }

    val presets = listOf(
        Triple("7 Days", 0, 7),
        Triple("1 Month", 1, 0),
        Triple("3 Months", 3, 0),
        Triple("6 Months", 6, 0),
        Triple("1 Year", 12, 0),
        Triple("2 Years", 24, 0)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Warranty Option", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "7-day pre-expiry alert & auto-remove after expiry",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("warranty_toggle")
            )
        }

        if (enabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { (label, m, d) ->
                    val selected = !showCustomInputs && months == m && days == d
                    FilterChip(
                        selected = selected,
                        onClick = {
                            showCustomInputs = false
                            monthsText = if (m > 0) m.toString() else ""
                            daysText = if (d > 0) d.toString() else ""
                            onPresetSelected(m, d)
                        },
                        label = { Text(label) },
                        modifier = Modifier.testTag("warranty_preset_${m}m_${d}d")
                    )
                }
                FilterChip(
                    selected = showCustomInputs,
                    onClick = { showCustomInputs = true },
                    label = { Text("Custom") },
                    modifier = Modifier.testTag("warranty_preset_custom")
                )
            }

            if (showCustomInputs) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monthsText,
                        onValueChange = { raw ->
                            val clean = raw.filter { it.isDigit() }.take(4)
                            monthsText = clean
                            val parsedM = (clean.toIntOrNull() ?: 0).coerceIn(0, 1200)
                            val parsedD = (daysText.toIntOrNull() ?: 0).coerceIn(0, 36500)
                            onCustomDurationChanged(parsedM, parsedD)
                        },
                        label = { Text("Months") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("warranty_custom_months_input")
                    )
                    OutlinedTextField(
                        value = daysText,
                        onValueChange = { raw ->
                            val clean = raw.filter { it.isDigit() }.take(5)
                            daysText = clean
                            val parsedM = (monthsText.toIntOrNull() ?: 0).coerceIn(0, 1200)
                            val parsedD = (clean.toIntOrNull() ?: 0).coerceIn(0, 36500)
                            onCustomDurationChanged(parsedM, parsedD)
                        },
                        label = { Text("Days") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("warranty_custom_days_input")
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = onNoteChanged,
                label = { Text("Warranty Note (e.g. Replacement / Service)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("warranty_note_input")
            )

            val previewEndMillis = WarrantyManager.computeWarrantyEndMillis(
                startMillis = referenceStartMillis,
                months = months,
                days = days
            )
            if (previewEndMillis > 0L) {
                val previewLabel = WarrantyManager.formatWarrantyLabel(months, days, note)
                val expiryStr = WarrantyManager.formatExpiryDate(previewEndMillis)
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("warranty_preview_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "$previewLabel • Valid till $expiryStr",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Select or enter at least 1 day/month for warranty.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

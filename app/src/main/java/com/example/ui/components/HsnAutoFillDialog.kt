package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.billing.GstCalculator
import com.example.billing.HsnAutoFillHelper
import com.example.data.ProductHsnUpdateEntry
import com.example.data.ProductItem

@Composable
fun AutoFillHsnDialog(
    products: List<ProductItem>,
    onDismiss: () -> Unit,
    onApply: (List<ProductHsnUpdateEntry>) -> Unit
) {
    var overwriteExisting by remember { mutableStateOf(false) }
    var alsoUpdateGstRate by remember { mutableStateOf(false) }
    var applyDefaultForUnmatched by remember { mutableStateOf(false) }
    val manualEdits = remember { mutableStateMapOf<String, String>() }

    val plan = remember(products, overwriteExisting, alsoUpdateGstRate, applyDefaultForUnmatched) {
        HsnAutoFillHelper.buildBatchPlan(
            products = products,
            overwriteExisting = overwriteExisting,
            alsoUpdateGstRate = alsoUpdateGstRate,
            applyDefaultIfUnmatched = applyDefaultForUnmatched
        )
    }

    val matchedCount = remember(plan) { plan.count { it.matched } }
    val missingCount = remember(products) { products.count { it.hsnCode.isBlank() } }

    val readyUpdates = plan.mapNotNull { item ->
        val chosenHsn = (manualEdits[item.product.id] ?: item.suggestedHsn)
            .trim()
            .uppercase()
            .take(20)
        if (chosenHsn.isBlank()) {
            null
        } else if (chosenHsn == item.currentHsn && item.suggestedGstRate == null) {
            null
        } else {
            ProductHsnUpdateEntry(
                productId = item.product.id,
                hsnCode = chosenHsn,
                gstRate = item.suggestedGstRate
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.AutoFixHigh,
                contentDescription = "Auto Fill HSN",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Auto Fill HSN — All Products") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Total: ${products.size} items • Missing HSN: $missingCount",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Auto-matched: $matchedCount • Ready to apply: ${readyUpdates.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !overwriteExisting,
                        onClick = {
                            overwriteExisting = false
                            manualEdits.clear()
                        },
                        label = { Text("Fill Empty Only ($missingCount)") },
                        leadingIcon = if (!overwriteExisting) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier.testTag("hsn_mode_empty_chip")
                    )
                    FilterChip(
                        selected = overwriteExisting,
                        onClick = {
                            overwriteExisting = true
                            manualEdits.clear()
                        },
                        label = { Text("Update All (${products.size})") },
                        leadingIcon = if (overwriteExisting) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier.testTag("hsn_mode_overwrite_chip")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Also update standard GST % slab",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = alsoUpdateGstRate,
                        onCheckedChange = { alsoUpdateGstRate = it },
                        modifier = Modifier.testTag("hsn_update_gst_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Use default HSN (9997) for unmatched",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = applyDefaultForUnmatched,
                        onCheckedChange = {
                            applyDefaultForUnmatched = it
                            manualEdits.clear()
                        },
                        modifier = Modifier.testTag("hsn_default_unmatched_switch")
                    )
                }

                HorizontalDivider()

                if (plan.isEmpty()) {
                    Text(
                        text = "All products already have HSN codes filled. Tap \"Update All\" above if you want to re-detect HSN codes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(plan, key = { it.product.id }) { item ->
                            val currentValue = manualEdits[item.product.id] ?: item.suggestedHsn
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.product.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (item.matched) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                        val existingLabel = if (item.currentHsn.isBlank()) "Current: Empty" else "Old: ${item.currentHsn}"
                                        val gstHint = item.suggestedGstRate?.let {
                                            " • GST ${GstCalculator.formatPlainNumber(it)}%"
                                        } ?: ""
                                        MonoCodeText(
                                            text = "$existingLabel$gstHint",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedTextField(
                                        value = currentValue,
                                        onValueChange = { next ->
                                            manualEdits[item.product.id] = next.uppercase().take(20)
                                        },
                                        label = { Text("HSN") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .width(104.dp)
                                            .testTag("hsn_preview_input_${item.product.id}")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(readyUpdates) },
                enabled = readyUpdates.isNotEmpty(),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_auto_fill_hsn_button")
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Apply HSN (${readyUpdates.size})")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

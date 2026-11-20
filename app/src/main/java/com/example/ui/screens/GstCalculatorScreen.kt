package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.example.billing.GstCalculator
import com.example.ui.components.SectionHeader

@Composable
fun GstCalculatorScreen(
    onAddToBill: (amount: Double, gstPercent: Double, isInclusive: Boolean, isInterState: Boolean) -> Unit
) {
    var amountInput by remember { mutableStateOf("1000") }
    var gstPercentInput by remember { mutableStateOf("18") }
    var isInclusive by remember { mutableStateOf(false) }
    var isInterState by remember { mutableStateOf(false) }

    val slabs = listOf("0", "5", "12", "18", "28")
    val parsedAmount = amountInput.toDoubleOrNull() ?: 0.0
    val parsedRate = gstPercentInput.toDoubleOrNull() ?: 0.0

    val result = remember(parsedAmount, parsedRate, isInclusive, isInterState) {
        GstCalculator.calculateSingleAmount(
            amount = parsedAmount,
            gstPercent = parsedRate,
            isInclusive = isInclusive,
            isInterState = isInterState
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "GST Calculator",
                subtitle = "Instant Exclusive (+GST) & Inclusive (Extract GST) breakdown"
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !isInclusive,
                            onClick = { isInclusive = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier.testTag("gst_mode_exclusive")
                        ) {
                            Text("Add GST (Exclusive)")
                        }
                        SegmentedButton(
                            selected = isInclusive,
                            onClick = { isInclusive = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier.testTag("gst_mode_inclusive")
                        ) {
                            Text("Remove GST (Inclusive)")
                        }
                    }

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text(if (isInclusive) "Total Amount (Inclusive of GST) ₹" else "Net / Base Amount ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gst_calc_amount_input")
                    )

                    Text("GST Tax Slab", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        slabs.forEach { slab ->
                            FilterChip(
                                selected = gstPercentInput == slab,
                                onClick = { gstPercentInput = slab },
                                label = { Text("$slab%") },
                                modifier = Modifier.testTag("gst_slab_$slab")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = gstPercentInput,
                        onValueChange = { gstPercentInput = it },
                        label = { Text("Custom GST %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !isInterState,
                            onClick = { isInterState = false },
                            label = { Text("Intra-State (CGST + SGST)") }
                        )
                        FilterChip(
                            selected = isInterState,
                            onClick = { isInterState = true },
                            label = { Text("Inter-State (IGST)") }
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "GST Breakdown (${GstCalculator.formatPlainNumber(result.gstPercent)}%)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    CalcRow("Base / Taxable Value", GstCalculator.formatRupees(result.baseAmount))

                    if (isInterState) {
                        CalcRow(
                            "IGST (${GstCalculator.formatPlainNumber(result.gstPercent)}%)",
                            GstCalculator.formatRupees(result.igstAmount)
                        )
                    } else {
                        val half = GstCalculator.formatPlainNumber(result.gstPercent / 2.0)
                        CalcRow("CGST ($half%)", GstCalculator.formatRupees(result.cgstAmount))
                        CalcRow("SGST ($half%)", GstCalculator.formatRupees(result.sgstAmount))
                    }

                    CalcRow("Total GST Tax", GstCalculator.formatRupees(result.totalGstAmount), bold = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Final Total",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            GstCalculator.formatRupees(result.grandTotal),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Text(
                        text = GstCalculator.amountToIndianWords(result.grandTotal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                amountInput = ""
                                gstPercentInput = "18"
                            },
                            modifier = Modifier
                                .weight(0.8f)
                                .heightIn(min = 48.dp)
                                .testTag("gst_calc_reset_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset")
                        }
                        Button(
                            onClick = {
                                onAddToBill(parsedAmount, parsedRate, isInclusive, isInterState)
                            },
                            enabled = parsedAmount > 0.0,
                            modifier = Modifier
                                .weight(1.4f)
                                .heightIn(min = 48.dp)
                                .testTag("gst_calc_use_in_bill_button")
                        ) {
                            Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add to New Bill")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

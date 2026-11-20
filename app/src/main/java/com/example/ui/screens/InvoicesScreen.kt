package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.billing.GstCalculator
import com.example.data.Invoice
import com.example.data.PaymentMode
import com.example.ui.BillingViewModel
import com.example.ui.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader
import com.example.ui.theme.WhatsAppGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InvoicesScreen(
    viewModel: BillingViewModel,
    invoicesState: UiState<List<Invoice>>,
    onGoToNewBill: () -> Unit,
    onPreviewPdf: (Invoice) -> Unit,
    onShareWhatsApp: (Invoice) -> Unit,
    onPrintThermal: (Invoice) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var editingOptionsInvoice by remember { mutableStateOf<Invoice?>(null) }
    var deletingInvoice by remember { mutableStateOf<Invoice?>(null) }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "GST Bills History",
                subtitle = "Preview PDF, share on WhatsApp, or print via Bluetooth"
            )
        }

        when (invoicesState) {
            is UiState.Loading -> {
                item {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Loading saved GST bills...")
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = invoicesState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                val all = invoicesState.data
                val totalSales = all.sumOf { it.grandTotal }
                val totalProfit = all.sumOf { it.estimatedProfit }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Total Billed (${all.size})", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    GstCalculator.formatRupees(totalSales),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Private Profit (Shopkeeper)", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    GstCalculator.formatRupees(totalProfit),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search bill no., customer, mobile or shop") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_invoices_input")
                    )
                }

                val filtered = all.filter { inv ->
                    if (searchQuery.isBlank()) true
                    else {
                        inv.invoiceNumber.contains(searchQuery, true) ||
                            inv.customerName.contains(searchQuery, true) ||
                            inv.customerPhone.contains(searchQuery, true) ||
                            inv.shopName.contains(searchQuery, true)
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = if (all.isEmpty()) "No GST Bills Yet" else "No Matching Bills",
                            description = if (all.isEmpty()) "Generate your first GST invoice to share PDF on WhatsApp or print." else "Try a different bill number or customer name.",
                            actionLabel = if (all.isEmpty()) "Create First Bill" else null,
                            onAction = if (all.isEmpty()) onGoToNewBill else null
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { inv ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        MonoCodeText(
                                            text = "${inv.invoiceNumber} • ${inv.shopName}",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = inv.customerName,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = buildString {
                                                append(dateFormatter.format(Date(inv.issuedAtMillis)))
                                                if (inv.customerPhone.isNotBlank()) append(" • ${inv.customerPhone}")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = GstCalculator.formatRupees(inv.grandTotal),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${inv.items.size} items • ${PaymentMode.fromRaw(inv.paymentMode).label}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val stampSignLabel = when {
                                        inv.includeStamp && inv.includeSignature -> "Mohar + Sign"
                                        inv.includeStamp -> "Mohar Only"
                                        inv.includeSignature -> "Sign Only"
                                        else -> "No Stamp/Sign"
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = stampSignLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(
                                        onClick = { editingOptionsInvoice = inv },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = "Edit stamp/sign options")
                                    }
                                    IconButton(
                                        onClick = { deletingInvoice = inv },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete bill",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onPreviewPdf(inv) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("invoice_pdf_${inv.id}"),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("PDF")
                                    }
                                    OutlinedButton(
                                        onClick = { onPrintThermal(inv) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("invoice_print_${inv.id}"),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Print")
                                    }
                                    Button(
                                        onClick = { onShareWhatsApp(inv) },
                                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .heightIn(min = 48.dp)
                                            .testTag("invoice_whatsapp_${inv.id}"),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("WhatsApp")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingOptionsInvoice?.let { inv ->
        var stamp by remember(inv.id) { mutableStateOf(inv.includeStamp) }
        var sign by remember(inv.id) { mutableStateOf(inv.includeSignature) }
        var mode by remember(inv.id) { mutableStateOf(PaymentMode.fromRaw(inv.paymentMode)) }
        var notes by remember(inv.id) { mutableStateOf(inv.notes) }

        AlertDialog(
            onDismissRequest = { editingOptionsInvoice = null },
            title = { Text("Bill Options (${inv.invoiceNumber})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = stamp, onCheckedChange = { stamp = it })
                        Text("Include Shop Mohar (Stamp) on PDF")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = sign, onCheckedChange = { sign = it })
                        Text("Include Signature on PDF")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaymentMode.entries.forEach { pm ->
                            FilterChip(
                                selected = mode == pm,
                                onClick = { mode = pm },
                                label = { Text(pm.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateInvoiceOptions(inv.id, stamp, sign, mode, notes)
                        editingOptionsInvoice = null
                    }
                ) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { editingOptionsInvoice = null }) { Text("Cancel") }
            }
        )
    }

    deletingInvoice?.let { inv ->
        AlertDialog(
            onDismissRequest = { deletingInvoice = null },
            title = { Text("Delete Bill ${inv.invoiceNumber}?") },
            text = { Text("This permanently removes this GST bill from your cloud history.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteInvoice(inv.id)
                        deletingInvoice = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingInvoice = null }) { Text("Cancel") }
            }
        )
    }
}

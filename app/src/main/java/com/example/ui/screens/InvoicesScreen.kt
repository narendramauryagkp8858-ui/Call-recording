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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.example.billing.EditedInvoiceLine
import com.example.billing.GstCalculator
import com.example.billing.StockManager
import com.example.data.Invoice
import com.example.data.PaymentMode
import com.example.data.ProductItem
import com.example.ui.BillingViewModel
import com.example.ui.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader
import com.example.ui.theme.WhatsAppGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun InvoicesScreen(
    viewModel: BillingViewModel,
    invoicesState: UiState<List<Invoice>>,
    productsState: UiState<List<ProductItem>>,
    onGoToNewBill: () -> Unit,
    onPreviewPdf: (Invoice) -> Unit,
    onShareWhatsApp: (Invoice) -> Unit,
    onPrintThermal: (Invoice) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var editingOptionsInvoice by remember { mutableStateOf<Invoice?>(null) }
    var editingReturnInvoice by remember { mutableStateOf<Invoice?>(null) }
    var deletingInvoice by remember { mutableStateOf<Invoice?>(null) }

    val products = (productsState as? UiState.Success)?.data ?: emptyList()
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "GST Bills History",
                subtitle = "Edit bills, process product returns with auto stock restore, or share PDF"
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

                                if (inv.items.isNotEmpty()) {
                                    val itemsPreview = inv.items.take(3).joinToString(", ") { line ->
                                        "${line.name} × ${GstCalculator.formatPlainNumber(line.quantity)}"
                                    } + if (inv.items.size > 3) " +${inv.items.size - 3} more" else ""
                                    Text(
                                        text = itemsPreview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                                    OutlinedButton(
                                        onClick = { editingReturnInvoice = inv },
                                        modifier = Modifier
                                            .heightIn(min = 40.dp)
                                            .testTag("invoice_edit_return_${inv.id}"),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.AssignmentReturn,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Edit / Return", style = MaterialTheme.typography.labelMedium)
                                    }
                                    IconButton(
                                        onClick = { editingOptionsInvoice = inv },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = "Edit stamp/sign options")
                                    }
                                    IconButton(
                                        onClick = { deletingInvoice = inv },
                                        modifier = Modifier.minimumInteractiveComponentSize()
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
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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

    editingReturnInvoice?.let { inv ->
        EditInvoiceReturnDialog(
            invoice = inv,
            products = products,
            onDismiss = { editingReturnInvoice = null },
            onSaveEdit = { updatedInvoice ->
                viewModel.updateInvoiceWithReturn(updatedInvoice)
                editingReturnInvoice = null
            },
            onFullReturnDelete = { target ->
                viewModel.deleteInvoice(target.id, restoreStock = true)
                editingReturnInvoice = null
            }
        )
    }

    deletingInvoice?.let { inv ->
        var restoreStock by remember(inv.id) { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { deletingInvoice = null },
            title = { Text("Delete / Return Bill ${inv.invoiceNumber}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("This removes this GST bill from your history.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = restoreStock,
                            onCheckedChange = { restoreStock = it },
                            modifier = Modifier.testTag("delete_restore_stock_checkbox")
                        )
                        Text(
                            "Restore all billed items back to store stock",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteInvoice(inv.id, restoreStock = restoreStock)
                        deletingInvoice = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(if (restoreStock) "Delete & Restore Stock" else "Delete Bill") }
            },
            dismissButton = {
                TextButton(onClick = { deletingInvoice = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EditInvoiceReturnDialog(
    invoice: Invoice,
    products: List<ProductItem>,
    onDismiss: () -> Unit,
    onSaveEdit: (Invoice) -> Unit,
    onFullReturnDelete: (Invoice) -> Unit
) {
    val productsById = remember(products) { products.associateBy { it.id } }

    var customerName by remember(invoice.id) { mutableStateOf(invoice.customerName) }
    var customerPhone by remember(invoice.id) { mutableStateOf(invoice.customerPhone) }
    var customerAddress by remember(invoice.id) { mutableStateOf(invoice.customerAddress) }
    var customerGstin by remember(invoice.id) { mutableStateOf(invoice.customerGstin) }
    var interState by remember(invoice.id) { mutableStateOf(invoice.interState) }
    var includeStamp by remember(invoice.id) { mutableStateOf(invoice.includeStamp) }
    var includeSignature by remember(invoice.id) { mutableStateOf(invoice.includeSignature) }
    var paymentMode by remember(invoice.id) { mutableStateOf(PaymentMode.fromRaw(invoice.paymentMode)) }
    var notes by remember(invoice.id) { mutableStateOf(invoice.notes) }

    var lines by remember(invoice.id) {
        mutableStateOf(
            invoice.items.mapIndexed { idx, item ->
                EditedInvoiceLine(
                    key = "${idx}_${item.productId}_${UUID.randomUUID()}",
                    productId = item.productId,
                    name = item.name,
                    hsnCode = item.hsnCode,
                    unit = item.unit,
                    originalQuantity = item.quantity,
                    quantity = item.quantity,
                    sellingRate = item.sellingRate,
                    gstRate = item.gstRate,
                    taxInclusive = item.taxInclusive
                )
            }
        )
    }
    var showAddProduct by remember { mutableStateOf(false) }

    val recalculated = remember(
        lines,
        customerName,
        customerPhone,
        customerAddress,
        customerGstin,
        interState,
        includeStamp,
        includeSignature,
        paymentMode,
        notes,
        productsById
    ) {
        StockManager.recalculateEditedInvoice(
            original = invoice,
            editedLines = lines,
            productsById = productsById,
            customerName = customerName,
            customerPhone = customerPhone,
            customerAddress = customerAddress,
            customerGstin = customerGstin,
            interState = interState,
            includeStamp = includeStamp,
            includeSignature = includeSignature,
            paymentMode = paymentMode,
            notes = notes
        )
    }

    val stockDeltas = remember(invoice.items, recalculated.items) {
        StockManager.computeStockDeltasForUpdate(invoice.items, recalculated.items)
    }
    val hasActiveItems = recalculated.items.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Edit / Return (${invoice.invoiceNumber})")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Reduce item quantity or tap Return to restore stock back to your store.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Items & Return Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Billed Items", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (products.isNotEmpty()) {
                        TextButton(onClick = { showAddProduct = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Item")
                        }
                    }
                }

                lines.forEach { line ->
                    val diff = GstCalculator.round2(line.originalQuantity - line.quantity)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(line.name, style = MaterialTheme.typography.titleSmall)
                                    MonoCodeText(
                                        "Bikri: ${GstCalculator.formatRupees(line.sellingRate)}/${line.unit}" +
                                            if (line.originalQuantity > 0.0) {
                                                " • Billed: ${GstCalculator.formatPlainNumber(line.originalQuantity)}"
                                            } else {
                                                " • New"
                                            }
                                    )
                                }
                                Text(
                                    text = GstCalculator.formatRupees(
                                        if (line.quantity > 0.0) {
                                            GstCalculator.computeLine(
                                                line.productId, line.name, line.hsnCode, line.unit,
                                                line.quantity, line.sellingRate, 0.0, line.gstRate, line.taxInclusive
                                            ).lineItem.lineTotal
                                        } else 0.0
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                lines = lines.map { item ->
                                                    if (item.key == line.key) {
                                                        val next = GstCalculator.round2((item.quantity - 1.0).coerceAtLeast(0.0))
                                                        item.copy(quantity = next)
                                                    } else item
                                                }
                                            },
                                            enabled = line.quantity > 0.0,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("return_minus_${line.key}")
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrease quantity / return 1", modifier = Modifier.size(18.dp))
                                        }
                                        Text(
                                            text = "${GstCalculator.formatPlainNumber(line.quantity)} ${line.unit}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        )
                                        IconButton(
                                            onClick = {
                                                lines = lines.map { item ->
                                                    if (item.key == line.key) {
                                                        val next = GstCalculator.round2((item.quantity + 1.0).coerceAtMost(99999.0))
                                                        item.copy(quantity = next)
                                                    } else item
                                                }
                                            },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("return_plus_${line.key}")
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Increase quantity", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                if (line.quantity > 0.0) {
                                    TextButton(
                                        onClick = {
                                            lines = lines.map { item ->
                                                if (item.key == line.key) item.copy(quantity = 0.0) else item
                                            }
                                        },
                                        modifier = Modifier.testTag("return_all_line_${line.key}")
                                    ) {
                                        Text("Return All", color = MaterialTheme.colorScheme.error)
                                    }
                                } else {
                                    TextButton(
                                        onClick = {
                                            lines = lines.map { item ->
                                                if (item.key == line.key) item.copy(quantity = line.originalQuantity.coerceAtLeast(1.0)) else item
                                            }
                                        }
                                    ) {
                                        Text("Undo Return")
                                    }
                                }
                            }

                            if (line.productId.isNotBlank() && kotlin.math.abs(diff) > 0.0001) {
                                val restoring = diff > 0.0
                                Text(
                                    text = if (restoring) {
                                        "+${GstCalculator.formatPlainNumber(diff)} ${line.unit} will restore to stock"
                                    } else {
                                        "${GstCalculator.formatPlainNumber(diff)} ${line.unit} will be deducted from stock"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (restoring) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                // Live Totals & Stock Preview
                Surface(
                    color = if (hasActiveItems) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!hasActiveItems) {
                            Text(
                                "Full Return Mode",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "All items are returned (0 remaining). Confirming will restore all billed stock and remove this cancelled bill.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Updated Taxable", style = MaterialTheme.typography.bodySmall)
                                Text(GstCalculator.formatRupees(recalculated.subtotalTaxable), style = MaterialTheme.typography.bodySmall)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Updated GST", style = MaterialTheme.typography.bodySmall)
                                Text(GstCalculator.formatRupees(recalculated.totalGstAmount), style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("New Bill Total", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    GstCalculator.formatRupees(recalculated.grandTotal),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (stockDeltas.isNotEmpty()) {
                                val summary = stockDeltas.entries.joinToString(", ") { (pid, d) ->
                                    val pname = productsById[pid]?.name ?: "Item"
                                    val sign = if (d > 0) "+" else ""
                                    "$pname $sign${GstCalculator.formatPlainNumber(d)}"
                                }
                                Text(
                                    text = "Stock Sync: $summary",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }

                // Customer & Tax Options
                HorizontalDivider()
                Text("Customer & Bill Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("Customer Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("Phone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customerGstin,
                        onValueChange = { customerGstin = it.uppercase() },
                        label = { Text("GSTIN") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = customerAddress,
                    onValueChange = { customerAddress = it },
                    label = { Text("Address") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !interState,
                        onClick = { interState = false },
                        label = { Text("CGST + SGST") }
                    )
                    FilterChip(
                        selected = interState,
                        onClick = { interState = true },
                        label = { Text("IGST") }
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PaymentMode.entries.forEach { pm ->
                        FilterChip(
                            selected = paymentMode == pm,
                            onClick = { paymentMode = pm },
                            label = { Text(pm.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Checkbox(checked = includeStamp, onCheckedChange = { includeStamp = it })
                        Text("Mohar", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Checkbox(checked = includeSignature, onCheckedChange = { includeSignature = it })
                        Text("Signature", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Return / Edit Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (!hasActiveItems) {
                Button(
                    onClick = { onFullReturnDelete(invoice) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_full_return_button")
                ) {
                    Text("Full Return & Restore Stock")
                }
            } else {
                Button(
                    onClick = { onSaveEdit(recalculated) },
                    enabled = customerName.isNotBlank(),
                    modifier = Modifier.testTag("save_bill_return_button")
                ) {
                    Text("Save Bill & Sync Stock")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showAddProduct) {
        AlertDialog(
            onDismissRequest = { showAddProduct = false },
            title = { Text("Add Item to Bill") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(products, key = { it.id }) { prod ->
                        Surface(
                            onClick = {
                                val existingIdx = lines.indexOfFirst { it.productId == prod.id && prod.id.isNotBlank() }
                                lines = if (existingIdx >= 0) {
                                    lines.toMutableList().apply {
                                        val cur = this[existingIdx]
                                        this[existingIdx] = cur.copy(quantity = GstCalculator.round2(cur.quantity + 1.0))
                                    }
                                } else {
                                    lines + EditedInvoiceLine(
                                        key = "${prod.id}_${UUID.randomUUID()}",
                                        productId = prod.id,
                                        name = prod.name,
                                        hsnCode = prod.hsnCode,
                                        unit = prod.unit,
                                        originalQuantity = 0.0,
                                        quantity = 1.0,
                                        sellingRate = prod.sellingRate,
                                        gstRate = prod.gstRate,
                                        taxInclusive = prod.taxInclusive
                                    )
                                }
                                showAddProduct = false
                            },
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prod.name, style = MaterialTheme.typography.titleSmall)
                                    MonoCodeText(
                                        "Bikri: ${GstCalculator.formatRupees(prod.sellingRate)}/${prod.unit} • Stock: ${
                                            GstCalculator.formatPlainNumber(prod.stockQty)
                                        }"
                                    )
                                }
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddProduct = false }) { Text("Close") }
            }
        )
    }
}

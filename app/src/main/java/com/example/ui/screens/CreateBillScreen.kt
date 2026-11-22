package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.material3.Switch
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
import com.example.billing.GstCalculator
import com.example.billing.StockManager
import com.example.billing.StockStatus
import com.example.billing.WarrantyEvaluation
import com.example.billing.WarrantyManager
import com.example.data.Customer
import com.example.data.Invoice
import com.example.data.PaymentMode
import com.example.data.ProductItem
import com.example.data.ShopProfile
import com.example.ui.BillingViewModel
import com.example.ui.DraftBillLine
import com.example.ui.DraftBillState
import com.example.ui.UiState
import com.example.ui.components.CustomerEditorDialog
import com.example.ui.components.DeleteCustomerConfirmDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader
import com.example.ui.components.WarrantyExpiryAlertBanner
import com.example.ui.components.WarrantyOptionSection
import com.example.ui.theme.WhatsAppGreen

@Composable
fun CreateBillScreen(
    viewModel: BillingViewModel,
    shopsState: UiState<List<ShopProfile>>,
    productsState: UiState<List<ProductItem>>,
    customersState: UiState<List<Customer>>,
    draft: DraftBillState,
    lastCreatedInvoice: Invoice?,
    expiringWarranties: List<Pair<Invoice, WarrantyEvaluation>> = emptyList(),
    onOpenWarrantyNotifications: () -> Unit = {},
    onRequestWarrantyNotificationPermission: () -> Unit = {},
    onNavigateToShops: () -> Unit,
    onPreviewPdf: (Invoice) -> Unit,
    onShareWhatsApp: (Invoice) -> Unit,
    onPrintThermal: (Invoice) -> Unit
) {
    val shops = (shopsState as? UiState.Success)?.data ?: emptyList()
    val products = (productsState as? UiState.Success)?.data ?: emptyList()
    val customers = (customersState as? UiState.Success)?.data ?: emptyList()
    val activeShop = viewModel.resolveActiveShop(shops)

    val productsById = remember(products) { products.associateBy { it.id } }
    val cartQtyByProductId = remember(draft.lines) {
        draft.lines.filter { it.productId.isNotBlank() }
            .groupBy { it.productId }
            .mapValues { (_, list) -> list.sumOf { it.quantity } }
    }

    var showCustomerPicker by remember { mutableStateOf(false) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<Customer?>(null) }
    var deletingCustomer by remember { mutableStateOf<Customer?>(null) }
    var showProductPicker by remember { mutableStateOf(false) }
    var showCustomItemDialog by remember { mutableStateOf(false) }
    var editingLine by remember { mutableStateOf<DraftBillLine?>(null) }

    val selectedSavedCustomer = remember(customers, draft.selectedCustomerId) {
        if (draft.selectedCustomerId.isBlank()) null
        else customers.firstOrNull { it.id == draft.selectedCustomerId }
    }

    val totals = remember(draft.lines, draft.interState) { draft.totals() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "New GST Invoice",
                subtitle = "Create bill with Shop Mohar, Sign & Bikri Rate only"
            )
        }

        if (expiringWarranties.isNotEmpty()) {
            item {
                WarrantyExpiryAlertBanner(
                    expiring = expiringWarranties,
                    onClick = onOpenWarrantyNotifications
                )
            }
        }

        // 1. Active Shop Selector
        item {
            if (shopsState is UiState.Loading) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Loading shop profiles...")
                    }
                }
            } else if (shops.isEmpty()) {
                EmptyStateCard(
                    title = "Add Your Shop / Dukaan First",
                    description = "Set up your Shop Name, GSTIN, Mohar (Stamp), and Signature to generate GST bills.",
                    actionLabel = "Add Shop Profile",
                    testTag = "empty_add_shop_button",
                    onAction = onNavigateToShops
                )
            } else {
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Billing From Shop", style = MaterialTheme.typography.titleSmall)
                            }
                            TextButton(
                                onClick = onNavigateToShops,
                                modifier = Modifier.testTag("manage_shops_shortcut")
                            ) {
                                Text("Manage Shops")
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            shops.forEach { shop ->
                                val selected = activeShop?.id == shop.id
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.selectShop(shop.id) },
                                    label = { Text(shop.shopName) },
                                    leadingIcon = if (selected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.testTag("select_shop_${shop.id}")
                                )
                            }
                        }

                        if (activeShop != null) {
                            val nextNo = "${activeShop.invoicePrefix}${activeShop.nextInvoiceNumber.toString().padStart(4, '0')}"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MonoCodeText(
                                    text = if (activeShop.gstin.isNotBlank()) "GSTIN: ${activeShop.gstin}" else "Unregistered / Composition"
                                )
                                MonoCodeText(
                                    text = "Next: $nextNo",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Customer Details Card (Name, Mobile, Full Address, optional GSTIN)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Customer Details", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (draft.selectedCustomerId.isNotBlank() || draft.customerName.isNotBlank()) {
                                TextButton(onClick = { viewModel.clearSelectedCustomer() }) {
                                    Text("Clear")
                                }
                            }
                            OutlinedButton(
                                onClick = { showCustomerPicker = true },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("pick_customer_button"),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PersonSearch, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Saved (${customers.size})")
                            }
                        }
                    }

                    if (selectedSavedCustomer != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("selected_saved_customer_bar")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Saved: ${selectedSavedCustomer.name}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    TextButton(
                                        onClick = { editingCustomer = selectedSavedCustomer },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("edit_selected_customer_button"),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Edit")
                                    }
                                    TextButton(
                                        onClick = { deletingCustomer = selectedSavedCustomer },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("delete_selected_customer_button"),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Delete", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = draft.customerName,
                        onValueChange = { viewModel.updateCustomerFields(name = it) },
                        label = { Text("Customer Name *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bill_customer_name_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = draft.customerPhone,
                            onValueChange = { viewModel.updateCustomerFields(phone = it) },
                            label = { Text("Mobile No.") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("bill_customer_phone_input")
                        )
                        OutlinedTextField(
                            value = draft.customerGstin,
                            onValueChange = { viewModel.updateCustomerFields(gstin = it.uppercase()) },
                            label = { Text("Customer GSTIN") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("bill_customer_gstin_input")
                        )
                    }

                    OutlinedTextField(
                        value = draft.customerAddress,
                        onValueChange = { viewModel.updateCustomerFields(address = it) },
                        label = { Text("Customer Full Address") },
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bill_customer_address_input")
                    )

                    if (draft.selectedCustomerId.isBlank() && draft.customerName.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Save customer for future bills", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = draft.saveNewCustomer,
                                onCheckedChange = { viewModel.updateCustomerFields(saveToDirectory = it) },
                                modifier = Modifier.testTag("save_new_customer_switch")
                            )
                        }
                    }
                }
            }
        }

        // 3. Bill Items Header & Quick Add Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Bill Items (Bikri Rate Only)",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showProductPicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("add_catalog_item_button")
                    ) {
                        Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("From Catalog (${products.size})")
                    }
                    OutlinedButton(
                        onClick = { showCustomItemDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("add_custom_item_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Custom Item")
                    }
                }
            }
        }

        if (draft.lines.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No items added yet. Pick saved items from Catalog or tap Custom Item.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(draft.lines, key = { it.localId }) { line ->
                val computed = remember(line) { line.computed().lineItem }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = line.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                MonoCodeText(
                                    text = buildString {
                                        append("Bikri: ${GstCalculator.formatRupees(line.sellingRate)}/${line.unit}")
                                        append(" • GST ${GstCalculator.formatPlainNumber(line.gstRate)}%")
                                        if (line.taxInclusive) append(" (Incl)")
                                    }
                                )
                                val stockProd = productsById[line.productId]
                                if (stockProd != null) {
                                    val afterBill = GstCalculator.round2((stockProd.stockQty - (cartQtyByProductId[line.productId] ?: line.quantity)).coerceAtLeast(0.0))
                                    val isShort = (cartQtyByProductId[line.productId] ?: line.quantity) > stockProd.stockQty
                                    Text(
                                        text = if (isShort) {
                                            "Stock in store: ${GstCalculator.formatPlainNumber(stockProd.stockQty)} ${stockProd.unit} (exceeds stock)"
                                        } else {
                                            "Store Stock: ${GstCalculator.formatPlainNumber(stockProd.stockQty)} ${stockProd.unit} → After bill: ${GstCalculator.formatPlainNumber(afterBill)}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isShort) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Row {
                                IconButton(
                                    onClick = { editingLine = line },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit line item")
                                }
                                IconButton(
                                    onClick = { viewModel.removeLine(line.localId) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Remove item",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { viewModel.updateLineQuantity(line.localId, -1.0) },
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrease quantity", modifier = Modifier.size(18.dp))
                                        }
                                        Text(
                                            text = "${GstCalculator.formatPlainNumber(line.quantity)} ${line.unit}",
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        )
                                        IconButton(
                                            onClick = { viewModel.updateLineQuantity(line.localId, 1.0) },
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Increase quantity", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = GstCalculator.formatRupees(computed.lineTotal),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Tax ${GstCalculator.formatRupees(computed.gstAmount)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. GST Type, Mohar (Stamp) & Signature Options, Payment Mode
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Tax, Mohar & Sign Options", style = MaterialTheme.typography.titleMedium)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !draft.interState,
                            onClick = { viewModel.updateBillOptions(interState = false) },
                            label = { Text("CGST + SGST") },
                            modifier = Modifier.testTag("chip_cgst_sgst")
                        )
                        FilterChip(
                            selected = draft.interState,
                            onClick = { viewModel.updateBillOptions(interState = true) },
                            label = { Text("IGST (Out of State)") },
                            modifier = Modifier.testTag("chip_igst")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(
                                checked = draft.includeStamp,
                                onCheckedChange = { viewModel.updateBillOptions(includeStamp = it) },
                                modifier = Modifier.testTag("include_stamp_checkbox")
                            )
                            Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("Mohar / Stamp", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(
                                checked = draft.includeSignature,
                                onCheckedChange = { viewModel.updateBillOptions(includeSignature = it) },
                                modifier = Modifier.testTag("include_signature_checkbox")
                            )
                            Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("Signature", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PaymentMode.entries.forEach { mode ->
                            FilterChip(
                                selected = draft.paymentMode == mode,
                                onClick = { viewModel.updateBillOptions(paymentMode = mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    HorizontalDivider()

                    WarrantyOptionSection(
                        enabled = draft.hasWarranty,
                        months = draft.warrantyMonths,
                        days = draft.warrantyDays,
                        note = draft.warrantyNote,
                        onToggle = { enabled ->
                            viewModel.updateWarrantyOption(enabled = enabled)
                            if (enabled) {
                                onRequestWarrantyNotificationPermission()
                            }
                        },
                        onPresetSelected = { m, d ->
                            viewModel.updateWarrantyOption(
                                enabled = true,
                                months = m,
                                days = d,
                                customEndMillis = 0L
                            )
                        },
                        onCustomDurationChanged = { m, d ->
                            viewModel.updateWarrantyOption(
                                enabled = true,
                                months = m,
                                days = d,
                                customEndMillis = 0L
                            )
                        },
                        onNoteChanged = { note ->
                            viewModel.updateWarrantyOption(note = note)
                        }
                    )

                    OutlinedTextField(
                        value = draft.notes,
                        onValueChange = { viewModel.updateBillOptions(notes = it) },
                        label = { Text("Optional Note (e.g. Vehicle No. / Delivery)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 5. Grand Total Summary & Generate Action
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryRow("Taxable Value", GstCalculator.formatRupees(totals.subtotalTaxable))
                    if (draft.interState) {
                        SummaryRow("IGST", GstCalculator.formatRupees(totals.igstAmount))
                    } else {
                        SummaryRow("CGST", GstCalculator.formatRupees(totals.cgstAmount))
                        SummaryRow("SGST", GstCalculator.formatRupees(totals.sgstAmount))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "GRAND TOTAL",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            GstCalculator.formatRupees(totals.grandTotal),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = { viewModel.generateAndSaveInvoice(activeShop) },
                        enabled = !draft.isSaving && activeShop != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("generate_bill_button")
                    ) {
                        if (draft.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Saving Bill...")
                        } else {
                            Icon(Icons.Default.Receipt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save & Generate GST Bill", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }

    if (showCustomerPicker) {
        CustomerPickerDialog(
            customers = customers,
            onDismiss = { showCustomerPicker = false },
            onSelect = {
                viewModel.selectCustomer(it)
                showCustomerPicker = false
            },
            onAddNew = { showAddCustomerDialog = true },
            onEdit = { editingCustomer = it },
            onDelete = { deletingCustomer = it }
        )
    }

    if (showAddCustomerDialog || editingCustomer != null) {
        CustomerEditorDialog(
            initial = editingCustomer,
            onDismiss = {
                showAddCustomerDialog = false
                editingCustomer = null
            },
            onSave = { cust ->
                viewModel.saveCustomer(cust)
                showAddCustomerDialog = false
                editingCustomer = null
            }
        )
    }

    deletingCustomer?.let { target ->
        DeleteCustomerConfirmDialog(
            customer = target,
            onDismiss = { deletingCustomer = null },
            onConfirmDelete = { cust ->
                viewModel.deleteCustomer(cust.id)
                deletingCustomer = null
            }
        )
    }

    if (showProductPicker) {
        ProductPickerDialog(
            products = products,
            inCartQtyByProductId = cartQtyByProductId,
            onDismiss = { showProductPicker = false },
            onAddProduct = {
                viewModel.addProductToBill(it)
            }
        )
    }

    if (showCustomItemDialog) {
        BillLineEditDialog(
            initial = null,
            onDismiss = { showCustomItemDialog = false },
            onSave = { name, qty, sellRate, gstRate, taxInclusive, unit, hsn ->
                viewModel.addCustomLineToBill(
                    DraftBillLine(
                        name = name,
                        hsnCode = hsn,
                        unit = unit,
                        quantity = qty,
                        sellingRate = sellRate,
                        purchaseRate = 0.0,
                        gstRate = gstRate,
                        taxInclusive = taxInclusive
                    )
                )
                showCustomItemDialog = false
            }
        )
    }

    editingLine?.let { target ->
        BillLineEditDialog(
            initial = target,
            onDismiss = { editingLine = null },
            onSave = { name, qty, sellRate, gstRate, taxInclusive, unit, hsn ->
                viewModel.updateLineDetails(
                    localId = target.localId,
                    name = name,
                    quantity = qty,
                    sellingRate = sellRate,
                    gstRate = gstRate,
                    taxInclusive = taxInclusive,
                    unit = unit,
                    hsnCode = hsn
                )
                editingLine = null
            }
        )
    }

    if (lastCreatedInvoice != null) {
        AlertDialog(
            onDismissRequest = { viewModel.consumeLastCreatedInvoice() },
            title = { Text("Bill ${lastCreatedInvoice.invoiceNumber} Ready!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Customer: ${lastCreatedInvoice.customerName}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Amount: ${GstCalculator.formatRupees(lastCreatedInvoice.grandTotal)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (WarrantyManager.isWarrantyActiveOnBill(lastCreatedInvoice)) {
                        val wEval = WarrantyManager.evaluate(lastCreatedInvoice)
                        Text(
                            text = "Warranty: ${wEval.displayLabel} (Till ${WarrantyManager.formatExpiryDate(wEval.expiryDateMillis)})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        "You can preview the GST Tax Invoice PDF, share directly on WhatsApp, or print on your Bluetooth mini thermal printer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val inv = lastCreatedInvoice
                            viewModel.consumeLastCreatedInvoice()
                            onShareWhatsApp(inv)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("dialog_whatsapp_share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share PDF on WhatsApp")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val inv = lastCreatedInvoice
                                viewModel.consumeLastCreatedInvoice()
                                onPreviewPdf(inv)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("dialog_preview_pdf_button")
                        ) {
                            Text("Preview PDF")
                        }
                        OutlinedButton(
                            onClick = {
                                val inv = lastCreatedInvoice
                                viewModel.consumeLastCreatedInvoice()
                                onPrintThermal(inv)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("dialog_thermal_print_button")
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Print")
                        }
                    }
                    TextButton(
                        onClick = { viewModel.consumeLastCreatedInvoice() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Done")
                    }
                }
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun CustomerPickerDialog(
    customers: List<Customer>,
    onDismiss: () -> Unit,
    onSelect: (Customer) -> Unit,
    onAddNew: () -> Unit,
    onEdit: (Customer) -> Unit,
    onDelete: (Customer) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(customers, query) {
        if (query.isBlank()) customers
        else customers.filter {
            it.name.contains(query, true) || it.phone.contains(query, true) || it.address.contains(query, true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Saved Customers (${customers.size})")
                TextButton(
                    onClick = onAddNew,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("picker_add_customer_button")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add New")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search name, mobile or address") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("picker_customer_search_input")
                )
                if (filtered.isEmpty()) {
                    Text(
                        if (customers.isEmpty()) "No saved customers yet. Tap Add New to save one."
                        else "No matching saved customers.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filtered, key = { it.id }) { cust ->
                            Surface(
                                onClick = { onSelect(cust) },
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("picker_select_customer_${cust.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cust.name, style = MaterialTheme.typography.titleSmall)
                                        val details = listOf(cust.phone, cust.address)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" • ")
                                        if (details.isNotBlank()) {
                                            Text(
                                                details,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2
                                            )
                                        }
                                        if (cust.gstin.isNotBlank()) {
                                            MonoCodeText("GSTIN: ${cust.gstin}")
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onEdit(cust) },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("picker_edit_customer_${cust.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit saved customer ${cust.name}",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDelete(cust) },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("picker_delete_customer_${cust.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Delete saved customer ${cust.name}",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
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
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ProductPickerDialog(
    products: List<ProductItem>,
    inCartQtyByProductId: Map<String, Double>,
    onDismiss: () -> Unit,
    onAddProduct: (ProductItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(products, query) {
        if (query.isBlank()) products
        else products.filter {
            it.name.contains(query, true) || it.hsnCode.contains(query, true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Items to Bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search item or HSN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Text(
                        "No items found. Add items in the Items tab or use Custom Item.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered, key = { it.id }) { prod ->
                            val inCart = inCartQtyByProductId[prod.id] ?: 0.0
                            val status = StockManager.getStockStatus(prod.stockQty, prod.lowStockThreshold)
                            val stockColor = when (status) {
                                StockStatus.OUT_OF_STOCK -> MaterialTheme.colorScheme.error
                                StockStatus.LOW_STOCK -> MaterialTheme.colorScheme.secondary
                                StockStatus.IN_STOCK -> MaterialTheme.colorScheme.tertiary
                            }
                            Surface(
                                onClick = { onAddProduct(prod) },
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prod.name, style = MaterialTheme.typography.titleSmall)
                                        MonoCodeText(
                                            "Bikri: ${GstCalculator.formatRupees(prod.sellingRate)}/${prod.unit} • GST ${
                                                GstCalculator.formatPlainNumber(prod.gstRate)
                                            }%"
                                        )
                                        Text(
                                            text = buildString {
                                                append(
                                                    when (status) {
                                                        StockStatus.OUT_OF_STOCK -> "Out of Stock (0 ${prod.unit})"
                                                        StockStatus.LOW_STOCK -> "Low Stock: ${GstCalculator.formatPlainNumber(prod.stockQty)} ${prod.unit}"
                                                        StockStatus.IN_STOCK -> "Stock: ${GstCalculator.formatPlainNumber(prod.stockQty)} ${prod.unit}"
                                                    }
                                                )
                                                if (inCart > 0.0) {
                                                    append(" • In Bill: ${GstCalculator.formatPlainNumber(inCart)}")
                                                }
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = stockColor
                                        )
                                    }
                                    Icon(Icons.Default.Add, contentDescription = "Add item", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun BillLineEditDialog(
    initial: DraftBillLine?,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Double, Boolean, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var qty by remember { mutableStateOf(initial?.let { GstCalculator.formatPlainNumber(it.quantity) } ?: "1") }
    var rate by remember { mutableStateOf(initial?.let { GstCalculator.formatPlainNumber(it.sellingRate) } ?: "") }
    var gst by remember { mutableStateOf(initial?.let { GstCalculator.formatPlainNumber(it.gstRate) } ?: "18") }
    var unit by remember { mutableStateOf(initial?.unit ?: "PCS") }
    var hsn by remember { mutableStateOf(initial?.hsnCode ?: "") }
    var inclusive by remember { mutableStateOf(initial?.taxInclusive ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Custom Bill Item" else "Edit Bill Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("Qty *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rate,
                        onValueChange = { rate = it },
                        label = { Text("Bikri Rate (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1.3f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = gst,
                        onValueChange = { gst = it },
                        label = { Text("GST %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hsn,
                        onValueChange = { hsn = it },
                        label = { Text("HSN") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Rate includes GST", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = inclusive, onCheckedChange = { inclusive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = qty.toDoubleOrNull() ?: 1.0
                    val r = rate.toDoubleOrNull() ?: 0.0
                    val g = gst.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && q > 0.0 && r >= 0.0) {
                        onSave(name, q, r, g, inclusive, unit, hsn)
                    }
                },
                enabled = name.isNotBlank() && (rate.toDoubleOrNull() ?: -1.0) >= 0.0
            ) {
                Text("Save Item")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

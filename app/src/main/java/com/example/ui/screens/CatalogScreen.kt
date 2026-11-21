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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.billing.StockManager
import com.example.billing.StockStatus
import com.example.data.Customer
import com.example.data.ProductItem
import com.example.ui.BillingViewModel
import com.example.ui.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader

@Composable
fun CatalogScreen(
    viewModel: BillingViewModel,
    productsState: UiState<List<ProductItem>>,
    customersState: UiState<List<Customer>>,
    onBillCustomer: (Customer) -> Unit
) {
    var selectedSegment by remember { mutableIntStateOf(0) } // 0 = Products, 1 = Customers
    var searchQuery by remember { mutableStateOf("") }
    var showLowStockOnly by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductItem?>(null) }
    var showAddProduct by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<Customer?>(null) }
    var showAddCustomer by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "Items, Stock & Customers",
                subtitle = "Live store stock sync + private Khareed / public Bikri rates",
                action = {
                    Button(
                        onClick = {
                            if (selectedSegment == 0) showAddProduct = true else showAddCustomer = true
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("catalog_add_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (selectedSegment == 0) "Add Item" else "Add Customer")
                    }
                }
            )
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedSegment == 0,
                    onClick = { selectedSegment = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("segment_products")
                ) {
                    Text("Stock & Items")
                }
                SegmentedButton(
                    selected = selectedSegment == 1,
                    onClick = { selectedSegment = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("segment_customers")
                ) {
                    Text("Customers")
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(if (selectedSegment == 0) "Search item or HSN..." else "Search customer name, mobile or address...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("catalog_search_input")
            )
        }

        if (selectedSegment == 0) {
            when (productsState) {
                is UiState.Loading -> item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                is UiState.Error -> item { Text(productsState.message, color = MaterialTheme.colorScheme.error) }
                is UiState.Success -> {
                    val allProducts = productsState.data
                    val lowOrOutProducts = allProducts.filter { StockManager.isLowOrOutOfStock(it) }
                    val outOfStockCount = allProducts.count {
                        StockManager.getStockStatus(it.stockQty, it.lowStockThreshold) == StockStatus.OUT_OF_STOCK
                    }

                    if (allProducts.isNotEmpty()) {
                        if (lowOrOutProducts.isNotEmpty()) {
                            item {
                                Surface(
                                    color = if (outOfStockCount > 0) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("low_stock_alert_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.WarningAmber,
                                            contentDescription = "Low stock alert",
                                            tint = if (outOfStockCount > 0) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Stock Alert (${lowOrOutProducts.size} ${if (lowOrOutProducts.size == 1) "item" else "items"})",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (outOfStockCount > 0) {
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                }
                                            )
                                            val previewNames = lowOrOutProducts.take(3).joinToString(", ") { prod ->
                                                val qtyStr = GstCalculator.formatPlainNumber(prod.stockQty)
                                                "${prod.name}: $qtyStr ${prod.unit}"
                                            }
                                            val extra = if (lowOrOutProducts.size > 3) " +${lowOrOutProducts.size - 3} more" else ""
                                            Text(
                                                text = previewNames + extra,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (outOfStockCount > 0) {
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = !showLowStockOnly,
                                    onClick = { showLowStockOnly = false },
                                    label = { Text("All Items (${allProducts.size})") },
                                    modifier = Modifier.testTag("filter_all_stock")
                                )
                                FilterChip(
                                    selected = showLowStockOnly,
                                    onClick = { showLowStockOnly = true },
                                    label = { Text("Low / Out (${lowOrOutProducts.size})") },
                                    modifier = Modifier.testTag("filter_low_stock")
                                )
                            }
                        }
                    }

                    val filtered = allProducts.filter { prod ->
                        val matchesQuery = searchQuery.isBlank() ||
                            prod.name.contains(searchQuery, true) ||
                            prod.hsnCode.contains(searchQuery, true)
                        val matchesStock = !showLowStockOnly || StockManager.isLowOrOutOfStock(prod)
                        matchesQuery && matchesStock
                    }

                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = if (showLowStockOnly && allProducts.isNotEmpty()) "All Stock Healthy" else "No Items Yet",
                                description = if (showLowStockOnly && allProducts.isNotEmpty()) {
                                    "Every item in your store is currently above its low-stock alert level."
                                } else {
                                    "Add items with opening Stock Qty, Khareed (Purchase) & Bikri (Selling) rates. Stock automatically updates when bills are created or returned."
                                },
                                actionLabel = if (showLowStockOnly && allProducts.isNotEmpty()) "Show All Items" else "Add First Item",
                                onAction = {
                                    if (showLowStockOnly && allProducts.isNotEmpty()) showLowStockOnly = false
                                    else showAddProduct = true
                                }
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { prod ->
                            val margin = GstCalculator.round2(prod.sellingRate - prod.purchaseRate)
                            val stockStatus = StockManager.getStockStatus(prod.stockQty, prod.lowStockThreshold)
                            val stockBg = when (stockStatus) {
                                StockStatus.OUT_OF_STOCK -> MaterialTheme.colorScheme.errorContainer
                                StockStatus.LOW_STOCK -> MaterialTheme.colorScheme.secondaryContainer
                                StockStatus.IN_STOCK -> MaterialTheme.colorScheme.tertiaryContainer
                            }
                            val stockFg = when (stockStatus) {
                                StockStatus.OUT_OF_STOCK -> MaterialTheme.colorScheme.onErrorContainer
                                StockStatus.LOW_STOCK -> MaterialTheme.colorScheme.onSecondaryContainer
                                StockStatus.IN_STOCK -> MaterialTheme.colorScheme.onTertiaryContainer
                            }
                            val stockLabel = when (stockStatus) {
                                StockStatus.OUT_OF_STOCK -> "Out of Stock: 0 ${prod.unit}"
                                StockStatus.LOW_STOCK -> "Low Stock: ${GstCalculator.formatPlainNumber(prod.stockQty)} ${prod.unit}"
                                StockStatus.IN_STOCK -> "In Stock: ${GstCalculator.formatPlainNumber(prod.stockQty)} ${prod.unit}"
                            }

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
                                            Text(prod.name, style = MaterialTheme.typography.titleMedium)
                                            MonoCodeText(
                                                "Unit: ${prod.unit} • GST ${GstCalculator.formatPlainNumber(prod.gstRate)}%${
                                                    if (prod.hsnCode.isNotBlank()) " • HSN ${prod.hsnCode}" else ""
                                                }"
                                            )
                                        }
                                        Row {
                                            IconButton(onClick = { editingProduct = prod }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit product")
                                            }
                                            IconButton(onClick = { viewModel.deleteProduct(prod.id) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete product", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }

                                    // Live Stock Status & Quick Adjust Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = stockBg,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.testTag("stock_badge_${prod.id}")
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                                Text(
                                                    text = stockLabel,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = stockFg
                                                )
                                                Text(
                                                    text = "Alert at ≤ ${GstCalculator.formatPlainNumber(prod.lowStockThreshold)} ${prod.unit}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = stockFg.copy(alpha = 0.85f)
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = { viewModel.adjustProductStock(prod, -1.0) },
                                                    enabled = prod.stockQty > 0.0,
                                                    modifier = Modifier
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("stock_minus_${prod.id}")
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = "Decrease stock by 1", modifier = Modifier.size(18.dp))
                                                }
                                                Text(
                                                    text = GstCalculator.formatPlainNumber(prod.stockQty),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                )
                                                IconButton(
                                                    onClick = { viewModel.adjustProductStock(prod, 1.0) },
                                                    modifier = Modifier
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("stock_plus_${prod.id}")
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Increase stock by 1", modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(Modifier.padding(8.dp)) {
                                                Text("Khareed (Private)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(GstCalculator.formatRupees(prod.purchaseRate), style = MaterialTheme.typography.titleSmall)
                                            }
                                        }
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(Modifier.padding(8.dp)) {
                                                Text("Bikri (On Bill)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                                Text(
                                                    GstCalculator.formatRupees(prod.sellingRate),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier.weight(0.9f)
                                        ) {
                                            Column(Modifier.padding(8.dp)) {
                                                Text("Margin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                                Text(
                                                    GstCalculator.formatRupees(margin),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            when (customersState) {
                is UiState.Loading -> item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                is UiState.Error -> item { Text(customersState.message, color = MaterialTheme.colorScheme.error) }
                is UiState.Success -> {
                    val filtered = customersState.data.filter {
                        searchQuery.isBlank() ||
                            it.name.contains(searchQuery, true) ||
                            it.phone.contains(searchQuery, true) ||
                            it.address.contains(searchQuery, true)
                    }
                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No Saved Customers Yet",
                                description = "Save customers with their name, mobile number, and full address for 1-tap billing.",
                                actionLabel = "Add First Customer",
                                onAction = { showAddCustomer = true }
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { cust ->
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
                                            Text(cust.name, style = MaterialTheme.typography.titleMedium)
                                            if (cust.phone.isNotBlank()) {
                                                Text("Mob: ${cust.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                            if (cust.address.isNotBlank()) {
                                                Text(cust.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (cust.gstin.isNotBlank()) {
                                                MonoCodeText("GSTIN: ${cust.gstin}")
                                            }
                                        }
                                        Row {
                                            IconButton(onClick = { editingCustomer = cust }, modifier = Modifier.size(44.dp)) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit customer")
                                            }
                                            IconButton(onClick = { viewModel.deleteCustomer(cust.id) }, modifier = Modifier.size(44.dp)) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete customer", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { onBillCustomer(cust) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 44.dp)
                                            .testTag("bill_customer_${cust.id}")
                                    ) {
                                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Make New Bill for ${cust.name}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddProduct || editingProduct != null) {
        ProductEditorDialog(
            initial = editingProduct,
            onDismiss = {
                showAddProduct = false
                editingProduct = null
            },
            onSave = { item ->
                viewModel.saveProduct(item)
                showAddProduct = false
                editingProduct = null
            }
        )
    }

    if (showAddCustomer || editingCustomer != null) {
        CustomerEditorDialog(
            initial = editingCustomer,
            onDismiss = {
                showAddCustomer = false
                editingCustomer = null
            },
            onSave = { cust ->
                viewModel.saveCustomer(cust)
                showAddCustomer = false
                editingCustomer = null
            }
        )
    }
}

@Composable
private fun ProductEditorDialog(
    initial: ProductItem?,
    onDismiss: () -> Unit,
    onSave: (ProductItem) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var purchaseRate by remember { mutableStateOf(initial?.let { GstCalculator.formatPlainNumber(it.purchaseRate) } ?: "") }
    var sellingRate by remember { mutableStateOf(initial?.let { GstCalculator.formatPlainNumber(it.sellingRate) } ?: "") }
    var gstRate by remember { mutableStateOf(initial?.let { GstCalculator.formatPlainNumber(it.gstRate) } ?: "18") }
    var hsn by remember { mutableStateOf(initial?.hsnCode ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "PCS") }
    var stockQty by remember { mutableStateOf(initial?.let { GstCalculator.formatPlainNumber(it.stockQty) } ?: "0") }
    var lowStockThreshold by remember {
        mutableStateOf(initial?.let { GstCalculator.formatPlainNumber(it.lowStockThreshold) } ?: "5")
    }
    var taxInclusive by remember { mutableStateOf(initial?.taxInclusive ?: false) }

    val slabs = listOf("0", "5", "12", "18", "28")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Item (Stock, Khareed & Bikri)" else "Edit Item & Stock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item / Product Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = purchaseRate,
                        onValueChange = { purchaseRate = it },
                        label = { Text("Khareed Rate (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_khareed_input")
                    )
                    OutlinedTextField(
                        value = sellingRate,
                        onValueChange = { sellingRate = it },
                        label = { Text("Bikri Rate (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_bikri_input")
                    )
                }
                Text(
                    "Note: Khareed rate is private for your margin calculation. Only Bikri rate shows on customer bills.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    slabs.forEach { s ->
                        FilterChip(
                            selected = gstRate == s,
                            onClick = { gstRate = s },
                            label = { Text("$s%") }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = gstRate,
                        onValueChange = { gstRate = it },
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stockQty,
                        onValueChange = { stockQty = it },
                        label = { Text("Stock Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_stock_input")
                    )
                    OutlinedTextField(
                        value = lowStockThreshold,
                        onValueChange = { lowStockThreshold = it },
                        label = { Text("Low Alert At") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_threshold_input")
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Selling Rate includes GST", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = taxInclusive, onCheckedChange = { taxInclusive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val buy = purchaseRate.toDoubleOrNull() ?: 0.0
                    val sell = sellingRate.toDoubleOrNull() ?: 0.0
                    val gst = gstRate.toDoubleOrNull() ?: 0.0
                    val stock = stockQty.toDoubleOrNull() ?: 0.0
                    val threshold = lowStockThreshold.toDoubleOrNull() ?: 5.0
                    onSave(
                        (initial ?: ProductItem()).copy(
                            name = name.trim(),
                            purchaseRate = buy.coerceAtLeast(0.0),
                            sellingRate = sell.coerceAtLeast(0.0),
                            gstRate = gst.coerceIn(0.0, 100.0),
                            hsnCode = hsn.trim().uppercase(),
                            unit = unit.trim().ifBlank { "PCS" }.uppercase(),
                            stockQty = stock.coerceAtLeast(0.0),
                            lowStockThreshold = threshold.coerceAtLeast(0.0),
                            taxInclusive = taxInclusive
                        )
                    )
                },
                enabled = name.isNotBlank() && (sellingRate.toDoubleOrNull() ?: -1.0) >= 0.0,
                modifier = Modifier.testTag("save_product_button")
            ) { Text("Save Item") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CustomerEditorDialog(
    initial: Customer?,
    onDismiss: () -> Unit,
    onSave: (Customer) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var gstin by remember { mutableStateOf(initial?.gstin ?: "") }
    var stateName by remember { mutableStateOf(initial?.stateName ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Customer" else "Edit Customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_phone_input")
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Full Address") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_address_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = gstin,
                        onValueChange = { gstin = it.uppercase() },
                        label = { Text("GSTIN (Optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = stateName,
                        onValueChange = { stateName = it },
                        label = { Text("State") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        (initial ?: Customer()).copy(
                            name = name.trim(),
                            phone = phone.trim(),
                            address = address.trim(),
                            gstin = gstin.trim().uppercase(),
                            stateName = stateName.trim()
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_customer_button")
            ) { Text("Save Customer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

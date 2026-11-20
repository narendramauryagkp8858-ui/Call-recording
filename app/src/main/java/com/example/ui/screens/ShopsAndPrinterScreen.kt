package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.billing.ImageStampHelper
import com.example.billing.PrinterDeviceInfo
import com.example.billing.ThermalPrinterManager
import com.example.data.PrinterConfig
import com.example.data.ShopProfile
import com.example.data.signOut
import com.example.ui.BillingViewModel
import com.example.ui.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MonoCodeText
import com.example.ui.components.SectionHeader
import com.example.ui.components.SignaturePadDialog
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun ShopsAndPrinterScreen(
    viewModel: BillingViewModel,
    shopsState: UiState<List<ShopProfile>>,
    printerConfig: PrinterConfig,
    onSelectPrinter: (name: String, address: String) -> Unit,
    onSelectPaperWidth: (Int) -> Unit,
    onTestPrint: (PrinterConfig) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var editingShop by remember { mutableStateOf<ShopProfile?>(null) }
    var showAddShop by remember { mutableStateOf(false) }

    var hasBtPerm by remember { mutableStateOf(ThermalPrinterManager.hasBluetoothPermission(context)) }
    var pairedPrinters by remember(hasBtPerm) {
        mutableStateOf(
            if (hasBtPerm) ThermalPrinterManager.getPairedPrinters(context) else emptyList()
        )
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            results[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else true
        hasBtPerm = granted
        if (granted) {
            pairedPrinters = ThermalPrinterManager.getPairedPrinters(context)
            onShowMessage("Loaded ${pairedPrinters.size} paired Bluetooth devices")
        } else {
            onShowMessage("Bluetooth permission is needed to connect mini thermal printers")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Shops (Dukaan) & GST",
                subtitle = "Any user can add separate shops with Mohar & Signature",
                action = {
                    Button(
                        onClick = { showAddShop = true },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("add_shop_button")
                    ) {
                        Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Shop")
                    }
                }
            )
        }

        when (shopsState) {
            is UiState.Loading -> item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            is UiState.Error -> item { Text(shopsState.message, color = MaterialTheme.colorScheme.error) }
            is UiState.Success -> {
                val shops = shopsState.data
                if (shops.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No Shop Added Yet",
                            description = "Add your Shop / Dukaan name, GSTIN, address, Mohar (Stamp) image, and Signature.",
                            actionLabel = "Create My Shop",
                            testTag = "create_first_shop_button",
                            onAction = { showAddShop = true }
                        )
                    }
                } else {
                    items(shops, key = { it.id }) { shop ->
                        ShopProfileCard(
                            shop = shop,
                            onSetDefault = { viewModel.setActiveDefaultShop(shop) },
                            onEdit = { editingShop = shop },
                            onDelete = { viewModel.deleteShop(shop.id) }
                        )
                    }
                }
            }
        }

        // Bluetooth Mini Thermal Printer Section
        item {
            SectionHeader(
                title = "Bluetooth Mini Thermal Printer",
                subtitle = "Supports 58mm & 80mm ESC/POS portable receipt printers"
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Paper Size", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (printerConfig.deviceName.isNotBlank()) "Selected: ${printerConfig.deviceName}" else "No printer selected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = printerConfig.paperWidthMm == 58,
                                onClick = { onSelectPaperWidth(58) },
                                label = { Text("58mm") },
                                modifier = Modifier.testTag("paper_58mm_chip")
                            )
                            FilterChip(
                                selected = printerConfig.paperWidthMm == 80,
                                onClick = { onSelectPaperWidth(80) },
                                label = { Text("80mm") },
                                modifier = Modifier.testTag("paper_80mm_chip")
                            )
                        }
                    }

                    if (!hasBtPerm) {
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    btPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH_CONNECT,
                                            Manifest.permission.BLUETOOTH_SCAN
                                        )
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("grant_bluetooth_button")
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Allow Bluetooth Printer Access")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pairedPrinters = ThermalPrinterManager.getPairedPrinters(context)
                                    if (pairedPrinters.isEmpty()) {
                                        onShowMessage("No paired Bluetooth devices found. Pair printer in Android Bluetooth settings.")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("refresh_printers_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Refresh Paired")
                            }
                            Button(
                                onClick = { onTestPrint(printerConfig) },
                                enabled = printerConfig.deviceAddress.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("test_print_button")
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Test Print")
                            }
                        }

                        if (pairedPrinters.isEmpty()) {
                            Text(
                                "Pair your mini Bluetooth thermal printer in Android Settings > Bluetooth first, then tap Refresh Paired.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            pairedPrinters.forEach { dev: PrinterDeviceInfo ->
                                val selected = dev.address == printerConfig.deviceAddress
                                Surface(
                                    onClick = { onSelectPrinter(dev.name, dev.address) },
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = { onSelectPrinter(dev.name, dev.address) }
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(dev.name, style = MaterialTheme.typography.titleSmall)
                                            MonoCodeText(dev.address)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Account Card
        item {
            val user = Firebase.auth.currentUser
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Google Account", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(user?.displayName ?: "Merchant", style = MaterialTheme.typography.titleSmall)
                        Text(user?.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick = { signOut(credentialManager, scope) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sign Out")
                    }
                }
            }
        }
    }

    if (showAddShop || editingShop != null) {
        ShopEditorDialog(
            initial = editingShop,
            onDismiss = {
                showAddShop = false
                editingShop = null
            },
            onSave = { shop ->
                viewModel.saveShop(shop)
                showAddShop = false
                editingShop = null
            }
        )
    }
}

@Composable
private fun ShopProfileCard(
    shop: ShopProfile,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val stampBmp = remember(shop.stampBase64) { ImageStampHelper.base64ToBitmap(shop.stampBase64) }
    val signBmp = remember(shop.signatureBase64) { ImageStampHelper.base64ToBitmap(shop.signatureBase64) }

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
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(shop.shopName, style = MaterialTheme.typography.titleMedium)
                        if (shop.defaultActive) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                    if (shop.gstin.isNotBlank()) MonoCodeText("GSTIN: ${shop.gstin}")
                    if (shop.phone.isNotBlank()) Text("Mob: ${shop.phone}", style = MaterialTheme.typography.bodySmall)
                    if (shop.address.isNotBlank()) {
                        Text(shop.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MonoCodeText("Next Bill: ${shop.invoicePrefix}${shop.nextInvoiceNumber.toString().padStart(4, '0')}")
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit shop")
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete shop", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StampMiniPreview(label = "Mohar / Stamp", bitmap = stampBmp, modifier = Modifier.weight(1f))
                StampMiniPreview(label = "Signature", bitmap = signBmp, modifier = Modifier.weight(1f))
            }

            if (!shop.defaultActive) {
                OutlinedButton(
                    onClick = onSetDefault,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                ) {
                    Text("Set as Active Shop")
                }
            }
        }
    }
}

@Composable
private fun StampMiniPreview(
    label: String,
    bitmap: android.graphics.Bitmap?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier
                        .height(44.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Not uploaded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun ShopEditorDialog(
    initial: ShopProfile?,
    onDismiss: () -> Unit,
    onSave: (ShopProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var shopName by remember { mutableStateOf(initial?.shopName ?: "") }
    var gstin by remember { mutableStateOf(initial?.gstin ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var stateName by remember { mutableStateOf(initial?.stateName ?: "") }
    var prefix by remember { mutableStateOf(initial?.invoicePrefix ?: "NRB-") }
    var nextNo by remember { mutableStateOf((initial?.nextInvoiceNumber ?: 1L).toString()) }
    var terms by remember { mutableStateOf(initial?.termsAndConditions ?: "Goods once sold will not be taken back.") }
    var stampBase64 by remember { mutableStateOf(initial?.stampBase64 ?: "") }
    var signatureBase64 by remember { mutableStateOf(initial?.signatureBase64 ?: "") }
    var defaultActive by remember { mutableStateOf(initial?.defaultActive ?: true) }
    var showSignaturePad by remember { mutableStateOf(false) }

    val stampPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val b64 = ImageStampHelper.uriToCompactBase64(context, uri)
                if (b64 != null) stampBase64 = b64
            }
        }
    }

    val signaturePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val b64 = ImageStampHelper.uriToCompactBase64(context, uri)
                if (b64 != null) signatureBase64 = b64
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add New Shop / Dukaan" else "Edit Shop Profile") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it },
                    label = { Text("Shop / Dukaan Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shop_name_input")
                )
                OutlinedTextField(
                    value = gstin,
                    onValueChange = { gstin = it.uppercase() },
                    label = { Text("GSTIN (15-digit)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shop_gstin_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Shop Mobile") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Shop Address") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it.uppercase() },
                        label = { Text("Bill Prefix") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = nextNo,
                        onValueChange = { nextNo = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Next Bill No.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = terms,
                    onValueChange = { terms = it },
                    label = { Text("Terms & Conditions") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                // Upload Mohar (Stamp)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text("Shop Mohar / Stamp", style = MaterialTheme.typography.titleSmall)
                            }
                            if (stampBase64.isNotBlank()) {
                                TextButton(onClick = { stampBase64 = "" }) { Text("Remove") }
                            }
                        }
                        ImageStampHelper.base64ToBitmap(stampBase64)?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Uploaded stamp preview",
                                modifier = Modifier
                                    .height(58.dp)
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(6.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                stampPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("upload_stamp_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (stampBase64.isBlank()) "Upload Mohar / Stamp Image" else "Change Mohar Image")
                        }
                    }
                }

                // Upload or Draw Signature
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text("Authorized Signature", style = MaterialTheme.typography.titleSmall)
                            }
                            if (signatureBase64.isNotBlank()) {
                                TextButton(onClick = { signatureBase64 = "" }) { Text("Remove") }
                            }
                        }
                        ImageStampHelper.base64ToBitmap(signatureBase64)?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Uploaded signature preview",
                                modifier = Modifier
                                    .height(58.dp)
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(6.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    signaturePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("upload_signature_button")
                            ) {
                                Text("Upload Photo")
                            }
                            OutlinedButton(
                                onClick = { showSignaturePad = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("draw_signature_button")
                            ) {
                                Text("Draw Sign")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Set as Active Shop", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = defaultActive, onCheckedChange = { defaultActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val num = nextNo.toLongOrNull()?.coerceIn(1L, 10_000_000L) ?: 1L
                    onSave(
                        (initial ?: ShopProfile()).copy(
                            shopName = shopName.trim(),
                            gstin = gstin.trim().uppercase(),
                            phone = phone.trim(),
                            address = address.trim(),
                            stateName = stateName.trim(),
                            invoicePrefix = prefix.trim().ifBlank { "INV-" },
                            nextInvoiceNumber = num,
                            termsAndConditions = terms.trim(),
                            stampBase64 = stampBase64,
                            signatureBase64 = signatureBase64,
                            defaultActive = defaultActive
                        )
                    )
                },
                enabled = shopName.isNotBlank(),
                modifier = Modifier.testTag("save_shop_button")
            ) {
                Text("Save Shop")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showSignaturePad) {
        SignaturePadDialog(
            onDismiss = { showSignaturePad = false },
            onSaveBase64 = { b64 ->
                signatureBase64 = b64
                showSignaturePad = false
            }
        )
    }
}

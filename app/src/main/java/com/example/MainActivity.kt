package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.billing.InvoicePdfGenerator
import com.example.billing.ThermalPrinterManager
import com.example.data.BillingRepository
import com.example.data.Invoice
import com.example.data.PrinterConfig
import com.example.data.PrinterPreferences
import com.example.data.ShopProfile
import com.example.data.authStateFlow
import com.example.ui.BillingViewModel
import com.example.ui.UiState
import com.example.ui.components.PdfPreviewDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.CreateBillScreen
import com.example.ui.screens.GstCalculatorScreen
import com.example.ui.screens.InvoicesScreen
import com.example.ui.screens.ShopsAndPrinterScreen
import com.example.ui.theme.NarendraBillingTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.io.File

enum class BillingTab(val label: String, val icon: ImageVector, val testTag: String) {
    NEW_BILL("New Bill", Icons.Default.Receipt, "tab_new_bill"),
    BILLS("Bills", Icons.Default.ReceiptLong, "tab_bills"),
    CATALOG("Items", Icons.Default.Inventory2, "tab_catalog"),
    GST_CALC("GST Calc", Icons.Default.Calculate, "tab_gst_calc"),
    SHOPS("Shops", Icons.Default.Storefront, "tab_shops")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NarendraBillingTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            BillingAppShell(currentUserId = user.uid)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingAppShell(currentUserId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: BillingViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                BillingViewModel(BillingRepository(db), currentUserId)
            }
        }
    )

    val printerPrefs = remember(context) { PrinterPreferences(context.applicationContext) }
    val printerConfig by printerPrefs.configFlow.collectAsStateWithLifecycle(initialValue = PrinterConfig())

    val shopsState by viewModel.shopsState.collectAsStateWithLifecycle()
    val productsState by viewModel.productsState.collectAsStateWithLifecycle()
    val customersState by viewModel.customersState.collectAsStateWithLifecycle()
    val invoicesState by viewModel.invoicesState.collectAsStateWithLifecycle()
    val draftBill by viewModel.draftBill.collectAsStateWithLifecycle()
    val lastCreatedInvoice by viewModel.lastCreatedInvoice.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(BillingTab.NEW_BILL) }
    var previewTarget by remember { mutableStateOf<Pair<Invoice, File>?>(null) }

    BackHandler(enabled = currentTab != BillingTab.NEW_BILL) {
        currentTab = BillingTab.NEW_BILL
    }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeStatusMessage()
        }
    }

    fun resolveShopForInvoice(invoice: Invoice): ShopProfile? {
        val shops = (shopsState as? UiState.Success)?.data ?: emptyList()
        return shops.firstOrNull { it.id == invoice.shopId }
            ?: shops.firstOrNull { it.shopName == invoice.shopName }
            ?: viewModel.resolveActiveShop(shops)
    }

    fun launchPdfPreview(invoice: Invoice) {
        scope.launch {
            val shop = resolveShopForInvoice(invoice)
            InvoicePdfGenerator.generateInvoicePdf(context, invoice, shop)
                .onSuccess { (file, _) ->
                    previewTarget = invoice to file
                }
                .onFailure {
                    snackbarHostState.showSnackbar("Could not render PDF: ${it.localizedMessage}")
                }
        }
    }

    fun launchWhatsAppShare(invoice: Invoice) {
        scope.launch {
            val shop = resolveShopForInvoice(invoice)
            InvoicePdfGenerator.generateInvoicePdf(context, invoice, shop)
                .onSuccess { (_, uri) ->
                    InvoicePdfGenerator.sharePdfToWhatsApp(context, uri, invoice)
                }
                .onFailure {
                    snackbarHostState.showSnackbar("Failed to prepare WhatsApp PDF: ${it.localizedMessage}")
                }
        }
    }

    fun launchThermalPrint(invoice: Invoice) {
        scope.launch {
            if (!ThermalPrinterManager.hasBluetoothPermission(context)) {
                snackbarHostState.showSnackbar("Allow Bluetooth permission in the Shops tab first.")
                currentTab = BillingTab.SHOPS
                return@launch
            }
            if (printerConfig.deviceAddress.isBlank()) {
                snackbarHostState.showSnackbar("Select a paired Bluetooth thermal printer in the Shops tab.")
                currentTab = BillingTab.SHOPS
                return@launch
            }
            snackbarHostState.showSnackbar("Printing ${invoice.invoiceNumber} to ${printerConfig.deviceName}...")
            val bytes = ThermalPrinterManager.buildEscPosReceipt(invoice, printerConfig.paperWidthMm)
            ThermalPrinterManager.sendBytesToPrinter(context, printerConfig.deviceAddress, bytes)
                .onSuccess {
                    snackbarHostState.showSnackbar("Printed ${invoice.invoiceNumber} successfully!")
                }
                .onFailure {
                    snackbarHostState.showSnackbar(it.localizedMessage ?: "Thermal print failed.")
                }
        }
    }

    fun launchTestPrint(cfg: PrinterConfig) {
        scope.launch {
            val activeShop = viewModel.resolveActiveShop((shopsState as? UiState.Success)?.data ?: emptyList())
            val bytes = ThermalPrinterManager.buildTestReceipt(
                shopName = activeShop?.shopName ?: "Narendra Billing",
                paperWidthMm = cfg.paperWidthMm
            )
            ThermalPrinterManager.sendBytesToPrinter(context, cfg.deviceAddress, bytes)
                .onSuccess { snackbarHostState.showSnackbar("Test print sent to ${cfg.deviceName}!") }
                .onFailure { snackbarHostState.showSnackbar(it.localizedMessage ?: "Test print failed") }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Narendra Billing",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        BillingTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        BillingTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.widthIn(max = 860.dp).fillMaxSize()) {
                        when (currentTab) {
                            BillingTab.NEW_BILL -> CreateBillScreen(
                                viewModel = viewModel,
                                shopsState = shopsState,
                                productsState = productsState,
                                customersState = customersState,
                                draft = draftBill,
                                lastCreatedInvoice = lastCreatedInvoice,
                                onNavigateToShops = { currentTab = BillingTab.SHOPS },
                                onPreviewPdf = ::launchPdfPreview,
                                onShareWhatsApp = ::launchWhatsAppShare,
                                onPrintThermal = ::launchThermalPrint
                            )
                            BillingTab.BILLS -> InvoicesScreen(
                                viewModel = viewModel,
                                invoicesState = invoicesState,
                                onGoToNewBill = { currentTab = BillingTab.NEW_BILL },
                                onPreviewPdf = ::launchPdfPreview,
                                onShareWhatsApp = ::launchWhatsAppShare,
                                onPrintThermal = ::launchThermalPrint
                            )
                            BillingTab.CATALOG -> CatalogScreen(
                                viewModel = viewModel,
                                productsState = productsState,
                                customersState = customersState,
                                onBillCustomer = { customer ->
                                    viewModel.selectCustomer(customer)
                                    currentTab = BillingTab.NEW_BILL
                                }
                            )
                            BillingTab.GST_CALC -> GstCalculatorScreen(
                                onAddToBill = { amount, gst, incl, inter ->
                                    viewModel.prefillAmountFromCalculator(amount, gst, incl, inter)
                                    currentTab = BillingTab.NEW_BILL
                                }
                            )
                            BillingTab.SHOPS -> ShopsAndPrinterScreen(
                                viewModel = viewModel,
                                shopsState = shopsState,
                                printerConfig = printerConfig,
                                onSelectPrinter = { name, addr ->
                                    scope.launch { printerPrefs.setPrinter(name, addr) }
                                },
                                onSelectPaperWidth = { mm ->
                                    scope.launch { printerPrefs.setPaperWidth(mm) }
                                },
                                onTestPrint = ::launchTestPrint,
                                onShowMessage = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    previewTarget?.let { (invoice, file) ->
        PdfPreviewDialog(
            invoice = invoice,
            pdfFile = file,
            onDismiss = { previewTarget = null },
            onShareWhatsApp = { launchWhatsAppShare(invoice) },
            onPrintThermal = { launchThermalPrint(invoice) }
        )
    }
}

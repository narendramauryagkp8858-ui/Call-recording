package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billing.ComputedLine
import com.example.billing.GstCalculator
import com.example.billing.InvoiceTotals
import com.example.data.BillingRepository
import com.example.data.Customer
import com.example.data.Invoice
import com.example.data.PaymentMode
import com.example.data.ProductItem
import com.example.data.ShopProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class DraftBillLine(
    val localId: String = UUID.randomUUID().toString(),
    val productId: String = "",
    val name: String,
    val hsnCode: String = "",
    val unit: String = "PCS",
    val quantity: Double = 1.0,
    val sellingRate: Double,    // Bikri Rate — shown on customer bill
    val purchaseRate: Double = 0.0, // Khareed Rate — internal profit calc only, NEVER shown on bill
    val gstRate: Double = 18.0,
    val taxInclusive: Boolean = false
) {
    fun computed(): ComputedLine = GstCalculator.computeLine(
        productId = productId,
        name = name,
        hsnCode = hsnCode,
        unit = unit,
        quantity = quantity,
        sellingRate = sellingRate,
        purchaseRate = purchaseRate,
        gstRate = gstRate,
        taxInclusive = taxInclusive
    )
}

data class DraftBillState(
    val selectedShopId: String = "",
    val selectedCustomerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val customerAddress: String = "",
    val customerGstin: String = "",
    val saveNewCustomer: Boolean = false,
    val interState: Boolean = false,
    val includeStamp: Boolean = true,
    val includeSignature: Boolean = true,
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val notes: String = "",
    val lines: List<DraftBillLine> = emptyList(),
    val isSaving: Boolean = false
) {
    fun totals(): InvoiceTotals = GstCalculator.summarizeInvoice(
        lines = lines.map { it.computed() },
        isInterState = interState
    )
}

class BillingViewModel(
    private val repository: BillingRepository,
    private val currentUserId: String
) : ViewModel() {

    private fun <T> Flow<List<T>>.toUiState(label: String): StateFlow<UiState<List<T>>> =
        map<List<T>, UiState<List<T>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val shopsState: StateFlow<UiState<List<ShopProfile>>> =
        repository.observeShops(currentUserId).toUiState("shops")

    val productsState: StateFlow<UiState<List<ProductItem>>> =
        repository.observeProducts(currentUserId).toUiState("products")

    val customersState: StateFlow<UiState<List<Customer>>> =
        repository.observeCustomers(currentUserId).toUiState("customers")

    val invoicesState: StateFlow<UiState<List<Invoice>>> =
        repository.observeInvoices(currentUserId).toUiState("invoices")

    private val _draftBill = MutableStateFlow(DraftBillState())
    val draftBill: StateFlow<DraftBillState> = _draftBill.asStateFlow()

    private val _lastCreatedInvoice = MutableStateFlow<Invoice?>(null)
    val lastCreatedInvoice: StateFlow<Invoice?> = _lastCreatedInvoice.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }

    fun consumeLastCreatedInvoice() {
        _lastCreatedInvoice.value = null
    }

    fun selectShop(shopId: String) {
        _draftBill.update { it.copy(selectedShopId = shopId) }
    }

    fun resolveActiveShop(shops: List<ShopProfile>): ShopProfile? {
        val chosen = _draftBill.value.selectedShopId
        return shops.firstOrNull { it.id == chosen }
            ?: shops.firstOrNull { it.defaultActive }
            ?: shops.firstOrNull()
    }

    fun selectCustomer(customer: Customer) {
        _draftBill.update {
            it.copy(
                selectedCustomerId = customer.id,
                customerName = customer.name,
                customerPhone = customer.phone,
                customerAddress = customer.address,
                customerGstin = customer.gstin,
                saveNewCustomer = false
            )
        }
    }

    fun clearSelectedCustomer() {
        _draftBill.update {
            it.copy(
                selectedCustomerId = "",
                customerName = "",
                customerPhone = "",
                customerAddress = "",
                customerGstin = "",
                saveNewCustomer = false
            )
        }
    }

    fun updateCustomerFields(
        name: String? = null,
        phone: String? = null,
        address: String? = null,
        gstin: String? = null,
        saveToDirectory: Boolean? = null
    ) {
        _draftBill.update { state ->
            val clearId = if (name != null && name != state.customerName && state.selectedCustomerId.isNotBlank()) "" else state.selectedCustomerId
            state.copy(
                selectedCustomerId = clearId,
                customerName = name ?: state.customerName,
                customerPhone = phone ?: state.customerPhone,
                customerAddress = address ?: state.customerAddress,
                customerGstin = gstin ?: state.customerGstin,
                saveNewCustomer = saveToDirectory ?: state.saveNewCustomer
            )
        }
    }

    fun updateBillOptions(
        interState: Boolean? = null,
        includeStamp: Boolean? = null,
        includeSignature: Boolean? = null,
        paymentMode: PaymentMode? = null,
        notes: String? = null
    ) {
        _draftBill.update {
            it.copy(
                interState = interState ?: it.interState,
                includeStamp = includeStamp ?: it.includeStamp,
                includeSignature = includeSignature ?: it.includeSignature,
                paymentMode = paymentMode ?: it.paymentMode,
                notes = notes ?: it.notes
            )
        }
    }

    fun addProductToBill(product: ProductItem) {
        _draftBill.update { state ->
            if (state.lines.size >= 50) return@update state
            val existingIdx = state.lines.indexOfFirst { it.productId == product.id && product.id.isNotBlank() }
            val updatedLines = if (existingIdx >= 0) {
                state.lines.toMutableList().apply {
                    val cur = this[existingIdx]
                    this[existingIdx] = cur.copy(quantity = (cur.quantity + 1.0).coerceAtMost(99999.0))
                }
            } else {
                state.lines + DraftBillLine(
                    productId = product.id,
                    name = product.name,
                    hsnCode = product.hsnCode,
                    unit = product.unit,
                    quantity = 1.0,
                    sellingRate = product.sellingRate,
                    purchaseRate = product.purchaseRate,
                    gstRate = product.gstRate,
                    taxInclusive = product.taxInclusive
                )
            }
            state.copy(lines = updatedLines)
        }
    }

    fun addCustomLineToBill(line: DraftBillLine) {
        _draftBill.update { state ->
            if (state.lines.size >= 50) return@update state
            state.copy(lines = state.lines + line)
        }
    }

    fun updateLineQuantity(localId: String, delta: Double) {
        _draftBill.update { state ->
            val updated = state.lines.mapNotNull { line ->
                if (line.localId != localId) line
                else {
                    val nextQty = GstCalculator.round2(line.quantity + delta)
                    if (nextQty <= 0.0) null else line.copy(quantity = nextQty)
                }
            }
            state.copy(lines = updated)
        }
    }

    fun updateLineDetails(
        localId: String,
        name: String,
        quantity: Double,
        sellingRate: Double,
        gstRate: Double,
        taxInclusive: Boolean,
        unit: String,
        hsnCode: String
    ) {
        _draftBill.update { state ->
            val updated = state.lines.map { line ->
                if (line.localId != localId) line
                else line.copy(
                    name = name.trim().ifBlank { line.name },
                    quantity = quantity.coerceAtLeast(0.01),
                    sellingRate = sellingRate.coerceAtLeast(0.0),
                    gstRate = gstRate.coerceIn(0.0, 100.0),
                    taxInclusive = taxInclusive,
                    unit = unit.trim().ifBlank { "PCS" }.uppercase(),
                    hsnCode = hsnCode.trim().uppercase()
                )
            }
            state.copy(lines = updated)
        }
    }

    fun removeLine(localId: String) {
        _draftBill.update { state ->
            state.copy(lines = state.lines.filterNot { it.localId == localId })
        }
    }

    fun prefillAmountFromCalculator(amount: Double, gstPercent: Double, isInclusive: Boolean, isInterState: Boolean) {
        if (amount <= 0.0) return
        _draftBill.update { state ->
            val line = DraftBillLine(
                name = "Billing Item",
                quantity = 1.0,
                sellingRate = amount,
                purchaseRate = 0.0,
                gstRate = gstPercent,
                taxInclusive = isInclusive
            )
            state.copy(
                interState = isInterState,
                lines = (state.lines + line).take(50)
            )
        }
        _statusMessage.value = "Added calculated amount to New Bill"
    }

    fun generateAndSaveInvoice(activeShop: ShopProfile?) {
        val state = _draftBill.value
        if (state.isSaving) return
        if (activeShop == null) {
            _statusMessage.value = "Please create a Shop profile in the Shops tab first."
            return
        }
        if (state.customerName.trim().isEmpty()) {
            _statusMessage.value = "Please enter customer name."
            return
        }
        if (state.lines.isEmpty()) {
            _statusMessage.value = "Please add at least one item to generate bill."
            return
        }

        viewModelScope.launch {
            _draftBill.update { it.copy(isSaving = true) }
            val computedLines = state.lines.map { it.computed() }
            val totals = GstCalculator.summarizeInvoice(computedLines, state.interState)
            val paddedNo = activeShop.nextInvoiceNumber.toString().padStart(4, '0')
            val invoiceNumber = "${activeShop.invoicePrefix}$paddedNo"

            val invoice = Invoice(
                invoiceNumber = invoiceNumber,
                shopId = activeShop.id,
                shopName = activeShop.shopName,
                shopGstin = activeShop.gstin,
                shopPhone = activeShop.phone,
                shopAddress = activeShop.address,
                customerId = state.selectedCustomerId,
                customerName = state.customerName.trim(),
                customerPhone = state.customerPhone.trim(),
                customerAddress = state.customerAddress.trim(),
                customerGstin = state.customerGstin.trim().uppercase(),
                interState = state.interState,
                includeStamp = state.includeStamp,
                includeSignature = state.includeSignature,
                items = computedLines.map { it.lineItem },
                subtotalTaxable = totals.subtotalTaxable,
                cgstAmount = totals.cgstAmount,
                sgstAmount = totals.sgstAmount,
                igstAmount = totals.igstAmount,
                totalGstAmount = totals.totalGstAmount,
                grandTotal = totals.grandTotal,
                estimatedProfit = totals.estimatedProfit,
                paymentMode = state.paymentMode.name,
                notes = state.notes.trim(),
                issuedAtMillis = System.currentTimeMillis()
            )

            val newCustomer = if (state.saveNewCustomer && state.selectedCustomerId.isBlank()) {
                Customer(
                    name = state.customerName.trim(),
                    phone = state.customerPhone.trim(),
                    address = state.customerAddress.trim(),
                    gstin = state.customerGstin.trim().uppercase(),
                    stateName = activeShop.stateName
                )
            } else null

            val result = repository.createInvoice(invoice, activeShop, newCustomer)
            result.onSuccess { saved ->
                _lastCreatedInvoice.value = saved
                _draftBill.update {
                    DraftBillState(
                        selectedShopId = activeShop.id,
                        includeStamp = it.includeStamp,
                        includeSignature = it.includeSignature,
                        paymentMode = it.paymentMode,
                        isSaving = false
                    )
                }
                _statusMessage.value = "GST Bill ${saved.invoiceNumber} saved!"
            }.onFailure { err ->
                _draftBill.update { it.copy(isSaving = false) }
                _statusMessage.value = err.localizedMessage ?: "Failed to save invoice."
            }
        }
    }

    fun saveShop(shop: ShopProfile) {
        val currentShops = (shopsState.value as? UiState.Success)?.data ?: emptyList()
        val withDefault = if (currentShops.isEmpty()) shop.copy(defaultActive = true) else shop
        viewModelScope.launch {
            repository.saveShop(withDefault, currentShops)
                .onSuccess { newId ->
                    _draftBill.update {
                        if (it.selectedShopId.isBlank() || withDefault.defaultActive) it.copy(selectedShopId = newId) else it
                    }
                    _statusMessage.value = "Shop \"${shop.shopName}\" saved"
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to save shop" }
        }
    }

    fun setActiveDefaultShop(shop: ShopProfile) {
        val currentShops = (shopsState.value as? UiState.Success)?.data ?: emptyList()
        viewModelScope.launch {
            repository.saveShop(shop.copy(defaultActive = true), currentShops)
                .onSuccess {
                    _draftBill.update { it.copy(selectedShopId = shop.id) }
                    _statusMessage.value = "${shop.shopName} set as active shop"
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to switch shop" }
        }
    }

    fun deleteShop(shopId: String) {
        viewModelScope.launch {
            repository.deleteShop(shopId)
                .onSuccess { _statusMessage.value = "Shop deleted" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to delete shop" }
        }
    }

    fun saveProduct(product: ProductItem) {
        viewModelScope.launch {
            repository.saveProduct(product)
                .onSuccess { _statusMessage.value = "Item \"${product.name}\" saved" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to save item" }
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            repository.deleteProduct(productId)
                .onSuccess { _statusMessage.value = "Item deleted" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to delete item" }
        }
    }

    fun saveCustomer(customer: Customer) {
        viewModelScope.launch {
            repository.saveCustomer(customer)
                .onSuccess { _statusMessage.value = "Customer \"${customer.name}\" saved" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to save customer" }
        }
    }

    fun deleteCustomer(customerId: String) {
        viewModelScope.launch {
            repository.deleteCustomer(customerId)
                .onSuccess { _statusMessage.value = "Customer deleted" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to delete customer" }
        }
    }

    fun updateInvoiceOptions(
        invoiceId: String,
        includeStamp: Boolean,
        includeSignature: Boolean,
        paymentMode: PaymentMode,
        notes: String
    ) {
        viewModelScope.launch {
            repository.updateInvoiceOptions(invoiceId, includeStamp, includeSignature, paymentMode, notes)
                .onSuccess { _statusMessage.value = "Bill options updated" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to update bill" }
        }
    }

    fun deleteInvoice(invoiceId: String) {
        viewModelScope.launch {
            repository.deleteInvoice(invoiceId)
                .onSuccess { _statusMessage.value = "Bill deleted" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to delete bill" }
        }
    }

    private companion object {
        const val TAG = "BillingVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}

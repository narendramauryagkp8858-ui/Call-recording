package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class ShopProfile(
    @DocumentId val id: String = "",
    val shopName: String = "",
    val gstin: String = "",
    val phone: String = "",
    val address: String = "",
    val stateName: String = "",
    val invoicePrefix: String = "INV-",
    val nextInvoiceNumber: Long = 1L,
    val termsAndConditions: String = "Goods once sold will not be taken back. Subject to local jurisdiction.",
    val stampBase64: String = "",
    val signatureBase64: String = "",
    val defaultActive: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "shopName" to shopName.trim().take(120),
        "gstin" to gstin.trim().uppercase().take(20),
        "phone" to phone.trim().take(20),
        "address" to address.trim().take(300),
        "stateName" to stateName.trim().take(60),
        "invoicePrefix" to invoicePrefix.trim().ifEmpty { "INV-" }.take(16),
        "nextInvoiceNumber" to nextInvoiceNumber.coerceIn(1L, 10_000_000L),
        "termsAndConditions" to termsAndConditions.trim().take(400),
        "stampBase64" to stampBase64.take(180_000),
        "signatureBase64" to signatureBase64.take(180_000),
        "defaultActive" to defaultActive,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "shopName" to shopName.trim().take(120),
        "gstin" to gstin.trim().uppercase().take(20),
        "phone" to phone.trim().take(20),
        "address" to address.trim().take(300),
        "stateName" to stateName.trim().take(60),
        "invoicePrefix" to invoicePrefix.trim().ifEmpty { "INV-" }.take(16),
        "nextInvoiceNumber" to nextInvoiceNumber.coerceIn(1L, 10_000_000L),
        "termsAndConditions" to termsAndConditions.trim().take(400),
        "stampBase64" to stampBase64.take(180_000),
        "signatureBase64" to signatureBase64.take(180_000),
        "defaultActive" to defaultActive,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class ProductItem(
    @DocumentId val id: String = "",
    val name: String = "",
    val hsnCode: String = "",
    val unit: String = "PCS",
    val purchaseRate: Double = 0.0, // Khareed Rate (private to shopkeeper ONLY)
    val sellingRate: Double = 0.0,  // Bikri Rate (printed on GST bills)
    val gstRate: Double = 18.0,
    val taxInclusive: Boolean = false,
    val stockQty: Double = 0.0,
    val lowStockThreshold: Double = 5.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "name" to name.trim().take(120),
        "hsnCode" to hsnCode.trim().uppercase().take(20),
        "unit" to unit.trim().ifEmpty { "PCS" }.uppercase().take(20),
        "purchaseRate" to purchaseRate.coerceIn(0.0, 100_000_000.0),
        "sellingRate" to sellingRate.coerceIn(0.0, 100_000_000.0),
        "gstRate" to gstRate.coerceIn(0.0, 100.0),
        "taxInclusive" to taxInclusive,
        "stockQty" to stockQty.coerceIn(0.0, 10_000_000.0),
        "lowStockThreshold" to lowStockThreshold.coerceIn(0.0, 10_000_000.0),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "name" to name.trim().take(120),
        "hsnCode" to hsnCode.trim().uppercase().take(20),
        "unit" to unit.trim().ifEmpty { "PCS" }.uppercase().take(20),
        "purchaseRate" to purchaseRate.coerceIn(0.0, 100_000_000.0),
        "sellingRate" to sellingRate.coerceIn(0.0, 100_000_000.0),
        "gstRate" to gstRate.coerceIn(0.0, 100.0),
        "taxInclusive" to taxInclusive,
        "stockQty" to stockQty.coerceIn(0.0, 10_000_000.0),
        "lowStockThreshold" to lowStockThreshold.coerceIn(0.0, 10_000_000.0),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Customer(
    @DocumentId val id: String = "",
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val gstin: String = "",
    val stateName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "name" to name.trim().take(120),
        "phone" to phone.trim().take(20),
        "address" to address.trim().take(300),
        "gstin" to gstin.trim().uppercase().take(20),
        "stateName" to stateName.trim().take(60),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "name" to name.trim().take(120),
        "phone" to phone.trim().take(20),
        "address" to address.trim().take(300),
        "gstin" to gstin.trim().uppercase().take(20),
        "stateName" to stateName.trim().take(60),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

/**
 * Customer-facing line item stored inside an Invoice.
 * Contains ONLY Bikri (selling) rate — NEVER purchase/Khareed rate.
 */
@Keep
@IgnoreExtraProperties
data class InvoiceLineItem(
    val productId: String = "",
    val name: String = "",
    val hsnCode: String = "",
    val unit: String = "PCS",
    val quantity: Double = 1.0,
    val sellingRate: Double = 0.0,
    val gstRate: Double = 18.0,
    val taxInclusive: Boolean = false,
    val taxableAmount: Double = 0.0,
    val gstAmount: Double = 0.0,
    val lineTotal: Double = 0.0
) {
    fun toMap(): Map<String, Any> = mapOf(
        "productId" to productId.take(128),
        "name" to name.trim().take(120),
        "hsnCode" to hsnCode.trim().uppercase().take(20),
        "unit" to unit.trim().ifEmpty { "PCS" }.uppercase().take(20),
        "quantity" to quantity.coerceAtLeast(0.001),
        "sellingRate" to sellingRate.coerceAtLeast(0.0),
        "gstRate" to gstRate.coerceIn(0.0, 100.0),
        "taxInclusive" to taxInclusive,
        "taxableAmount" to taxableAmount.coerceAtLeast(0.0),
        "gstAmount" to gstAmount.coerceAtLeast(0.0),
        "lineTotal" to lineTotal.coerceAtLeast(0.0)
    )
}

enum class PaymentMode(val label: String) {
    CASH("Cash"),
    UPI("UPI"),
    CARD("Card"),
    CREDIT("Credit / Udhar");

    companion object {
        fun fromRaw(raw: String): PaymentMode =
            entries.firstOrNull { it.name == raw.uppercase() } ?: CASH
    }
}

@Keep
@IgnoreExtraProperties
data class Invoice(
    @DocumentId val id: String = "",
    val invoiceNumber: String = "",
    val shopId: String = "",
    val shopName: String = "",
    val shopGstin: String = "",
    val shopPhone: String = "",
    val shopAddress: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val customerAddress: String = "",
    val customerGstin: String = "",
    val interState: Boolean = false,
    val includeStamp: Boolean = true,
    val includeSignature: Boolean = true,
    val items: List<InvoiceLineItem> = emptyList(),
    val subtotalTaxable: Double = 0.0,
    val cgstAmount: Double = 0.0,
    val sgstAmount: Double = 0.0,
    val igstAmount: Double = 0.0,
    val totalGstAmount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val estimatedProfit: Double = 0.0, // Private shopkeeper analytics ONLY
    val paymentMode: String = PaymentMode.CASH.name,
    val notes: String = "",
    val issuedAtMillis: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "invoiceNumber" to invoiceNumber.trim().take(40),
        "shopId" to shopId.take(128),
        "shopName" to shopName.trim().take(120),
        "shopGstin" to shopGstin.trim().uppercase().take(20),
        "shopPhone" to shopPhone.trim().take(20),
        "shopAddress" to shopAddress.trim().take(300),
        "customerId" to customerId.take(128),
        "customerName" to customerName.trim().take(120),
        "customerPhone" to customerPhone.trim().take(20),
        "customerAddress" to customerAddress.trim().take(300),
        "customerGstin" to customerGstin.trim().uppercase().take(20),
        "interState" to interState,
        "includeStamp" to includeStamp,
        "includeSignature" to includeSignature,
        "items" to items.take(50).map { it.toMap() },
        "subtotalTaxable" to subtotalTaxable.coerceAtLeast(0.0),
        "cgstAmount" to cgstAmount.coerceAtLeast(0.0),
        "sgstAmount" to sgstAmount.coerceAtLeast(0.0),
        "igstAmount" to igstAmount.coerceAtLeast(0.0),
        "totalGstAmount" to totalGstAmount.coerceAtLeast(0.0),
        "grandTotal" to grandTotal.coerceAtLeast(0.0),
        "estimatedProfit" to estimatedProfit.coerceIn(-1_000_000_000.0, 1_000_000_000.0),
        "paymentMode" to PaymentMode.fromRaw(paymentMode).name,
        "notes" to notes.trim().take(300),
        "issuedAtMillis" to issuedAtMillis.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toEditUpdateMap(): Map<String, Any> = mapOf(
        "customerId" to customerId.take(128),
        "customerName" to customerName.trim().take(120),
        "customerPhone" to customerPhone.trim().take(20),
        "customerAddress" to customerAddress.trim().take(300),
        "customerGstin" to customerGstin.trim().uppercase().take(20),
        "interState" to interState,
        "includeStamp" to includeStamp,
        "includeSignature" to includeSignature,
        "items" to items.take(50).map { it.toMap() },
        "subtotalTaxable" to subtotalTaxable.coerceAtLeast(0.0),
        "cgstAmount" to cgstAmount.coerceAtLeast(0.0),
        "sgstAmount" to sgstAmount.coerceAtLeast(0.0),
        "igstAmount" to igstAmount.coerceAtLeast(0.0),
        "totalGstAmount" to totalGstAmount.coerceAtLeast(0.0),
        "grandTotal" to grandTotal.coerceAtLeast(0.0),
        "estimatedProfit" to estimatedProfit.coerceIn(-1_000_000_000.0, 1_000_000_000.0),
        "paymentMode" to PaymentMode.fromRaw(paymentMode).name,
        "notes" to notes.trim().take(300),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

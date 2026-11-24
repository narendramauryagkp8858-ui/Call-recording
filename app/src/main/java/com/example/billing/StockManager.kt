package com.example.billing

import com.example.data.Invoice
import com.example.data.InvoiceLineItem
import com.example.data.PaymentMode
import com.example.data.ProductItem
import kotlin.math.abs

enum class StockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK
}

data class EditedInvoiceLine(
    val key: String,
    val productId: String,
    val name: String,
    val hsnCode: String,
    val unit: String,
    val originalQuantity: Double,
    val quantity: Double,
    val sellingRate: Double,
    val gstRate: Double,
    val taxInclusive: Boolean,
    val imeiNumber: String = ""
)

object StockManager {

    const val DEFAULT_LOW_STOCK_THRESHOLD = 5.0

    fun getStockStatus(stockQty: Double, lowStockThreshold: Double = DEFAULT_LOW_STOCK_THRESHOLD): StockStatus {
        return when {
            stockQty <= 0.0 -> StockStatus.OUT_OF_STOCK
            lowStockThreshold > 0.0 && stockQty <= lowStockThreshold -> StockStatus.LOW_STOCK
            else -> StockStatus.IN_STOCK
        }
    }

    fun isLowOrOutOfStock(product: ProductItem): Boolean {
        return getStockStatus(product.stockQty, product.lowStockThreshold) != StockStatus.IN_STOCK
    }

    /**
     * Aggregates total quantities grouped by non-blank productId.
     */
    fun aggregateProductQuantities(items: List<InvoiceLineItem>): Map<String, Double> {
        val totals = linkedMapOf<String, Double>()
        items.forEach { item ->
            val pid = item.productId.trim()
            if (pid.isNotEmpty() && item.quantity > 0.0) {
                totals[pid] = GstCalculator.round2((totals[pid] ?: 0.0) + item.quantity)
            }
        }
        return totals
    }

    /**
     * Returns stock deltas (negative) when creating an invoice.
     */
    fun computeStockDeltasForCreate(items: List<InvoiceLineItem>): Map<String, Double> {
        return aggregateProductQuantities(items).mapValues { (_, qty) ->
            GstCalculator.round2(-qty)
        }
    }

    /**
     * Returns net stock deltas when updating an existing invoice.
     * Positive delta = stock restored (e.g. product returned / qty reduced).
     * Negative delta = additional stock deducted (e.g. qty increased / item added).
     */
    fun computeStockDeltasForUpdate(
        oldItems: List<InvoiceLineItem>,
        newItems: List<InvoiceLineItem>
    ): Map<String, Double> {
        val oldMap = aggregateProductQuantities(oldItems)
        val newMap = aggregateProductQuantities(newItems)
        val allIds = (oldMap.keys + newMap.keys).distinct()
        val deltas = linkedMapOf<String, Double>()
        allIds.forEach { pid ->
            val oldQty = oldMap[pid] ?: 0.0
            val newQty = newMap[pid] ?: 0.0
            val delta = GstCalculator.round2(oldQty - newQty)
            if (abs(delta) > 0.0001) {
                deltas[pid] = delta
            }
        }
        return deltas
    }

    /**
     * Applies stock delta without dropping below 0 or exceeding Firestore limit.
     */
    fun computeUpdatedStockQty(currentStock: Double, delta: Double): Double {
        return GstCalculator.round2((currentStock + delta).coerceIn(0.0, 10_000_000.0))
    }

    /**
     * Recomputes all line totals, GST splits, grandTotal, and private estimatedProfit
     * when an invoice is edited or items are returned.
     */
    fun recalculateEditedInvoice(
        original: Invoice,
        editedLines: List<EditedInvoiceLine>,
        productsById: Map<String, ProductItem>,
        customerName: String,
        customerPhone: String,
        customerAddress: String,
        customerGstin: String,
        interState: Boolean,
        includeStamp: Boolean,
        includeSignature: Boolean,
        paymentMode: PaymentMode,
        notes: String
    ): Invoice {
        val activeLines = editedLines.filter { it.quantity > 0.0 }.take(50)
        val computedLines = activeLines.map { line ->
            val purchaseRate = productsById[line.productId]?.purchaseRate ?: 0.0
            GstCalculator.computeLine(
                productId = line.productId,
                name = line.name,
                hsnCode = line.hsnCode,
                unit = line.unit,
                quantity = line.quantity,
                sellingRate = line.sellingRate,
                purchaseRate = purchaseRate,
                gstRate = line.gstRate,
                taxInclusive = line.taxInclusive,
                imeiNumber = line.imeiNumber
            )
        }
        val totals = GstCalculator.summarizeInvoice(computedLines, interState)
        return original.copy(
            customerName = customerName.trim(),
            customerPhone = customerPhone.trim(),
            customerAddress = customerAddress.trim(),
            customerGstin = customerGstin.trim().uppercase(),
            interState = interState,
            includeStamp = includeStamp,
            includeSignature = includeSignature,
            items = computedLines.map { it.lineItem },
            subtotalTaxable = totals.subtotalTaxable,
            cgstAmount = totals.cgstAmount,
            sgstAmount = totals.sgstAmount,
            igstAmount = totals.igstAmount,
            totalGstAmount = totals.totalGstAmount,
            grandTotal = totals.grandTotal,
            estimatedProfit = totals.estimatedProfit,
            paymentMode = paymentMode.name,
            notes = notes.trim()
        )
    }
}

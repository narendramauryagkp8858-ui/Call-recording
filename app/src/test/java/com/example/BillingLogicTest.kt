package com.example

import com.example.billing.GstCalculator
import com.example.billing.ThermalPrinterManager
import com.example.data.Invoice
import com.example.data.PaymentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingLogicTest {

    @Test
    fun gstExclusiveIntraState_splitsCgstAndSgstEqually() {
        val result = GstCalculator.calculateSingleAmount(
            amount = 1000.0,
            gstPercent = 18.0,
            isInclusive = false,
            isInterState = false
        )
        assertEquals(1000.0, result.baseAmount, 0.001)
        assertEquals(90.0, result.cgstAmount, 0.001)
        assertEquals(90.0, result.sgstAmount, 0.001)
        assertEquals(0.0, result.igstAmount, 0.001)
        assertEquals(180.0, result.totalGstAmount, 0.001)
        assertEquals(1180.0, result.grandTotal, 0.001)
    }

    @Test
    fun gstInclusiveInterState_extractsBaseAndIgst() {
        val result = GstCalculator.calculateSingleAmount(
            amount = 1180.0,
            gstPercent = 18.0,
            isInclusive = true,
            isInterState = true
        )
        assertEquals(1000.0, result.baseAmount, 0.001)
        assertEquals(0.0, result.cgstAmount, 0.001)
        assertEquals(0.0, result.sgstAmount, 0.001)
        assertEquals(180.0, result.igstAmount, 0.001)
        assertEquals(1180.0, result.grandTotal, 0.001)
    }

    @Test
    fun computeLine_usesOnlyBikriRateOnInvoiceItemAndTracksProfitPrivately() {
        val line = GstCalculator.computeLine(
            productId = "p1",
            name = "Sugar 1KG",
            hsnCode = "1701",
            unit = "KG",
            quantity = 10.0,
            sellingRate = 50.0,   // Bikri Rate
            purchaseRate = 38.0,  // Khareed Rate (private)
            gstRate = 5.0,
            taxInclusive = false
        )
        assertEquals(50.0, line.lineItem.sellingRate, 0.001)
        assertEquals(500.0, line.lineItem.taxableAmount, 0.001)
        assertEquals(25.0, line.lineItem.gstAmount, 0.001)
        assertEquals(525.0, line.lineItem.lineTotal, 0.001)
        assertEquals(120.0, line.lineProfit, 0.001)

        val invoice = Invoice(
            invoiceNumber = "NRB-0001",
            shopName = "Narendra Store",
            shopGstin = "09ABCDE1234F1Z5",
            customerName = "Mohan",
            customerPhone = "9876543210",
            customerAddress = "Main Market",
            interState = false,
            items = listOf(line.lineItem),
            subtotalTaxable = 500.0,
            cgstAmount = 12.5,
            sgstAmount = 12.5,
            totalGstAmount = 25.0,
            grandTotal = 525.0,
            estimatedProfit = 120.0,
            paymentMode = PaymentMode.UPI.name
        )

        val escPosBytes = ThermalPrinterManager.buildEscPosReceipt(invoice, paperWidthMm = 58)
        val receiptText = String(escPosBytes, Charsets.US_ASCII)

        assertTrue(receiptText.contains("Narendra Store"))
        assertTrue(receiptText.contains("NRB-0001"))
        assertTrue(receiptText.contains("50.00"))
        assertTrue(receiptText.contains("525.00"))
        // Khareed rate (38.00) and private profit (120.00) MUST NOT appear on customer receipt
        assertFalse(receiptText.contains("38.00"))
        assertFalse(receiptText.contains("120.00"))
    }

    @Test
    fun amountToIndianWords_formatsLakhsAndThousands() {
        val words = GstCalculator.amountToIndianWords(125450.50)
        assertEquals(
            "Rupees One Lakh Twenty Five Thousand Four Hundred Fifty and Fifty Paise Only",
            words
        )
    }

    @Test
    fun stockManager_deductsOnSaleAndRestoresOnReturn() {
        val phoneSoldOne = com.example.data.InvoiceLineItem(
            productId = "phone_1",
            name = "Phone",
            unit = "PCS",
            quantity = 1.0,
            sellingRate = 10000.0
        )
        val createDeltas = com.example.billing.StockManager.computeStockDeltasForCreate(listOf(phoneSoldOne))
        assertEquals(-1.0, createDeltas["phone_1"] ?: 0.0, 0.001)
        val afterSaleStock = com.example.billing.StockManager.computeUpdatedStockQty(10.0, createDeltas["phone_1"]!!)
        assertEquals(9.0, afterSaleStock, 0.001)

        // Return 1 out of 2 units sold
        val billedTwo = listOf(phoneSoldOne.copy(quantity = 2.0))
        val updatedOne = listOf(phoneSoldOne.copy(quantity = 1.0))
        val returnDeltas = com.example.billing.StockManager.computeStockDeltasForUpdate(billedTwo, updatedOne)
        assertEquals(1.0, returnDeltas["phone_1"] ?: 0.0, 0.001)
        val restoredStock = com.example.billing.StockManager.computeUpdatedStockQty(8.0, returnDeltas["phone_1"]!!)
        assertEquals(9.0, restoredStock, 0.001)

        // Never drop below zero when billed above available stock
        assertEquals(0.0, com.example.billing.StockManager.computeUpdatedStockQty(2.0, -5.0), 0.001)

        // Low stock threshold detection
        assertEquals(
            com.example.billing.StockStatus.OUT_OF_STOCK,
            com.example.billing.StockManager.getStockStatus(0.0, 5.0)
        )
        assertEquals(
            com.example.billing.StockStatus.LOW_STOCK,
            com.example.billing.StockManager.getStockStatus(4.0, 5.0)
        )
        assertEquals(
            com.example.billing.StockStatus.IN_STOCK,
            com.example.billing.StockManager.getStockStatus(9.0, 5.0)
        )
    }
}

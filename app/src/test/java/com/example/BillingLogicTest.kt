package com.example

import com.example.billing.GstCalculator
import com.example.billing.ThermalPrinterManager
import com.example.data.Invoice
import com.example.data.PaymentMode
import com.example.data.formatE164PhoneNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun formatE164PhoneNumber_formatsIndianAndInternationalNumbers() {
        assertEquals("+919876543210", formatE164PhoneNumber("+91", "98765 43210"))
        assertEquals("+14155552671", formatE164PhoneNumber("+1", "4155552671"))
        assertEquals("+919876543210", formatE164PhoneNumber("+1", "+91 9876543210"))
        assertNull(formatE164PhoneNumber("+91", "12345"))
    }
}

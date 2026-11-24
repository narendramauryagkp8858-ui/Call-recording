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

    @Test
    fun appMigration_resolvesMonotonicVersionCodeNeverDowngrades() {
        assertEquals(
            2,
            com.example.data.AppDataMigrationManager.resolveMonotonicVersionCode(baseVersionCode = 2, ciRunCode = null)
        )
        assertEquals(
            12,
            com.example.data.AppDataMigrationManager.resolveMonotonicVersionCode(baseVersionCode = 2, ciRunCode = 12)
        )
        assertEquals(
            20,
            com.example.data.AppDataMigrationManager.resolveMonotonicVersionCode(baseVersionCode = 20, ciRunCode = 5)
        )
    }

    @Test
    fun customer_createAndUpdateMaps_preserveTrimmedFieldsAndUppercaseGstin() {
        val edited = com.example.data.Customer(
            id = "cust_1",
            name = "  Ramesh Traders  ",
            phone = " 9876543210 ",
            address = "  Main Bazaar, Lucknow ",
            gstin = " 09abcde1234f1z5 ",
            stateName = " Uttar Pradesh "
        )
        val updateMap = edited.toUpdateMap()
        assertEquals("Ramesh Traders", updateMap["name"])
        assertEquals("9876543210", updateMap["phone"])
        assertEquals("Main Bazaar, Lucknow", updateMap["address"])
        assertEquals("09ABCDE1234F1Z5", updateMap["gstin"])
        assertEquals("Uttar Pradesh", updateMap["stateName"])
        assertFalse(updateMap.containsKey("createdAt"))
        assertTrue(updateMap.containsKey("updatedAt"))
    }

    @Test
    fun warrantyManager_evaluatesActive_7DayAlertWindow_andAutoExpiryRemoval() {
        val legacyInvoice = Invoice(invoiceNumber = "NRB-0009", grandTotal = 999.0)
        assertEquals(
            com.example.billing.WarrantyStatus.NONE,
            com.example.billing.WarrantyManager.evaluate(legacyInvoice).status
        )
        assertFalse(com.example.billing.WarrantyManager.isWarrantyActiveOnBill(legacyInvoice))

        val dayMillis = 86_400_000L
        val issueCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.JANUARY, 1, 10, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val issuedAt = issueCal.timeInMillis
        val expiryMillis = com.example.billing.WarrantyManager.computeWarrantyEndMillis(
            startMillis = issuedAt,
            months = 0,
            days = 10
        )

        val invoiceWithWarranty = Invoice(
            invoiceNumber = "NRB-0010",
            shopName = "Narendra Store",
            customerName = "Ramesh",
            grandTotal = 1500.0,
            hasWarranty = true,
            warrantyMonths = 0,
            warrantyDays = 10,
            warrantyEndMillis = expiryMillis,
            warrantyLabel = com.example.billing.WarrantyManager.formatWarrantyLabel(0, 10, "Full Replacement"),
            issuedAtMillis = issuedAt
        )

        // Day 1 (9 days remaining) -> ACTIVE, not yet in 7-day alert window
        val day1Eval = com.example.billing.WarrantyManager.evaluate(invoiceWithWarranty, issuedAt + dayMillis)
        assertEquals(com.example.billing.WarrantyStatus.ACTIVE, day1Eval.status)
        assertEquals(9, day1Eval.daysRemaining)
        assertTrue(com.example.billing.WarrantyManager.isWarrantyActiveOnBill(invoiceWithWarranty, issuedAt + dayMillis))
        assertTrue(
            com.example.billing.WarrantyManager.getExpiringSoonInvoices(
                listOf(invoiceWithWarranty),
                issuedAt + dayMillis
            ).isEmpty()
        )

        // Day 3 (7 days remaining) -> EXPIRING_SOON notification window triggers!
        val day3Now = issuedAt + 3 * dayMillis
        val day3Eval = com.example.billing.WarrantyManager.evaluate(invoiceWithWarranty, day3Now)
        assertEquals(com.example.billing.WarrantyStatus.EXPIRING_SOON, day3Eval.status)
        assertEquals(7, day3Eval.daysRemaining)
        assertTrue(com.example.billing.WarrantyManager.isWarrantyActiveOnBill(invoiceWithWarranty, day3Now))
        assertEquals(
            1,
            com.example.billing.WarrantyManager.getExpiringSoonInvoices(listOf(invoiceWithWarranty), day3Now).size
        )

        // Receipt shows warranty while active/expiring soon
        val activeReceipt = String(
            ThermalPrinterManager.buildEscPosReceipt(invoiceWithWarranty, paperWidthMm = 58, nowMillis = day3Now),
            Charsets.US_ASCII
        )
        assertTrue(activeReceipt.contains("Warranty:"))
        assertTrue(activeReceipt.contains("Valid Till:"))

        // Expiry day itself (0 days left) -> still EXPIRING_SOON
        val expiryDayNoon = expiryMillis - 3_600_000L
        val day10Eval = com.example.billing.WarrantyManager.evaluate(invoiceWithWarranty, expiryDayNoon)
        assertEquals(com.example.billing.WarrantyStatus.EXPIRING_SOON, day10Eval.status)
        assertEquals(0, day10Eval.daysRemaining)

        // After expiry -> EXPIRED & automatically removed from bill/receipt & alerts
        val afterExpiryNow = expiryMillis + 1_000L
        val expiredEval = com.example.billing.WarrantyManager.evaluate(invoiceWithWarranty, afterExpiryNow)
        assertEquals(com.example.billing.WarrantyStatus.EXPIRED, expiredEval.status)
        assertFalse(com.example.billing.WarrantyManager.isWarrantyActiveOnBill(invoiceWithWarranty, afterExpiryNow))
        assertTrue(
            com.example.billing.WarrantyManager.getExpiringSoonInvoices(
                listOf(invoiceWithWarranty),
                afterExpiryNow
            ).isEmpty()
        )
        assertEquals(
            1,
            com.example.billing.WarrantyManager.getExpiredTaggedInvoices(
                listOf(invoiceWithWarranty),
                afterExpiryNow
            ).size
        )

        val expiredReceipt = String(
            ThermalPrinterManager.buildEscPosReceipt(invoiceWithWarranty, paperWidthMm = 58, nowMillis = afterExpiryNow),
            Charsets.US_ASCII
        )
        assertFalse(expiredReceipt.contains("Warranty:"))
        assertFalse(expiredReceipt.contains("Valid Till:"))
    }

    @Test
    fun mobileImeiHelper_detectsPhones_combinesSplitsAndPrintsOnReceipt() {
        assertTrue(com.example.billing.MobileImeiHelper.isLikelyMobilePhone("Vivo Y200 5G", ""))
        assertTrue(com.example.billing.MobileImeiHelper.isLikelyMobilePhone("Smart Device", "8517"))
        assertFalse(com.example.billing.MobileImeiHelper.isLikelyMobilePhone("Basmati Rice 25kg", "1006"))

        val combined = com.example.billing.MobileImeiHelper.combineImeiNumbers("352099001761481", "352099001761482")
        assertEquals("352099001761481 / 352099001761482", combined)
        assertTrue(com.example.billing.MobileImeiHelper.hasStandard15DigitImei(combined))

        val (imei1, imei2) = com.example.billing.MobileImeiHelper.splitImeiNumbers(combined)
        assertEquals("352099001761481", imei1)
        assertEquals("352099001761482", imei2)

        val computed = GstCalculator.computeLine(
            productId = "mob_1",
            name = "Redmi Note 14 5G",
            hsnCode = "8517",
            unit = "PCS",
            quantity = 1.0,
            sellingRate = 18000.0,
            purchaseRate = 15500.0,
            gstRate = 18.0,
            taxInclusive = true,
            imeiNumber = combined
        )
        assertEquals(combined, computed.lineItem.imeiNumber)

        val invoice = Invoice(
            invoiceNumber = "NRB-0022",
            shopName = "Narendra Telecom",
            customerName = "Suresh",
            items = listOf(computed.lineItem),
            subtotalTaxable = computed.lineItem.taxableAmount,
            cgstAmount = computed.lineItem.gstAmount / 2.0,
            sgstAmount = computed.lineItem.gstAmount / 2.0,
            totalGstAmount = computed.lineItem.gstAmount,
            grandTotal = computed.lineItem.lineTotal,
            estimatedProfit = computed.lineProfit
        )

        val receiptText = String(
            ThermalPrinterManager.buildEscPosReceipt(invoice, paperWidthMm = 58),
            Charsets.US_ASCII
        )
        assertTrue(receiptText.contains("IMEI: 352099001761481"))
        assertTrue(receiptText.contains("352099001761482"))
        // Khareed rate (15500.00) must still remain private
        assertFalse(receiptText.contains("15500.00"))
    }

    @Test
    fun hsnAutoFillHelper_suggestsCorrectHsnAndBuildsBatchPlan() {
        val phone = com.example.billing.HsnAutoFillHelper.suggestHsn("Samsung Galaxy A55 5G")
        assertEquals("8517", phone?.hsnCode)
        assertEquals(18.0, phone?.gstRate ?: 0.0, 0.001)

        // Accessory must NOT misclassify as phone
        val cover = com.example.billing.HsnAutoFillHelper.suggestHsn("Vivo V30 Back Cover")
        assertEquals("3926", cover?.hsnCode)

        val tempered = com.example.billing.HsnAutoFillHelper.suggestHsn("Redmi Note 13 Tempered Glass")
        assertEquals("7007", tempered?.hsnCode)

        val rice = com.example.billing.HsnAutoFillHelper.suggestHsn("Basmati Rice 25kg")
        assertEquals("1006", rice?.hsnCode)
        assertEquals(5.0, rice?.gstRate ?: 0.0, 0.001)

        // Whole-word guard: "Black Bag" should match Bag (4202), not AC (8415); "Leather Sandal" matches Footwear (6403), not Dal
        assertEquals("4202", com.example.billing.HsnAutoFillHelper.suggestHsn("Black Laptop Bag")?.hsnCode)
        assertEquals("6403", com.example.billing.HsnAutoFillHelper.suggestHsn("Leather Sandal")?.hsnCode)

        val products = listOf(
            com.example.data.ProductItem(id = "p1", name = "Vivo Y200 5G", hsnCode = ""),
            com.example.data.ProductItem(id = "p2", name = "Aashirvaad Atta 10kg", hsnCode = "1101"),
            com.example.data.ProductItem(id = "p3", name = "Custom Gift Item", hsnCode = "")
        )
        val missingOnlyPlan = com.example.billing.HsnAutoFillHelper.buildBatchPlan(
            products = products,
            overwriteExisting = false,
            alsoUpdateGstRate = false,
            applyDefaultIfUnmatched = false
        )
        assertEquals(2, missingOnlyPlan.size)
        assertEquals("8517", missingOnlyPlan.first { it.product.id == "p1" }.suggestedHsn)
        assertEquals("", missingOnlyPlan.first { it.product.id == "p3" }.suggestedHsn)

        val withDefaultPlan = com.example.billing.HsnAutoFillHelper.buildBatchPlan(
            products = products,
            overwriteExisting = true,
            alsoUpdateGstRate = true,
            applyDefaultIfUnmatched = true
        )
        assertEquals(3, withDefaultPlan.size)
        assertEquals("9997", withDefaultPlan.first { it.product.id == "p3" }.suggestedHsn)
    }
}



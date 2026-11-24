package com.example.billing

import com.example.data.InvoiceLineItem
import java.util.Locale
import kotlin.math.roundToLong

data class GstCalculationResult(
    val inputAmount: Double,
    val gstPercent: Double,
    val isInclusive: Boolean,
    val isInterState: Boolean,
    val baseAmount: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val igstAmount: Double,
    val totalGstAmount: Double,
    val grandTotal: Double
)

data class ComputedLine(
    val lineItem: InvoiceLineItem,
    val lineProfit: Double
)

data class InvoiceTotals(
    val subtotalTaxable: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val igstAmount: Double,
    val totalGstAmount: Double,
    val grandTotal: Double,
    val estimatedProfit: Double
)

object GstCalculator {

    fun round2(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return (value * 100.0).roundToLong() / 100.0
    }

    fun calculateSingleAmount(
        amount: Double,
        gstPercent: Double,
        isInclusive: Boolean,
        isInterState: Boolean
    ): GstCalculationResult {
        val safeAmount = amount.coerceAtLeast(0.0)
        val safeRate = gstPercent.coerceIn(0.0, 100.0)

        val base: Double
        val totalGst: Double
        val total: Double

        if (isInclusive) {
            base = if (safeRate == 0.0) safeAmount else safeAmount * 100.0 / (100.0 + safeRate)
            total = safeAmount
            totalGst = (total - base).coerceAtLeast(0.0)
        } else {
            base = safeAmount
            totalGst = base * safeRate / 100.0
            total = base + totalGst
        }

        val roundedBase = round2(base)
        val roundedTotalGst = round2(totalGst)
        val roundedTotal = round2(total)

        val cgst: Double
        val sgst: Double
        val igst: Double
        if (isInterState) {
            cgst = 0.0
            sgst = 0.0
            igst = roundedTotalGst
        } else {
            val half = round2(roundedTotalGst / 2.0)
            cgst = half
            sgst = round2(roundedTotalGst - half)
            igst = 0.0
        }

        return GstCalculationResult(
            inputAmount = round2(safeAmount),
            gstPercent = safeRate,
            isInclusive = isInclusive,
            isInterState = isInterState,
            baseAmount = roundedBase,
            cgstAmount = cgst,
            sgstAmount = sgst,
            igstAmount = igst,
            totalGstAmount = roundedTotalGst,
            grandTotal = roundedTotal
        )
    }

    /**
     * Computes a customer-facing InvoiceLineItem using ONLY sellingRate (Bikri Rate),
     * while tracking private lineProfit separately from purchaseRate (Khareed Rate).
     */
    fun computeLine(
        productId: String,
        name: String,
        hsnCode: String,
        unit: String,
        quantity: Double,
        sellingRate: Double,
        purchaseRate: Double,
        gstRate: Double,
        taxInclusive: Boolean,
        imeiNumber: String = ""
    ): ComputedLine {
        val qty = quantity.coerceAtLeast(0.001)
        val sell = sellingRate.coerceAtLeast(0.0)
        val buy = purchaseRate.coerceAtLeast(0.0)
        val gross = qty * sell
        val calc = calculateSingleAmount(
            amount = gross,
            gstPercent = gstRate,
            isInclusive = taxInclusive,
            isInterState = false
        )
        val item = InvoiceLineItem(
            productId = productId,
            name = name.trim(),
            hsnCode = hsnCode.trim().uppercase(),
            unit = unit.trim().ifEmpty { "PCS" }.uppercase(),
            quantity = qty,
            sellingRate = round2(sell),
            gstRate = gstRate.coerceIn(0.0, 100.0),
            taxInclusive = taxInclusive,
            taxableAmount = calc.baseAmount,
            gstAmount = calc.totalGstAmount,
            lineTotal = calc.grandTotal,
            imeiNumber = imeiNumber.trim().take(80)
        )
        // Profit is computed on pre-tax selling value vs purchase cost
        val costBase = if (taxInclusive && gstRate > 0.0) {
            (qty * buy) * 100.0 / (100.0 + gstRate)
        } else {
            qty * buy
        }
        val profit = round2(calc.baseAmount - costBase)
        return ComputedLine(lineItem = item, lineProfit = profit)
    }

    fun summarizeInvoice(lines: List<ComputedLine>, isInterState: Boolean): InvoiceTotals {
        val subtotal = round2(lines.sumOf { it.lineItem.taxableAmount })
        val totalGst = round2(lines.sumOf { it.lineItem.gstAmount })
        val grandTotal = round2(lines.sumOf { it.lineItem.lineTotal })
        val profit = round2(lines.sumOf { it.lineProfit })

        val cgst: Double
        val sgst: Double
        val igst: Double
        if (isInterState) {
            cgst = 0.0
            sgst = 0.0
            igst = totalGst
        } else {
            val half = round2(totalGst / 2.0)
            cgst = half
            sgst = round2(totalGst - half)
            igst = 0.0
        }

        return InvoiceTotals(
            subtotalTaxable = subtotal,
            cgstAmount = cgst,
            sgstAmount = sgst,
            igstAmount = igst,
            totalGstAmount = totalGst,
            grandTotal = grandTotal,
            estimatedProfit = profit
        )
    }

    fun formatRupees(amount: Double): String {
        return "₹" + String.format(Locale.US, "%,.2f", round2(amount))
    }

    fun formatPlainNumber(amount: Double): String {
        val rounded = round2(amount)
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded)
        }
    }

    fun amountToIndianWords(amount: Double): String {
        val totalPaise = (amount.coerceAtLeast(0.0) * 100.0).roundToLong()
        val rupees = totalPaise / 100L
        val paise = (totalPaise % 100L).toInt()
        val rupeeWords = if (rupees == 0L) "Zero" else convertNumberIndian(rupees)
        return if (paise > 0) {
            "Rupees $rupeeWords and ${convertNumberIndian(paise.toLong())} Paise Only"
        } else {
            "Rupees $rupeeWords Only"
        }
    }

    private val ones = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen"
    )
    private val tens = arrayOf(
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    )

    private fun twoDigits(n: Int): String {
        return when {
            n == 0 -> ""
            n < 20 -> ones[n]
            else -> tens[n / 10] + if (n % 10 != 0) " ${ones[n % 10]}" else ""
        }
    }

    private fun convertNumberIndian(number: Long): String {
        if (number == 0L) return "Zero"
        val parts = mutableListOf<String>()
        var n = number

        val crores = (n / 10_000_000L).toInt()
        n %= 10_000_000L
        if (crores > 0) parts.add("${convertNumberIndian(crores.toLong())} Crore")

        val lakhs = (n / 100_000L).toInt()
        n %= 100_000L
        if (lakhs > 0) parts.add("${twoDigits(lakhs)} Lakh")

        val thousands = (n / 1_000L).toInt()
        n %= 1_000L
        if (thousands > 0) parts.add("${twoDigits(thousands)} Thousand")

        val hundreds = (n / 100L).toInt()
        val remainder = (n % 100L).toInt()
        if (hundreds > 0) parts.add("${ones[hundreds]} Hundred")
        if (remainder > 0) parts.add(twoDigits(remainder))

        return parts.joinToString(" ")
    }
}

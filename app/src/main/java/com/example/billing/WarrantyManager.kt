package com.example.billing

import com.example.data.Invoice
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class WarrantyStatus {
    NONE,
    ACTIVE,
    EXPIRING_SOON,
    EXPIRED
}

data class WarrantyEvaluation(
    val status: WarrantyStatus,
    val daysRemaining: Int,
    val expiryDateMillis: Long,
    val displayLabel: String,
    val badgeText: String
)

object WarrantyManager {

    const val EXPIRY_ALERT_WINDOW_DAYS = 7

    private const val MILLIS_PER_DAY = 86_400_000.0

    fun endOfDayMillis(dateMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun startOfDayMillis(dateMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun computeWarrantyEndMillis(startMillis: Long, months: Int, days: Int): Long {
        if (months <= 0 && days <= 0) return 0L
        val cal = Calendar.getInstance().apply {
            timeInMillis = startMillis
            if (months > 0) add(Calendar.MONTH, months)
            if (days > 0) add(Calendar.DAY_OF_YEAR, days)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun formatWarrantyLabel(months: Int, days: Int, customNote: String = ""): String {
        val duration = when {
            months > 0 && months % 12 == 0 && days == 0 -> {
                val years = months / 12
                "$years ${if (years == 1) "Year" else "Years"}"
            }
            months > 0 && days == 0 -> "$months ${if (months == 1) "Month" else "Months"}"
            months == 0 && days > 0 -> "$days ${if (days == 1) "Day" else "Days"}"
            months > 0 && days > 0 -> "${months}M ${days}D"
            else -> "Custom"
        }
        val cleanNote = customNote.trim()
        return if (cleanNote.isNotEmpty()) {
            "$duration Warranty ($cleanNote)".take(100)
        } else {
            "$duration Warranty".take(100)
        }
    }

    fun formatExpiryDate(expiryMillis: Long): String {
        if (expiryMillis <= 0L) return ""
        return SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(expiryMillis))
    }

    fun evaluate(invoice: Invoice, nowMillis: Long = System.currentTimeMillis()): WarrantyEvaluation {
        if (!invoice.hasWarranty || invoice.warrantyEndMillis <= 0L) {
            return WarrantyEvaluation(
                status = WarrantyStatus.NONE,
                daysRemaining = 0,
                expiryDateMillis = 0L,
                displayLabel = "",
                badgeText = ""
            )
        }
        val label = invoice.warrantyLabel.ifBlank {
            formatWarrantyLabel(invoice.warrantyMonths, invoice.warrantyDays)
        }
        val expiryMillis = invoice.warrantyEndMillis
        if (nowMillis > expiryMillis) {
            return WarrantyEvaluation(
                status = WarrantyStatus.EXPIRED,
                daysRemaining = -1,
                expiryDateMillis = expiryMillis,
                displayLabel = label,
                badgeText = "Expired"
            )
        }

        val todayStart = startOfDayMillis(nowMillis)
        val expiryStart = startOfDayMillis(expiryMillis)
        val daysLeft = ((expiryStart - todayStart) / MILLIS_PER_DAY).roundToInt().coerceAtLeast(0)

        val dateStr = formatExpiryDate(expiryMillis)
        return if (daysLeft <= EXPIRY_ALERT_WINDOW_DAYS) {
            val urgency = when (daysLeft) {
                0 -> "Warranty expires today ($dateStr)"
                1 -> "Warranty expires tomorrow ($dateStr)"
                else -> "Warranty expires in $daysLeft days ($dateStr)"
            }
            WarrantyEvaluation(
                status = WarrantyStatus.EXPIRING_SOON,
                daysRemaining = daysLeft,
                expiryDateMillis = expiryMillis,
                displayLabel = label,
                badgeText = urgency
            )
        } else {
            WarrantyEvaluation(
                status = WarrantyStatus.ACTIVE,
                daysRemaining = daysLeft,
                expiryDateMillis = expiryMillis,
                displayLabel = label,
                badgeText = "$label • Valid till $dateStr"
            )
        }
    }

    fun isWarrantyActiveOnBill(invoice: Invoice, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val status = evaluate(invoice, nowMillis).status
        return status == WarrantyStatus.ACTIVE || status == WarrantyStatus.EXPIRING_SOON
    }

    fun getExpiringSoonInvoices(
        invoices: List<Invoice>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<Pair<Invoice, WarrantyEvaluation>> {
        return invoices.mapNotNull { inv ->
            val eval = evaluate(inv, nowMillis)
            if (eval.status == WarrantyStatus.EXPIRING_SOON) inv to eval else null
        }.sortedBy { it.second.expiryDateMillis }
    }

    fun getExpiredTaggedInvoices(
        invoices: List<Invoice>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<Invoice> {
        return invoices.filter { inv ->
            inv.hasWarranty && evaluate(inv, nowMillis).status == WarrantyStatus.EXPIRED
        }
    }
}

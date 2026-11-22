package com.example.billing

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.Invoice
import com.example.data.PaymentMode
import com.example.data.ShopProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoicePdfGenerator {

    private const val PAGE_WIDTH = 595   // A4 points
    private const val PAGE_HEIGHT = 842  // A4 points
    private const val MARGIN = 32f

    suspend fun generateInvoicePdf(
        context: Context,
        invoice: Invoice,
        shop: ShopProfile?
    ): Result<Pair<File, Uri>> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
            val safeName = invoice.invoiceNumber.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                .ifBlank { "GST_Bill_${System.currentTimeMillis()}" }
            val outFile = File(dir, "$safeName.pdf")

            val document = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            val tealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(11, 79, 108) }
            val lightBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(241, 245, 249) }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(203, 213, 225)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val headerTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val whiteSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(224, 242, 254)
                textSize = 9.5f
            }
            val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(254, 243, 199)
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(51, 65, 85)
                textSize = 9.5f
            }
            val rightBoldPaint = TextPaint(boldPaint).apply { textAlign = Paint.Align.RIGHT }
            val rightBodyPaint = TextPaint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
            val mutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 8.5f
            }

            // 1. Header Band
            val headerHeight = 96f
            canvas.drawRoundRect(
                RectF(MARGIN, MARGIN, PAGE_WIDTH - MARGIN, MARGIN + headerHeight),
                8f,
                8f,
                tealPaint
            )
            canvas.drawText(
                invoice.shopName.ifBlank { "Shop Invoice" },
                MARGIN + 14f,
                MARGIN + 26f,
                headerTitlePaint
            )
            val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
                .format(Date(invoice.issuedAtMillis))
            canvas.drawText("TAX INVOICE", PAGE_WIDTH - MARGIN - 14f, MARGIN + 24f, badgePaint)
            canvas.drawText(
                "Bill No: ${invoice.invoiceNumber}",
                PAGE_WIDTH - MARGIN - 14f,
                MARGIN + 42f,
                TextPaint(whiteSubPaint).apply { textAlign = Paint.Align.RIGHT }
            )
            canvas.drawText(
                dateStr,
                PAGE_WIDTH - MARGIN - 14f,
                MARGIN + 56f,
                TextPaint(whiteSubPaint).apply { textAlign = Paint.Align.RIGHT }
            )

            var hy = MARGIN + 42f
            if (invoice.shopGstin.isNotBlank()) {
                canvas.drawText("GSTIN: ${invoice.shopGstin}", MARGIN + 14f, hy, whiteSubPaint)
                hy += 13f
            }
            if (invoice.shopPhone.isNotBlank()) {
                canvas.drawText("Mob: ${invoice.shopPhone}", MARGIN + 14f, hy, whiteSubPaint)
                hy += 13f
            }
            if (invoice.shopAddress.isNotBlank()) {
                val maxShopW = 290
                val shopAddrLayout = StaticLayout.Builder.obtain(
                    invoice.shopAddress, 0, invoice.shopAddress.length, whiteSubPaint, maxShopW
                ).setMaxLines(2).build()
                canvas.save()
                canvas.translate(MARGIN + 14f, hy - 9f)
                shopAddrLayout.draw(canvas)
                canvas.restore()
            }

            var y = MARGIN + headerHeight + 14f

            // 2. Customer ("Bill To") Box
            val custBoxTop = y
            val custBoxHeight = 66f
            canvas.drawRoundRect(
                RectF(MARGIN, custBoxTop, PAGE_WIDTH - MARGIN, custBoxTop + custBoxHeight),
                6f,
                6f,
                lightBgPaint
            )
            canvas.drawRoundRect(
                RectF(MARGIN, custBoxTop, PAGE_WIDTH - MARGIN, custBoxTop + custBoxHeight),
                6f,
                6f,
                borderPaint
            )
            canvas.drawText("BILL TO (CUSTOMER)", MARGIN + 10f, custBoxTop + 15f, mutedPaint)
            canvas.drawText(invoice.customerName, MARGIN + 10f, custBoxTop + 30f, boldPaint)
            val phoneText = if (invoice.customerPhone.isNotBlank()) "Mobile: ${invoice.customerPhone}" else "Mobile: -"
            canvas.drawText(phoneText, PAGE_WIDTH - MARGIN - 10f, custBoxTop + 30f, rightBodyPaint)
            if (invoice.customerGstin.isNotBlank()) {
                canvas.drawText(
                    "GSTIN: ${invoice.customerGstin}",
                    PAGE_WIDTH - MARGIN - 10f,
                    custBoxTop + 44f,
                    rightBodyPaint
                )
            }
            val addrDisplay = invoice.customerAddress.ifBlank { "Address not provided" }
            val custAddrLayout = StaticLayout.Builder.obtain(
                addrDisplay, 0, addrDisplay.length, bodyPaint, 340
            ).setMaxLines(2).build()
            canvas.save()
            canvas.translate(MARGIN + 10f, custBoxTop + 36f)
            custAddrLayout.draw(canvas)
            canvas.restore()

            y = custBoxTop + custBoxHeight + 14f

            // 3. Table Header (NOTE: Displays ONLY Bikri/Selling Rate — NEVER Khareed Rate!)
            fun drawTableHeader(c: Canvas, topY: Float): Float {
                val h = 24f
                c.drawRect(MARGIN, topY, PAGE_WIDTH - MARGIN, topY + h, tealPaint)
                val hdr = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 9f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val hdrRight = TextPaint(hdr).apply { textAlign = Paint.Align.RIGHT }
                c.drawText("#", MARGIN + 6f, topY + 15f, hdr)
                c.drawText("Item / HSN", MARGIN + 26f, topY + 15f, hdr)
                c.drawText("Qty", MARGIN + 262f, topY + 15f, hdrRight)
                c.drawText("Rate", MARGIN + 332f, topY + 15f, hdrRight)
                c.drawText("Taxable", MARGIN + 402f, topY + 15f, hdrRight)
                c.drawText("GST", MARGIN + 455f, topY + 15f, hdrRight)
                c.drawText("Total", PAGE_WIDTH - MARGIN - 6f, topY + 15f, hdrRight)
                return topY + h
            }

            y = drawTableHeader(canvas, y)

            // 4. Table Rows
            invoice.items.forEachIndexed { idx, item ->
                val itemTitle = if (item.hsnCode.isNotBlank()) "${item.name} (HSN: ${item.hsnCode})" else item.name
                val nameLayout = StaticLayout.Builder.obtain(
                    itemTitle, 0, itemTitle.length, bodyPaint, 195
                ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
                val rowHeight = (nameLayout.height + 10f).coerceAtLeast(24f)

                if (y + rowHeight > PAGE_HEIGHT - 190f) {
                    document.finishPage(page)
                    pageNumber += 1
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = drawTableHeader(canvas, MARGIN)
                }

                if (idx % 2 == 1) {
                    canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowHeight, lightBgPaint)
                }
                canvas.drawLine(MARGIN, y + rowHeight, PAGE_WIDTH - MARGIN, y + rowHeight, borderPaint)

                canvas.drawText("${idx + 1}", MARGIN + 6f, y + 14f, bodyPaint)
                canvas.save()
                canvas.translate(MARGIN + 26f, y + 4f)
                nameLayout.draw(canvas)
                canvas.restore()

                val qtyLabel = "${GstCalculator.formatPlainNumber(item.quantity)} ${item.unit}"
                canvas.drawText(qtyLabel, MARGIN + 262f, y + 14f, rightBodyPaint)
                // Strictly sellingRate (Bikri Rate)
                canvas.drawText(
                    String.format(Locale.US, "%.2f", item.sellingRate),
                    MARGIN + 332f,
                    y + 14f,
                    rightBodyPaint
                )
                canvas.drawText(
                    String.format(Locale.US, "%.2f", item.taxableAmount),
                    MARGIN + 402f,
                    y + 14f,
                    rightBodyPaint
                )
                canvas.drawText(
                    "${GstCalculator.formatPlainNumber(item.gstRate)}%",
                    MARGIN + 455f,
                    y + 14f,
                    rightBodyPaint
                )
                canvas.drawText(
                    String.format(Locale.US, "%.2f", item.lineTotal),
                    PAGE_WIDTH - MARGIN - 6f,
                    y + 14f,
                    rightBoldPaint
                )

                y += rowHeight
            }

            if (y > PAGE_HEIGHT - 220f) {
                document.finishPage(page)
                pageNumber += 1
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN
            } else {
                y += 14f
            }

            // 5. Totals Box (Right) + Amount in Words & Payment (Left)
            val summaryTop = y
            val leftWidth = 285
            val wordsText = "Amount in Words:\n${GstCalculator.amountToIndianWords(invoice.grandTotal)}"
            val wordsLayout = StaticLayout.Builder.obtain(
                wordsText, 0, wordsText.length, bodyPaint, leftWidth
            ).build()
            canvas.save()
            canvas.translate(MARGIN, summaryTop + 4f)
            wordsLayout.draw(canvas)
            canvas.restore()

            val payLabel = "Payment Mode: ${PaymentMode.fromRaw(invoice.paymentMode).label}"
            canvas.drawText(payLabel, MARGIN, summaryTop + wordsLayout.height + 20f, boldPaint)
            var leftExtraY = summaryTop + wordsLayout.height + 28f
            if (invoice.notes.isNotBlank()) {
                val notesLayout = StaticLayout.Builder.obtain(
                    "Note: ${invoice.notes}", 0, "Note: ${invoice.notes}".length, mutedPaint, leftWidth
                ).setMaxLines(2).build()
                canvas.save()
                canvas.translate(MARGIN, leftExtraY)
                notesLayout.draw(canvas)
                canvas.restore()
                leftExtraY += notesLayout.height + 4f
            }
            if (WarrantyManager.isWarrantyActiveOnBill(invoice)) {
                val wEval = WarrantyManager.evaluate(invoice)
                val wText = "Warranty: ${wEval.displayLabel} (Valid till ${WarrantyManager.formatExpiryDate(wEval.expiryDateMillis)})"
                val wLayout = StaticLayout.Builder.obtain(
                    wText, 0, wText.length, boldPaint, leftWidth
                ).setMaxLines(2).build()
                canvas.save()
                canvas.translate(MARGIN, leftExtraY + 2f)
                wLayout.draw(canvas)
                canvas.restore()
            }

            val boxLeft = PAGE_WIDTH - MARGIN - 220f
            val boxRight = PAGE_WIDTH - MARGIN
            var ty = summaryTop + 14f
            fun drawSummaryRow(label: String, value: String, bold: Boolean = false) {
                val lp = if (bold) boldPaint else bodyPaint
                val rp = if (bold) rightBoldPaint else rightBodyPaint
                canvas.drawText(label, boxLeft + 10f, ty, lp)
                canvas.drawText(value, boxRight - 10f, ty, rp)
                ty += 16f
            }
            drawSummaryRow("Taxable Subtotal", GstCalculator.formatRupees(invoice.subtotalTaxable))
            if (invoice.interState) {
                drawSummaryRow("IGST", GstCalculator.formatRupees(invoice.igstAmount))
            } else {
                drawSummaryRow("CGST", GstCalculator.formatRupees(invoice.cgstAmount))
                drawSummaryRow("SGST", GstCalculator.formatRupees(invoice.sgstAmount))
            }
            canvas.drawLine(boxLeft + 8f, ty - 8f, boxRight - 8f, ty - 8f, borderPaint)
            ty += 4f
            val grandPaint = TextPaint(boldPaint).apply {
                textSize = 12f
                color = Color.rgb(11, 79, 108)
            }
            val grandRightPaint = TextPaint(grandPaint).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText("GRAND TOTAL", boxLeft + 10f, ty, grandPaint)
            canvas.drawText(GstCalculator.formatRupees(invoice.grandTotal), boxRight - 10f, ty, grandRightPaint)
            val boxBottom = ty + 12f
            canvas.drawRoundRect(RectF(boxLeft, summaryTop, boxRight, boxBottom), 6f, 6f, borderPaint)

            // 6. Stamp (Mohar) & Signature Footer
            val footerTop = maxOf(boxBottom + 24f, PAGE_HEIGHT - 145f)
            val termsText = shop?.termsAndConditions?.takeIf { it.isNotBlank() }
                ?: "Thank you for your business!"
            val termsLayout = StaticLayout.Builder.obtain(
                "Terms & Conditions:\n$termsText", 0, "Terms & Conditions:\n$termsText".length, mutedPaint, 250
            ).setMaxLines(4).build()
            canvas.save()
            canvas.translate(MARGIN, footerTop + 12f)
            termsLayout.draw(canvas)
            canvas.restore()

            val stampBitmap: Bitmap? = if (invoice.includeStamp) {
                shop?.stampBase64?.let { ImageStampHelper.base64ToBitmap(it) }
            } else null
            val signBitmap: Bitmap? = if (invoice.includeSignature) {
                shop?.signatureBase64?.let { ImageStampHelper.base64ToBitmap(it) }
            } else null

            val authBoxRight = PAGE_WIDTH - MARGIN
            val signAreaLeft = authBoxRight - 140f
            val stampAreaLeft = signAreaLeft - 110f

            if (stampBitmap != null) {
                val dest = fitInsideRect(stampBitmap, RectF(stampAreaLeft, footerTop, stampAreaLeft + 92f, footerTop + 72f))
                canvas.drawBitmap(stampBitmap, Rect(0, 0, stampBitmap.width, stampBitmap.height), dest, null)
                canvas.drawText("Shop Mohar / Stamp", stampAreaLeft + 46f, footerTop + 86f, TextPaint(mutedPaint).apply {
                    textAlign = Paint.Align.CENTER
                })
            }

            if (signBitmap != null) {
                val dest = fitInsideRect(signBitmap, RectF(signAreaLeft + 10f, footerTop + 4f, authBoxRight - 10f, footerTop + 62f))
                canvas.drawBitmap(signBitmap, Rect(0, 0, signBitmap.width, signBitmap.height), dest, null)
            }
            if (invoice.includeSignature || invoice.includeStamp) {
                canvas.drawLine(signAreaLeft + 6f, footerTop + 70f, authBoxRight - 6f, footerTop + 70f, borderPaint)
                canvas.drawText(
                    "For ${invoice.shopName}",
                    (signAreaLeft + authBoxRight) / 2f,
                    footerTop + 83f,
                    TextPaint(boldPaint).apply {
                        textSize = 8.5f
                        textAlign = Paint.Align.CENTER
                    }
                )
                canvas.drawText(
                    "Authorized Signatory",
                    (signAreaLeft + authBoxRight) / 2f,
                    footerTop + 95f,
                    TextPaint(mutedPaint).apply { textAlign = Paint.Align.CENTER }
                )
            }

            document.finishPage(page)
            FileOutputStream(outFile).use { fos ->
                document.writeTo(fos)
            }
            document.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            Result.success(outFile to uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fitInsideRect(bitmap: Bitmap, bounds: RectF): RectF {
        val bw = bitmap.width.coerceAtLeast(1).toFloat()
        val bh = bitmap.height.coerceAtLeast(1).toFloat()
        val scale = minOf(bounds.width() / bw, bounds.height() / bh)
        val w = bw * scale
        val h = bh * scale
        val left = bounds.left + (bounds.width() - w) / 2f
        val top = bounds.top + (bounds.height() - h) / 2f
        return RectF(left, top, left + w, top + h)
    }

    fun sharePdfToWhatsApp(context: Context, pdfUri: Uri, invoice: Invoice) {
        val summary = buildString {
            append("*${invoice.shopName}*\n")
            append("GST Bill No: ${invoice.invoiceNumber}\n")
            append("Customer: ${invoice.customerName}\n")
            append("Grand Total: ${GstCalculator.formatRupees(invoice.grandTotal)}\n")
            if (WarrantyManager.isWarrantyActiveOnBill(invoice)) {
                val wEval = WarrantyManager.evaluate(invoice)
                append("Warranty: ${wEval.displayLabel} (Till ${WarrantyManager.formatExpiryDate(wEval.expiryDateMillis)})\n")
            }
            append("Thank you!")
        }
        val baseIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            putExtra(Intent.EXTRA_TEXT, summary)
            putExtra(Intent.EXTRA_SUBJECT, "GST Invoice ${invoice.invoiceNumber}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val pm = context.packageManager
        val whatsappPkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { pkg ->
            try {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                true
            } catch (_: Exception) {
                false
            }
        }

        if (whatsappPkg != null) {
            val targeted = Intent(baseIntent).apply { setPackage(whatsappPkg) }
            try {
                context.startActivity(targeted)
                return
            } catch (_: Exception) {
                // Fall back to chooser below
            }
        }
        context.startActivity(Intent.createChooser(baseIntent, "Share GST Bill PDF"))
    }

    fun openPdfExternally(context: Context, pdfUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(pdfUri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open GST Invoice PDF"))
    }
}

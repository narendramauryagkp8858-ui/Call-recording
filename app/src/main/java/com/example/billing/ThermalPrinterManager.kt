package com.example.billing

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.Invoice
import com.example.data.PaymentMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class PrinterDeviceInfo(
    val name: String,
    val address: String
)

object ThermalPrinterManager {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val CMD_INIT = byteArrayOf(0x1B, 0x40)
    private val CMD_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val CMD_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val CMD_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val CMD_DOUBLE_SIZE_ON = byteArrayOf(0x1D, 0x21, 0x11)
    private val CMD_NORMAL_SIZE = byteArrayOf(0x1D, 0x21, 0x00)
    private val CMD_FEED_AND_CUT = byteArrayOf(0x1B, 0x64, 0x04, 0x1D, 0x56, 0x42, 0x00)

    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isBluetoothAvailable(context: Context): Boolean {
        val adapter = getAdapter(context)
        return adapter != null && adapter.isEnabled
    }

    private fun getAdapter(context: Context): BluetoothAdapter? {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter
    }

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(context: Context): List<PrinterDeviceInfo> {
        if (!hasBluetoothPermission(context)) return emptyList()
        val adapter = getAdapter(context) ?: return emptyList()
        val bonded: Set<BluetoothDevice> = adapter.bondedDevices ?: return emptyList()
        return bonded.map { device ->
            PrinterDeviceInfo(
                name = device.name?.takeIf { it.isNotBlank() } ?: "Bluetooth Printer",
                address = device.address
            )
        }.sortedBy { it.name.lowercase() }
    }

    /**
     * Builds ESC/POS commands for 58mm (32 columns) or 80mm (48 columns) mini thermal printers.
     * Displays ONLY Bikri/Selling Rate — NEVER Purchase/Khareed Rate.
     */
    fun buildEscPosReceipt(
        invoice: Invoice,
        paperWidthMm: Int = 58,
        nowMillis: Long = System.currentTimeMillis()
    ): ByteArray {
        val cols = if (paperWidthMm >= 80) 48 else 32
        val divider = "-".repeat(cols)
        val out = ByteArrayOutputStream()
        val ascii = Charset.forName("US-ASCII")

        fun writeText(line: String) {
            out.write(line.toByteArray(ascii))
            out.write('\n'.code)
        }

        fun padRow(left: String, right: String): String {
            val cleanLeft = left.take(cols - right.length - 1).trimEnd()
            val spaces = (cols - cleanLeft.length - right.length).coerceAtLeast(1)
            return cleanLeft + " ".repeat(spaces) + right
        }

        out.write(CMD_INIT)
        out.write(CMD_ALIGN_CENTER)
        out.write(CMD_BOLD_ON)
        out.write(CMD_DOUBLE_SIZE_ON)
        writeText(invoice.shopName.take(cols / 2))
        out.write(CMD_NORMAL_SIZE)
        out.write(CMD_BOLD_OFF)

        if (invoice.shopAddress.isNotBlank()) {
            invoice.shopAddress.chunked(cols).forEach { writeText(it.trim()) }
        }
        if (invoice.shopPhone.isNotBlank()) {
            writeText("Mob: ${invoice.shopPhone}")
        }
        if (invoice.shopGstin.isNotBlank()) {
            writeText("GSTIN: ${invoice.shopGstin}")
        }
        writeText(divider)
        out.write(CMD_BOLD_ON)
        writeText("TAX INVOICE")
        out.write(CMD_BOLD_OFF)
        out.write(CMD_ALIGN_LEFT)

        val dateStr = SimpleDateFormat("dd/MM/yy hh:mm a", Locale.ENGLISH)
            .format(Date(invoice.issuedAtMillis))
        writeText("Bill: ${invoice.invoiceNumber}")
        writeText("Date: $dateStr")
        writeText("Cust: ${invoice.customerName.take(cols - 6)}")
        if (invoice.customerPhone.isNotBlank()) {
            writeText("Mob : ${invoice.customerPhone}")
        }
        if (invoice.customerAddress.isNotBlank()) {
            writeText("Addr: ${invoice.customerAddress.take(cols - 6)}")
        }
        if (invoice.customerGstin.isNotBlank()) {
            writeText("GST : ${invoice.customerGstin}")
        }
        writeText(divider)
        out.write(CMD_BOLD_ON)
        writeText(padRow("Item (Qty x Rate)", "Amt"))
        out.write(CMD_BOLD_OFF)
        writeText(divider)

        invoice.items.forEach { item ->
            writeText(item.name.take(cols))
            val rateInfo = "${GstCalculator.formatPlainNumber(item.quantity)} ${item.unit} x ${
                String.format(Locale.US, "%.2f", item.sellingRate)
            } (${GstCalculator.formatPlainNumber(item.gstRate)}%)"
            val amtStr = String.format(Locale.US, "%.2f", item.lineTotal)
            writeText(padRow(rateInfo, amtStr))
        }

        writeText(divider)
        writeText(padRow("Taxable", "Rs.${String.format(Locale.US, "%.2f", invoice.subtotalTaxable)}"))
        if (invoice.interState) {
            writeText(padRow("IGST", "Rs.${String.format(Locale.US, "%.2f", invoice.igstAmount)}"))
        } else {
            writeText(padRow("CGST", "Rs.${String.format(Locale.US, "%.2f", invoice.cgstAmount)}"))
            writeText(padRow("SGST", "Rs.${String.format(Locale.US, "%.2f", invoice.sgstAmount)}"))
        }
        writeText(divider)
        out.write(CMD_BOLD_ON)
        writeText(padRow("GRAND TOTAL", "Rs.${String.format(Locale.US, "%.2f", invoice.grandTotal)}"))
        out.write(CMD_BOLD_OFF)
        writeText(padRow("Paid Via", PaymentMode.fromRaw(invoice.paymentMode).label))
        if (WarrantyManager.isWarrantyActiveOnBill(invoice, nowMillis)) {
            val wEval = WarrantyManager.evaluate(invoice, nowMillis)
            writeText(divider)
            writeText("Warranty: ${wEval.displayLabel}".take(cols))
            writeText("Valid Till: ${WarrantyManager.formatExpiryDate(wEval.expiryDateMillis)}".take(cols))
        }
        writeText(divider)
        out.write(CMD_ALIGN_CENTER)
        writeText("Thank You! Visit Again")
        out.write(CMD_FEED_AND_CUT)
        return out.toByteArray()
    }

    fun buildTestReceipt(shopName: String, paperWidthMm: Int = 58): ByteArray {
        val cols = if (paperWidthMm >= 80) 48 else 32
        val divider = "-".repeat(cols)
        val out = ByteArrayOutputStream()
        val ascii = Charset.forName("US-ASCII")
        fun writeText(line: String) {
            out.write(line.toByteArray(ascii))
            out.write('\n'.code)
        }
        out.write(CMD_INIT)
        out.write(CMD_ALIGN_CENTER)
        out.write(CMD_BOLD_ON)
        writeText(shopName.ifBlank { "NARENDRA BILLING" }.take(cols))
        out.write(CMD_BOLD_OFF)
        writeText(divider)
        writeText("Bluetooth Thermal Printer OK")
        writeText("Paper Width: ${paperWidthMm}mm")
        writeText(divider)
        writeText("Ready to Print GST Bills")
        out.write(CMD_FEED_AND_CUT)
        return out.toByteArray()
    }

    @SuppressLint("MissingPermission")
    suspend fun sendBytesToPrinter(
        context: Context,
        deviceAddress: String,
        bytes: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermission(context)) {
            return@withContext Result.failure(SecurityException("Bluetooth permission not granted."))
        }
        val adapter = getAdapter(context)
            ?: return@withContext Result.failure(IllegalStateException("Bluetooth is not supported on this device."))
        if (!adapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Please turn ON Bluetooth first."))
        }
        var socket: android.bluetooth.BluetoothSocket? = null
        try {
            val device = adapter.getRemoteDevice(deviceAddress)
            try {
                adapter.cancelDiscovery()
            } catch (_: SecurityException) {
                // Ignore if BLUETOOTH_SCAN not requested for discovery cancel
            }
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            socket.outputStream.use { stream ->
                stream.write(bytes)
                stream.flush()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Printer connection failed: ${e.localizedMessage ?: "Check printer power"}"))
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }
}

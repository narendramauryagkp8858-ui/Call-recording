# Narendra Billing

**Narendra Billing** is a native Android GST billing and shop invoicing application built with **Kotlin**, **Jetpack Compose**, **Material 3**, and **Firebase Firestore**.

## Key Features
- **Multi-Shop / Dukaan Profiles**: Any user can sign in with Google and create separate Shop profiles with Shop Name, GSTIN, Address, Mobile, Invoice Prefix, and Terms.
- **Shop Mohar (Stamp) & Signature**: Upload your shop stamp/Mohar and signature photos via Android Photo Picker (`PickVisualMedia`) or draw your signature directly in-app. Choose **Mohar**, **Signature**, or **Both** per GST bill.
- **Khareed & Bikri Rate Privacy**: Store both private **Khareed Rate (Purchase)** and **Bikri Rate (Selling)** in your inventory to track private shopkeeper profit, while customer GST bills, A4 PDFs, and thermal receipts display **ONLY Bikri Rate**.
- **Customer Directory**: Save customer Name, Mobile Number, Full Address, and optional GSTIN for 1-tap billing.
- **Proper A4 GST PDF & WhatsApp Share**: Generates GST Tax Invoices (`PdfDocument`) with CGST/SGST or IGST breakdown, Amount in Indian Words, Mohar, and Signature — with 1-tap PDF preview and **WhatsApp PDF sharing**.
- **Bluetooth Mini Thermal Printer**: Real Classic Bluetooth SPP ESC/POS support for **58mm** and **80mm** mini thermal receipt printers.
- **Built-in GST Calculator**: Instant Exclusive (+GST) & Inclusive (extract GST) calculation across `0%`, `5%`, `12%`, `18%`, `28%`, or custom slabs with 1-tap transfer to a new bill.

package com.example.data

import android.content.Context
import com.example.R
import com.example.billing.StockManager
import com.example.billing.StockStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class StockChangeNotice(
    val productId: String,
    val productName: String,
    val unit: String,
    val previousStock: Double,
    val newStock: Double,
    val delta: Double,
    val status: StockStatus
)

data class InvoiceCreateResult(
    val invoice: Invoice,
    val stockChanges: List<StockChangeNotice> = emptyList()
)

data class InvoiceUpdateResult(
    val invoice: Invoice,
    val stockChanges: List<StockChangeNotice> = emptyList()
)

class BillingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google to access billing records.")
    }

    private fun userRoot(uid: String) = db.collection("users").document(uid)

    private fun <T> observeCollection(
        uid: String,
        collectionName: String,
        orderField: String,
        descending: Boolean,
        mapper: (List<DocumentSnapshot>) -> List<T>
    ): Flow<List<T>> = flow {
        val currentUid = auth.currentUser?.uid
        if (currentUid.isNullOrBlank() || currentUid != uid) {
            throw IllegalStateException("Authenticated session mismatch.")
        }
        val path = "users/$uid/$collectionName"
        val direction = if (descending) Query.Direction.DESCENDING else Query.Direction.ASCENDING
        emitAll(
            userRoot(uid).collection(collectionName)
                .orderBy(orderField, direction)
                .snapshots()
                .map { snapshot -> mapper(snapshot.documents) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeShops(uid: String): Flow<List<ShopProfile>> =
        observeCollection(uid, "shops", "createdAt", descending = false) { docs ->
            docs.mapNotNull { it.toObject(ShopProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        }

    fun observeProducts(uid: String): Flow<List<ProductItem>> =
        observeCollection(uid, "products", "name", descending = false) { docs ->
            docs.mapNotNull { it.toObject(ProductItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        }

    fun observeCustomers(uid: String): Flow<List<Customer>> =
        observeCollection(uid, "customers", "name", descending = false) { docs ->
            docs.mapNotNull { it.toObject(Customer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        }

    fun observeInvoices(uid: String): Flow<List<Invoice>> =
        observeCollection(uid, "invoices", "issuedAtMillis", descending = true) { docs ->
            docs.mapNotNull { it.toObject(Invoice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        }

    suspend fun saveShop(shop: ShopProfile, existingShops: List<ShopProfile>): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/shops"
        return try {
            val uid = requireUserId()
            require(shop.shopName.trim().isNotEmpty()) { "Shop name cannot be empty." }
            val collection = userRoot(uid).collection("shops")
            val isNew = shop.id.isBlank()
            val docRef = if (isNew) collection.document() else collection.document(shop.id)
            docPath = docRef.path

            val batch = db.batch()
            if (shop.defaultActive) {
                existingShops.filter { it.id != docRef.id && it.defaultActive }.forEach { other ->
                    batch.update(
                        collection.document(other.id),
                        mapOf("defaultActive" to false, "updatedAt" to FieldValue.serverTimestamp())
                    )
                }
            }
            if (isNew) {
                batch.set(docRef, shop.toCreateMap())
            } else {
                batch.update(docRef, shop.toUpdateMap())
            }
            batch.commit().await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (shop.id.isBlank()) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteShop(shopId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/shops/$shopId"
        return try {
            val uid = requireUserId()
            require(shopId.isNotBlank()) { "Invalid shop ID." }
            val docRef = userRoot(uid).collection("shops").document(shopId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveProduct(product: ProductItem): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/products"
        return try {
            val uid = requireUserId()
            require(product.name.trim().isNotEmpty()) { "Product name cannot be empty." }
            require(product.purchaseRate >= 0.0 && product.sellingRate >= 0.0) { "Rates cannot be negative." }
            val collection = userRoot(uid).collection("products")
            val isNew = product.id.isBlank()
            val docRef = if (isNew) collection.document() else collection.document(product.id)
            docPath = docRef.path
            if (isNew) {
                docRef.set(product.toCreateMap()).await()
            } else {
                docRef.update(product.toUpdateMap()).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (product.id.isBlank()) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(productId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/products/$productId"
        return try {
            val uid = requireUserId()
            require(productId.isNotBlank()) { "Invalid product ID." }
            val docRef = userRoot(uid).collection("products").document(productId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adjustProductStock(productId: String, delta: Double): Result<Double> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/products/$productId"
        return try {
            val uid = requireUserId()
            require(productId.isNotBlank()) { "Invalid product ID." }
            val docRef = userRoot(uid).collection("products").document(productId)
            docPath = docRef.path

            val newStock = db.runTransaction { transaction ->
                val snap = transaction.get(docRef)
                if (!snap.exists()) throw IllegalStateException("Item no longer exists.")
                val currentStock = (snap.get("stockQty") as? Number)?.toDouble() ?: 0.0
                val updatedStock = StockManager.computeUpdatedStockQty(currentStock, delta)
                transaction.update(
                    docRef,
                    mapOf(
                        "stockQty" to updatedStock,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                updatedStock
            }.await()
            Result.success(newStock)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveCustomer(customer: Customer): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/customers"
        return try {
            val uid = requireUserId()
            require(customer.name.trim().isNotEmpty()) { "Customer name cannot be empty." }
            val collection = userRoot(uid).collection("customers")
            val isNew = customer.id.isBlank()
            val docRef = if (isNew) collection.document() else collection.document(customer.id)
            docPath = docRef.path
            if (isNew) {
                docRef.set(customer.toCreateMap()).await()
            } else {
                docRef.update(customer.toUpdateMap()).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (customer.id.isBlank()) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomer(customerId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/customers/$customerId"
        return try {
            val uid = requireUserId()
            require(customerId.isNotBlank()) { "Invalid customer ID." }
            val docRef = userRoot(uid).collection("customers").document(customerId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createInvoice(
        invoice: Invoice,
        shop: ShopProfile,
        newCustomerIfRequested: Customer? = null
    ): Result<InvoiceCreateResult> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/invoices"
        return try {
            val uid = requireUserId()
            require(invoice.items.isNotEmpty() && invoice.items.size <= 50) {
                "Invoice must contain between 1 and 50 items."
            }
            require(invoice.customerName.trim().isNotEmpty()) { "Customer name is required." }

            val root = userRoot(uid)
            val invoiceRef = root.collection("invoices").document()
            docPath = invoiceRef.path

            val stockDeltas = StockManager.computeStockDeltasForCreate(invoice.items)
            val productRefs = stockDeltas.keys.associateWith { pid ->
                root.collection("products").document(pid)
            }

            var finalCustomerId = invoice.customerId
            val custRef = if (newCustomerIfRequested != null && finalCustomerId.isBlank()) {
                root.collection("customers").document().also { finalCustomerId = it.id }
            } else null

            val savedInvoice = invoice.copy(
                id = invoiceRef.id,
                customerId = finalCustomerId
            )

            val stockNotices = db.runTransaction { transaction ->
                // Phase 1: All Firestore reads BEFORE any writes
                val productSnapshots = productRefs.mapValues { (_, ref) ->
                    transaction.get(ref)
                }

                // Phase 2: All Firestore writes
                if (custRef != null && newCustomerIfRequested != null) {
                    transaction.set(custRef, newCustomerIfRequested.toCreateMap())
                }
                transaction.set(invoiceRef, savedInvoice.toCreateMap())

                if (shop.id.isNotBlank()) {
                    val shopRef = root.collection("shops").document(shop.id)
                    transaction.update(
                        shopRef,
                        mapOf(
                            "nextInvoiceNumber" to (shop.nextInvoiceNumber + 1L).coerceAtMost(10_000_000L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val notices = mutableListOf<StockChangeNotice>()
                productSnapshots.forEach { (pid, snap) ->
                    if (snap.exists()) {
                        val delta = stockDeltas[pid] ?: 0.0
                        val currentStock = (snap.get("stockQty") as? Number)?.toDouble() ?: 0.0
                        val threshold = (snap.get("lowStockThreshold") as? Number)?.toDouble()
                            ?: StockManager.DEFAULT_LOW_STOCK_THRESHOLD
                        val nextStock = StockManager.computeUpdatedStockQty(currentStock, delta)
                        val name = snap.getString("name") ?: "Item"
                        val unit = snap.getString("unit") ?: "PCS"
                        transaction.update(
                            snap.reference,
                            mapOf(
                                "stockQty" to nextStock,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        notices.add(
                            StockChangeNotice(
                                productId = pid,
                                productName = name,
                                unit = unit,
                                previousStock = currentStock,
                                newStock = nextStock,
                                delta = delta,
                                status = StockManager.getStockStatus(nextStock, threshold)
                            )
                        )
                    }
                }
                notices
            }.await()

            Result.success(InvoiceCreateResult(savedInvoice, stockNotices))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateInvoiceOptions(
        invoiceId: String,
        includeStamp: Boolean,
        includeSignature: Boolean,
        paymentMode: PaymentMode,
        notes: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/invoices/$invoiceId"
        return try {
            val uid = requireUserId()
            require(invoiceId.isNotBlank()) { "Invalid invoice ID." }
            val docRef = userRoot(uid).collection("invoices").document(invoiceId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "includeStamp" to includeStamp,
                    "includeSignature" to includeSignature,
                    "paymentMode" to paymentMode.name,
                    "notes" to notes.trim().take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Automatically clears expired warranty metadata from bills without deleting or
     * altering any invoice items, customer info, stock, GST totals, or billing history.
     */
    suspend fun clearExpiredWarrantiesBatch(invoiceIds: List<String>): Result<Int> {
        val cleanIds = invoiceIds.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) return Result.success(0)
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/invoices/${cleanIds.first()}"
        return try {
            val uid = requireUserId()
            val invoicesCol = userRoot(uid).collection("invoices")
            var cleared = 0
            cleanIds.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { id ->
                    val ref = invoicesCol.document(id)
                    docPath = ref.path
                    batch.update(
                        ref,
                        mapOf(
                            "hasWarranty" to false,
                            "warrantyMonths" to 0,
                            "warrantyDays" to 0,
                            "warrantyEndMillis" to 0L,
                            "warrantyLabel" to "",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    cleared += 1
                }
                batch.commit().await()
            }
            Result.success(cleared)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateInvoiceWithStockSync(updatedInvoice: Invoice): Result<InvoiceUpdateResult> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/invoices/${updatedInvoice.id}"
        return try {
            val uid = requireUserId()
            require(updatedInvoice.id.isNotBlank()) { "Invalid invoice ID." }
            require(updatedInvoice.items.isNotEmpty() && updatedInvoice.items.size <= 50) {
                "Invoice must contain between 1 and 50 items."
            }
            require(updatedInvoice.customerName.trim().isNotEmpty()) { "Customer name is required." }

            val root = userRoot(uid)
            val invoiceRef = root.collection("invoices").document(updatedInvoice.id)
            docPath = invoiceRef.path

            val notices = db.runTransaction { transaction ->
                // Phase 1: Read existing invoice first, then all affected product documents
                val invSnap = transaction.get(invoiceRef)
                if (!invSnap.exists()) {
                    throw IllegalStateException("Bill no longer exists.")
                }
                val existingInvoice = invSnap.toObject(
                    Invoice::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val stockDeltas = StockManager.computeStockDeltasForUpdate(
                    oldItems = existingInvoice?.items.orEmpty(),
                    newItems = updatedInvoice.items
                )
                val productSnapshots = stockDeltas.keys.associateWith { pid ->
                    transaction.get(root.collection("products").document(pid))
                }

                // Phase 2: All writes after reads
                transaction.update(invoiceRef, updatedInvoice.toEditUpdateMap())

                val changes = mutableListOf<StockChangeNotice>()
                productSnapshots.forEach { (pid, snap) ->
                    if (snap.exists()) {
                        val delta = stockDeltas[pid] ?: 0.0
                        val currentStock = (snap.get("stockQty") as? Number)?.toDouble() ?: 0.0
                        val threshold = (snap.get("lowStockThreshold") as? Number)?.toDouble()
                            ?: StockManager.DEFAULT_LOW_STOCK_THRESHOLD
                        val nextStock = StockManager.computeUpdatedStockQty(currentStock, delta)
                        val name = snap.getString("name") ?: "Item"
                        val unit = snap.getString("unit") ?: "PCS"
                        transaction.update(
                            snap.reference,
                            mapOf(
                                "stockQty" to nextStock,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        changes.add(
                            StockChangeNotice(
                                productId = pid,
                                productName = name,
                                unit = unit,
                                previousStock = currentStock,
                                newStock = nextStock,
                                delta = delta,
                                status = StockManager.getStockStatus(nextStock, threshold)
                            )
                        )
                    }
                }
                changes
            }.await()

            Result.success(InvoiceUpdateResult(updatedInvoice, notices))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteInvoice(invoiceId: String, restoreStock: Boolean = true): Result<List<StockChangeNotice>> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/invoices/$invoiceId"
        return try {
            val uid = requireUserId()
            require(invoiceId.isNotBlank()) { "Invalid invoice ID." }
            val root = userRoot(uid)
            val invoiceRef = root.collection("invoices").document(invoiceId)
            docPath = invoiceRef.path

            val notices = db.runTransaction { transaction ->
                val invSnap = transaction.get(invoiceRef)
                if (!invSnap.exists()) {
                    throw IllegalStateException("Bill no longer exists.")
                }
                val existingInvoice = invSnap.toObject(
                    Invoice::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val restoreQtys = if (restoreStock) {
                    StockManager.aggregateProductQuantities(existingInvoice?.items.orEmpty())
                } else {
                    emptyMap()
                }
                val productSnapshots = restoreQtys.keys.associateWith { pid ->
                    transaction.get(root.collection("products").document(pid))
                }

                val changes = mutableListOf<StockChangeNotice>()
                productSnapshots.forEach { (pid, snap) ->
                    if (snap.exists()) {
                        val addBackQty = restoreQtys[pid] ?: 0.0
                        val currentStock = (snap.get("stockQty") as? Number)?.toDouble() ?: 0.0
                        val threshold = (snap.get("lowStockThreshold") as? Number)?.toDouble()
                            ?: StockManager.DEFAULT_LOW_STOCK_THRESHOLD
                        val nextStock = StockManager.computeUpdatedStockQty(currentStock, addBackQty)
                        val name = snap.getString("name") ?: "Item"
                        val unit = snap.getString("unit") ?: "PCS"
                        transaction.update(
                            snap.reference,
                            mapOf(
                                "stockQty" to nextStock,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        changes.add(
                            StockChangeNotice(
                                productId = pid,
                                productName = name,
                                unit = unit,
                                previousStock = currentStock,
                                newStock = nextStock,
                                delta = addBackQty,
                                status = StockManager.getStockStatus(nextStock, threshold)
                            )
                        )
                    }
                }
                transaction.delete(invoiceRef)
                changes
            }.await()

            Result.success(notices)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}

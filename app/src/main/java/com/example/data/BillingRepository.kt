package com.example.data

import android.content.Context
import android.util.Log
import com.example.billing.StockManager
import com.example.billing.StockStatus
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

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
    val stockChanges: List<StockChangeNotice> = emptyList(),
    val queuedOffline: Boolean = false
)

data class InvoiceUpdateResult(
    val invoice: Invoice,
    val stockChanges: List<StockChangeNotice> = emptyList(),
    val queuedOffline: Boolean = false
)

data class ProductHsnUpdateEntry(
    val productId: String,
    val hsnCode: String,
    val gstRate: Double? = null
)

class BillingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(FirestoreProvider.getDatabase(context))

    private val _pendingWritesByCollection = MutableStateFlow<Map<String, Int>>(emptyMap())
    val pendingSyncCount: Flow<Int> = _pendingWritesByCollection.map { counts ->
        counts.values.sum()
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google to access billing records.")
    }

    private fun userRoot(uid: String) = db.collection("users").document(uid)

    private fun isNetworkOrOfflineException(t: Throwable): Boolean {
        val fsEx = generateSequence(t) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsEx != null) {
            return when (fsEx.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                FirebaseFirestoreException.Code.ABORTED -> true
                else -> false
            }
        }
        val msg = t.message?.lowercase().orEmpty()
        return msg.contains("offline") || msg.contains("unavailable") || msg.contains("network")
    }

    /**
     * Executes a Firestore write task with offline-first support:
     * - Returns true if confirmed by cloud backend right now.
     * - Returns false if persisted in local disk cache/queue (offline or slow network),
     *   so the app remains responsive offline and syncs automatically when online.
     * - Rethrows real errors (e.g. PERMISSION_DENIED, INVALID_ARGUMENT).
     */
    private suspend fun awaitTaskOfflineAware(
        task: Task<*>,
        isOnline: Boolean,
        opType: OperationType,
        docPath: String
    ): Boolean {
        if (!isOnline) {
            withTimeoutOrNull(OFFLINE_QUEUE_SETTLE_MS) {
                try {
                    task.await()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
            if (task.isComplete && !task.isSuccessful) {
                val ex = task.exception
                if (ex != null && !isNetworkOrOfflineException(ex)) {
                    throw ex
                }
            }
            return false
        }

        return try {
            val syncedNow = withTimeoutOrNull(ONLINE_ACK_TIMEOUT_MS) {
                task.await()
                true
            }
            syncedNow == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isNetworkOrOfflineException(e)) {
                Log.w(TAG, "Write queued offline for $docPath ($opType): ${e.message}")
                false
            } else {
                throw e
            }
        }
    }

    private suspend fun readDocumentCacheFirst(
        docRef: DocumentReference,
        isOnline: Boolean
    ): DocumentSnapshot? {
        val cached = try {
            docRef.get(Source.CACHE).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (cached != null && cached.exists()) return cached
        if (!isOnline) return null
        return try {
            withTimeoutOrNull(ONLINE_ACK_TIMEOUT_MS) {
                docRef.get().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

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
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val pendingDocs = snapshot.documents.count { it.metadata.hasPendingWrites() }
                    _pendingWritesByCollection.update { prev ->
                        if (prev[collectionName] == pendingDocs) prev
                        else prev + (collectionName to pendingDocs)
                    }
                    mapper(snapshot.documents)
                }
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

    suspend fun syncPendingWritesNow(): Result<Unit> {
        return try {
            requireUserId()
            db.enableNetwork().await()
            val synced = withTimeoutOrNull(SYNC_WAIT_TIMEOUT_MS) {
                db.waitForPendingWrites().await()
                true
            }
            if (synced == true) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Still syncing with cloud... check internet connection."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Sync pending writes failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun saveShop(
        shop: ShopProfile,
        existingShops: List<ShopProfile>,
        isOnline: Boolean = true
    ): Result<String> {
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
            awaitTaskOfflineAware(
                task = batch.commit(),
                isOnline = isOnline,
                opType = if (isNew) OperationType.CREATE else OperationType.UPDATE,
                docPath = docPath
            )
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (shop.id.isBlank()) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteShop(shopId: String, isOnline: Boolean = true): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/shops/$shopId"
        return try {
            val uid = requireUserId()
            require(shopId.isNotBlank()) { "Invalid shop ID." }
            val docRef = userRoot(uid).collection("shops").document(shopId)
            docPath = docRef.path
            awaitTaskOfflineAware(docRef.delete(), isOnline, OperationType.DELETE, docPath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveProduct(product: ProductItem, isOnline: Boolean = true): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/products"
        return try {
            val uid = requireUserId()
            require(product.name.trim().isNotEmpty()) { "Product name cannot be empty." }
            require(product.purchaseRate >= 0.0 && product.sellingRate >= 0.0) { "Rates cannot be negative." }
            val collection = userRoot(uid).collection("products")
            val isNew = product.id.isBlank()
            val docRef = if (isNew) collection.document() else collection.document(product.id)
            docPath = docRef.path
            val task = if (isNew) {
                docRef.set(product.toCreateMap())
            } else {
                docRef.update(product.toUpdateMap())
            }
            awaitTaskOfflineAware(
                task = task,
                isOnline = isOnline,
                opType = if (isNew) OperationType.CREATE else OperationType.UPDATE,
                docPath = docPath
            )
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (product.id.isBlank()) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun bulkUpdateProductHsn(
        updates: List<ProductHsnUpdateEntry>,
        isOnline: Boolean = true
    ): Result<Int> {
        val cleanUpdates = updates
            .mapNotNull { entry ->
                val cleanId = entry.productId.trim()
                val cleanHsn = entry.hsnCode.trim().uppercase().take(20)
                if (cleanId.isBlank() || cleanHsn.isBlank()) null
                else entry.copy(productId = cleanId, hsnCode = cleanHsn)
            }
            .distinctBy { it.productId }
        if (cleanUpdates.isEmpty()) return Result.success(0)

        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/products/${cleanUpdates.first().productId}"
        return try {
            val uid = requireUserId()
            val productsCol = userRoot(uid).collection("products")
            var updatedCount = 0
            cleanUpdates.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { item ->
                    val ref = productsCol.document(item.productId)
                    docPath = ref.path
                    val fields = mutableMapOf<String, Any>(
                        "hsnCode" to item.hsnCode,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    item.gstRate?.let { rate ->
                        fields["gstRate"] = rate.coerceIn(0.0, 100.0)
                    }
                    batch.update(ref, fields)
                    updatedCount += 1
                }
                awaitTaskOfflineAware(
                    task = batch.commit(),
                    isOnline = isOnline,
                    opType = OperationType.UPDATE,
                    docPath = docPath
                )
            }
            Result.success(updatedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(productId: String, isOnline: Boolean = true): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/products/$productId"
        return try {
            val uid = requireUserId()
            require(productId.isNotBlank()) { "Invalid product ID." }
            val docRef = userRoot(uid).collection("products").document(productId)
            docPath = docRef.path
            awaitTaskOfflineAware(docRef.delete(), isOnline, OperationType.DELETE, docPath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adjustProductStock(
        productId: String,
        delta: Double,
        cachedStockQty: Double? = null,
        isOnline: Boolean = true
    ): Result<Double> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/products/$productId"
        return try {
            val uid = requireUserId()
            require(productId.isNotBlank()) { "Invalid product ID." }
            val docRef = userRoot(uid).collection("products").document(productId)
            docPath = docRef.path

            val currentStock = cachedStockQty ?: run {
                val snap = readDocumentCacheFirst(docRef, isOnline)
                    ?: throw IllegalStateException("Item no longer exists.")
                if (!snap.exists()) throw IllegalStateException("Item no longer exists.")
                (snap.get("stockQty") as? Number)?.toDouble() ?: 0.0
            }
            val updatedStock = StockManager.computeUpdatedStockQty(currentStock, delta)
            val task = docRef.update(
                mapOf(
                    "stockQty" to updatedStock,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            awaitTaskOfflineAware(task, isOnline, OperationType.UPDATE, docPath)
            Result.success(updatedStock)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveCustomer(customer: Customer, isOnline: Boolean = true): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/customers"
        return try {
            val uid = requireUserId()
            require(customer.name.trim().isNotEmpty()) { "Customer name cannot be empty." }
            val collection = userRoot(uid).collection("customers")
            val isNew = customer.id.isBlank()
            val docRef = if (isNew) collection.document() else collection.document(customer.id)
            docPath = docRef.path
            val task = if (isNew) {
                docRef.set(customer.toCreateMap())
            } else {
                docRef.update(customer.toUpdateMap())
            }
            awaitTaskOfflineAware(
                task = task,
                isOnline = isOnline,
                opType = if (isNew) OperationType.CREATE else OperationType.UPDATE,
                docPath = docPath
            )
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (customer.id.isBlank()) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomer(customerId: String, isOnline: Boolean = true): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/customers/$customerId"
        return try {
            val uid = requireUserId()
            require(customerId.isNotBlank()) { "Invalid customer ID." }
            val docRef = userRoot(uid).collection("customers").document(customerId)
            docPath = docRef.path
            awaitTaskOfflineAware(docRef.delete(), isOnline, OperationType.DELETE, docPath)
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
        newCustomerIfRequested: Customer? = null,
        cachedProductsById: Map<String, ProductItem> = emptyMap(),
        isOnline: Boolean = true
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
            val resolvedProducts = mutableMapOf<String, ProductItem>()
            for (pid in stockDeltas.keys) {
                val cached = cachedProductsById[pid]
                if (cached != null) {
                    resolvedProducts[pid] = cached
                } else {
                    val snap = readDocumentCacheFirst(root.collection("products").document(pid), isOnline)
                    val obj = snap?.toObject(ProductItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    if (obj != null) resolvedProducts[pid] = obj
                }
            }

            var finalCustomerId = invoice.customerId
            val custRef = if (newCustomerIfRequested != null && finalCustomerId.isBlank()) {
                root.collection("customers").document().also { finalCustomerId = it.id }
            } else null

            val savedInvoice = invoice.copy(
                id = invoiceRef.id,
                customerId = finalCustomerId
            )

            val batch = db.batch()
            if (custRef != null && newCustomerIfRequested != null) {
                batch.set(custRef, newCustomerIfRequested.toCreateMap())
            }
            batch.set(invoiceRef, savedInvoice.toCreateMap())

            if (shop.id.isNotBlank()) {
                val shopRef = root.collection("shops").document(shop.id)
                batch.update(
                    shopRef,
                    mapOf(
                        "nextInvoiceNumber" to (shop.nextInvoiceNumber + 1L).coerceIn(1L, 10_000_000L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val notices = mutableListOf<StockChangeNotice>()
            resolvedProducts.forEach { (pid, prod) ->
                val delta = stockDeltas[pid] ?: 0.0
                val currentStock = prod.stockQty
                val threshold = prod.lowStockThreshold
                val nextStock = StockManager.computeUpdatedStockQty(currentStock, delta)
                batch.update(
                    root.collection("products").document(pid),
                    mapOf(
                        "stockQty" to nextStock,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                notices.add(
                    StockChangeNotice(
                        productId = pid,
                        productName = prod.name.ifBlank { "Item" },
                        unit = prod.unit.ifBlank { "PCS" },
                        previousStock = currentStock,
                        newStock = nextStock,
                        delta = delta,
                        status = StockManager.getStockStatus(nextStock, threshold)
                    )
                )
            }

            val syncedNow = awaitTaskOfflineAware(
                task = batch.commit(),
                isOnline = isOnline,
                opType = OperationType.CREATE,
                docPath = docPath
            )
            Result.success(InvoiceCreateResult(savedInvoice, notices, queuedOffline = !syncedNow))
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
        notes: String,
        isOnline: Boolean = true
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/invoices/$invoiceId"
        return try {
            val uid = requireUserId()
            require(invoiceId.isNotBlank()) { "Invalid invoice ID." }
            val docRef = userRoot(uid).collection("invoices").document(invoiceId)
            docPath = docRef.path
            val task = docRef.update(
                mapOf(
                    "includeStamp" to includeStamp,
                    "includeSignature" to includeSignature,
                    "paymentMode" to paymentMode.name,
                    "notes" to notes.trim().take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            awaitTaskOfflineAware(task, isOnline, OperationType.UPDATE, docPath)
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
    suspend fun clearExpiredWarrantiesBatch(
        invoiceIds: List<String>,
        isOnline: Boolean = true
    ): Result<Int> {
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
                awaitTaskOfflineAware(batch.commit(), isOnline, OperationType.UPDATE, docPath)
            }
            Result.success(cleared)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateInvoiceWithStockSync(
        updatedInvoice: Invoice,
        existingInvoiceHint: Invoice? = null,
        cachedProductsById: Map<String, ProductItem> = emptyMap(),
        isOnline: Boolean = true
    ): Result<InvoiceUpdateResult> {
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

            val existingInvoice = existingInvoiceHint ?: run {
                val invSnap = readDocumentCacheFirst(invoiceRef, isOnline)
                    ?: throw IllegalStateException("Bill no longer exists.")
                if (!invSnap.exists()) throw IllegalStateException("Bill no longer exists.")
                invSnap.toObject(Invoice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

            val stockDeltas = StockManager.computeStockDeltasForUpdate(
                oldItems = existingInvoice?.items.orEmpty(),
                newItems = updatedInvoice.items
            )

            val resolvedProducts = mutableMapOf<String, ProductItem>()
            for (pid in stockDeltas.keys) {
                val cached = cachedProductsById[pid]
                if (cached != null) {
                    resolvedProducts[pid] = cached
                } else {
                    val snap = readDocumentCacheFirst(root.collection("products").document(pid), isOnline)
                    val obj = snap?.toObject(ProductItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    if (obj != null) resolvedProducts[pid] = obj
                }
            }

            val batch = db.batch()
            batch.update(invoiceRef, updatedInvoice.toEditUpdateMap())

            val changes = mutableListOf<StockChangeNotice>()
            resolvedProducts.forEach { (pid, prod) ->
                val delta = stockDeltas[pid] ?: 0.0
                val currentStock = prod.stockQty
                val threshold = prod.lowStockThreshold
                val nextStock = StockManager.computeUpdatedStockQty(currentStock, delta)
                batch.update(
                    root.collection("products").document(pid),
                    mapOf(
                        "stockQty" to nextStock,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                changes.add(
                    StockChangeNotice(
                        productId = pid,
                        productName = prod.name.ifBlank { "Item" },
                        unit = prod.unit.ifBlank { "PCS" },
                        previousStock = currentStock,
                        newStock = nextStock,
                        delta = delta,
                        status = StockManager.getStockStatus(nextStock, threshold)
                    )
                )
            }

            val syncedNow = awaitTaskOfflineAware(
                task = batch.commit(),
                isOnline = isOnline,
                opType = OperationType.UPDATE,
                docPath = docPath
            )
            Result.success(InvoiceUpdateResult(updatedInvoice, changes, queuedOffline = !syncedNow))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteInvoice(
        invoiceId: String,
        restoreStock: Boolean = true,
        existingInvoiceHint: Invoice? = null,
        cachedProductsById: Map<String, ProductItem> = emptyMap(),
        isOnline: Boolean = true
    ): Result<List<StockChangeNotice>> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/invoices/$invoiceId"
        return try {
            val uid = requireUserId()
            require(invoiceId.isNotBlank()) { "Invalid invoice ID." }
            val root = userRoot(uid)
            val invoiceRef = root.collection("invoices").document(invoiceId)
            docPath = invoiceRef.path

            val existingInvoice = existingInvoiceHint ?: run {
                val invSnap = readDocumentCacheFirst(invoiceRef, isOnline)
                invSnap?.toObject(Invoice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

            val restoreQtys = if (restoreStock) {
                StockManager.aggregateProductQuantities(existingInvoice?.items.orEmpty())
            } else {
                emptyMap()
            }

            val resolvedProducts = mutableMapOf<String, ProductItem>()
            for (pid in restoreQtys.keys) {
                val cached = cachedProductsById[pid]
                if (cached != null) {
                    resolvedProducts[pid] = cached
                } else {
                    val snap = readDocumentCacheFirst(root.collection("products").document(pid), isOnline)
                    val obj = snap?.toObject(ProductItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    if (obj != null) resolvedProducts[pid] = obj
                }
            }

            val batch = db.batch()
            val changes = mutableListOf<StockChangeNotice>()
            resolvedProducts.forEach { (pid, prod) ->
                val addBackQty = restoreQtys[pid] ?: 0.0
                val currentStock = prod.stockQty
                val threshold = prod.lowStockThreshold
                val nextStock = StockManager.computeUpdatedStockQty(currentStock, addBackQty)
                batch.update(
                    root.collection("products").document(pid),
                    mapOf(
                        "stockQty" to nextStock,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                changes.add(
                    StockChangeNotice(
                        productId = pid,
                        productName = prod.name.ifBlank { "Item" },
                        unit = prod.unit.ifBlank { "PCS" },
                        previousStock = currentStock,
                        newStock = nextStock,
                        delta = addBackQty,
                        status = StockManager.getStockStatus(nextStock, threshold)
                    )
                )
            }
            batch.delete(invoiceRef)

            awaitTaskOfflineAware(
                task = batch.commit(),
                isOnline = isOnline,
                opType = OperationType.DELETE,
                docPath = docPath
            )
            Result.success(changes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "BillingRepo"
        const val ONLINE_ACK_TIMEOUT_MS = 3500L
        const val OFFLINE_QUEUE_SETTLE_MS = 180L
        const val SYNC_WAIT_TIMEOUT_MS = 8000L
    }
}

package com.example.data

import android.content.Context
import com.example.R
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
    ): Result<Invoice> {
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
            val batch = db.batch()

            var finalCustomerId = invoice.customerId
            if (newCustomerIfRequested != null && finalCustomerId.isBlank()) {
                val custRef = root.collection("customers").document()
                finalCustomerId = custRef.id
                batch.set(custRef, newCustomerIfRequested.toCreateMap())
            }

            val savedInvoice = invoice.copy(
                id = invoiceRef.id,
                customerId = finalCustomerId
            )
            batch.set(invoiceRef, savedInvoice.toCreateMap())

            if (shop.id.isNotBlank()) {
                val shopRef = root.collection("shops").document(shop.id)
                batch.update(
                    shopRef,
                    mapOf(
                        "nextInvoiceNumber" to (shop.nextInvoiceNumber + 1L).coerceAtMost(10_000_000L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(savedInvoice)
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

    suspend fun deleteInvoice(invoiceId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/invoices/$invoiceId"
        return try {
            val uid = requireUserId()
            require(invoiceId.isNotBlank()) { "Invalid invoice ID." }
            val docRef = userRoot(uid).collection("invoices").document(invoiceId)
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
}

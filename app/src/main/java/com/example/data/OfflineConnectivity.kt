package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object FirestoreProvider {
    private const val TAG = "FirestoreProvider"

    @Volatile
    private var configured = false

    fun getDatabase(context: Context): FirebaseFirestore {
        val appContext = context.applicationContext
        val databaseId = appContext.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        if (!configured) {
            synchronized(this) {
                if (!configured) {
                    try {
                        val cacheSettings = PersistentCacheSettings.newBuilder()
                            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            .build()
                        val settings = FirebaseFirestoreSettings.Builder()
                            .setLocalCacheSettings(cacheSettings)
                            .build()
                        db.firestoreSettings = settings
                    } catch (e: IllegalStateException) {
                        // Safe if Firestore was already initialized elsewhere
                        Log.d(TAG, "Firestore settings already applied: ${e.message}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Non-fatal Firestore persistent cache config issue: ${e.message}")
                    }
                    configured = true
                }
            }
        }
        return db
    }
}

object NetworkMonitor {

    fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        } catch (_: Exception) {
            false
        }
    }

    fun observeConnectivity(context: Context): Flow<Boolean> = callbackFlow {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        trySend(isOnline(appContext))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isOnline(appContext))
            }

            override fun onLost(network: Network) {
                trySend(isOnline(appContext))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(isOnline(appContext))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        var registered = false
        try {
            cm.registerNetworkCallback(request, callback)
            registered = true
        } catch (_: Exception) {
            trySend(isOnline(appContext))
        }

        awaitClose {
            if (registered) {
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (_: Exception) {
                }
            }
        }
    }.distinctUntilChanged()
}

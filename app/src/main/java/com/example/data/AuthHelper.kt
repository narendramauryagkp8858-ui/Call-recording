package com.example.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest

private const val AUTH_TAG = "Auth"
private const val MISSING_CONFIG_PLACEHOLDER = "missing-google-services-json"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION")
fun getAppSigningSha1(context: Context): String {
    return try {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.let { si ->
                if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
            }
        } else {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            info.signatures
        }
        val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return "unknown"
        val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
        digest.joinToString(":") { "%02X".format(it) }
    } catch (_: Exception) {
        "unknown"
    }
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id).trim()
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    if (clientId.isBlank() || clientId == MISSING_CONFIG_PLACEHOLDER) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
    scope.launch {
        try {
            val activityContext = context.findActivity() ?: context
            val response = withTimeoutOrNull(2500L) {
                credentialManager.getCredential(activityContext, request)
            }
            val cred = response?.credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: (diagnostic: String) -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id).trim()
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }
    if (clientId.isBlank() || clientId == MISSING_CONFIG_PLACEHOLDER) {
        onAuthError("Google Sign-In is not configured in this APK (missing google-services.json). Add GOOGLE_SERVICES_JSON secret in GitHub Settings -> Secrets -> Actions and rebuild.")
        return
    }

    val sha1 = getAppSigningSha1(context)
    val clientSuffix = clientId.substringBefore('.').takeLast(12)

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()
    scope.launch {
        try {
            val activityContext = context.findActivity() ?: context
            val cred = credentialManager.getCredential(activityContext, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type received.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            val reason = e.message?.takeIf { it.isNotBlank() } ?: "cancelled"
            onAuthCancelled("Details: $reason | SHA1: $sha1 | Client: ...$clientSuffix")
        } catch (e: NoCredentialException) {
            Log.w(AUTH_TAG, "No Google credential available on device: ${e.message}", e)
            onAuthError("No Google account found or Google Sign-In prompts disabled in phone Settings > Google > Sign in with Google. (SHA1: $sha1)")
        } catch (e: GetCredentialProviderConfigurationException) {
            Log.e(AUTH_TAG, "Credential provider configuration error: ${e.message}", e)
            onAuthError("Google Play Services Credential Manager is unavailable or outdated on this phone. Please update Google Play Services.")
        } catch (e: GetCredentialUnsupportedException) {
            Log.e(AUTH_TAG, "Credential Manager unsupported: ${e.message}", e)
            onAuthError("Credential Manager is not supported on this device/Play Services version. Please update Google Play Services.")
        } catch (e: GetCredentialException) {
            Log.e(AUTH_TAG, "Credential Manager error: ${e.type} - ${e.message}", e)
            val msg = e.errorMessage?.toString() ?: e.localizedMessage ?: "Google Sign-In failed (${e.type})."
            onAuthError("$msg (SHA1: $sha1 | Client: ...$clientSuffix)")
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed. Please try again.")
        }
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

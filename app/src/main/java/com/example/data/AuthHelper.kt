package com.example.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
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
import java.util.concurrent.TimeUnit

private const val AUTH_TAG = "PhoneAuth"
private const val MISSING_CONFIG_PLACEHOLDER = "missing-google-services-json"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

/**
 * Formats a country code + raw mobile number into E.164 format (e.g. "+919876543210"),
 * or returns null if invalid.
 */
fun formatE164PhoneNumber(countryCodeInput: String, phoneInput: String): String? {
    val cleanedDigits = phoneInput.filter { it.isDigit() }
    val trimmedInput = phoneInput.trim()
    if (trimmedInput.startsWith("+") && cleanedDigits.length in 10..15) {
        return "+$cleanedDigits"
    }
    val ccDigits = countryCodeInput.filter { it.isDigit() }.ifBlank { "91" }
    if (cleanedDigits.length < 7 || cleanedDigits.length > 13) return null
    val combined = "+$ccDigits$cleanedDigits"
    val totalDigits = combined.count { it.isDigit() }
    return if (totalDigits in 10..15) combined else null
}

private fun checkFirebaseConfigured(context: Context): String? {
    val projectId = try {
        context.getString(R.string.project_id)
    } catch (_: Exception) {
        ""
    }
    if (projectId.isBlank() || projectId == MISSING_CONFIG_PLACEHOLDER) {
        return "Firebase is not configured in this APK (missing google-services.json). Add GOOGLE_SERVICES_JSON secret in GitHub or generate the APK from AI Studio."
    }
    return null
}

fun sendPhoneOtp(
    context: Context,
    e164PhoneNumber: String,
    scope: CoroutineScope,
    resendToken: PhoneAuthProvider.ForceResendingToken? = null,
    onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken) -> Unit,
    onAutoVerified: () -> Unit,
    onError: (String) -> Unit
) {
    val configError = checkFirebaseConfigured(context)
    if (configError != null) {
        onError(configError)
        return
    }
    val activity = context.findActivity()
    if (activity == null) {
        onError("Unable to start phone verification from this context.")
        return
    }

    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            scope.launch {
                try {
                    Firebase.auth.signInWithCredential(credential).await()
                    onAutoVerified()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(AUTH_TAG, "Auto-verification sign-in failed", e)
                    onError(e.localizedMessage ?: "Verification failed. Please try entering the OTP.")
                }
            }
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.e(AUTH_TAG, "Phone verification failed", e)
            val msg = when (e) {
                is FirebaseAuthInvalidCredentialsException ->
                    "Invalid mobile number. Please check the country code and mobile number."
                is FirebaseTooManyRequestsException ->
                    "Too many OTP attempts. Please wait a bit and try again."
                else -> {
                    val raw = e.message.orEmpty()
                    if (raw.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ||
                        raw.contains("not enabled", ignoreCase = true)
                    ) {
                        "Phone OTP login is not enabled in Firebase Console yet. Enable Authentication > Sign-in method > Phone for project gen-lang-client-0894883542."
                    } else {
                        e.localizedMessage ?: "Could not send OTP. Check internet connection and try again."
                    }
                }
            }
            onError(msg)
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            onCodeSent(verificationId, token)
        }
    }

    val builder = PhoneAuthOptions.newBuilder(Firebase.auth)
        .setPhoneNumber(e164PhoneNumber)
        .setTimeout(60L, TimeUnit.SECONDS)
        .setActivity(activity)
        .setCallbacks(callbacks)

    if (resendToken != null) {
        builder.setForceResendingToken(resendToken)
    }

    try {
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    } catch (e: Exception) {
        Log.e(AUTH_TAG, "Failed to initiate verifyPhoneNumber", e)
        onError(e.localizedMessage ?: "Failed to start OTP verification.")
    }
}

fun verifyPhoneOtp(
    verificationId: String,
    otpCode: String,
    scope: CoroutineScope,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val cleanOtp = otpCode.filter { it.isDigit() }
    if (cleanOtp.length != 6) {
        onError("Please enter the 6-digit OTP code.")
        return
    }
    scope.launch {
        try {
            val credential = PhoneAuthProvider.getCredential(verificationId, cleanOtp)
            Firebase.auth.signInWithCredential(credential).await()
            onSuccess()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.w(AUTH_TAG, "Invalid OTP entered: ${e.message}")
            onError("Invalid OTP code. Please check the SMS and try again.")
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "OTP sign-in failed", e)
            onError(e.localizedMessage ?: "OTP verification failed. Please try again.")
        }
    }
}

fun signOut(
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                Firebase.auth.signOut()
            } finally {
                onSignOutComplete()
            }
        }
    }
}

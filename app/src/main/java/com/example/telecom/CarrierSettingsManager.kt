package com.example.telecom

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

enum class ForwardCondition(
    val label: String,
    val activatePrefix: String,
    val deactivateCode: String,
    val statusCode: String
) {
    ALWAYS("Always Forward", "*21*", "##21#", "*#21#"),
    WHEN_BUSY("When Busy", "*67*", "##67#", "*#67#"),
    WHEN_UNANSWERED("When Unanswered", "*61*", "##61#", "*#61#"),
    WHEN_UNREACHABLE("When Unreachable", "*62*", "##62#", "*#62#")
}

object CarrierSettingsManager {

    @SuppressLint("MissingPermission")
    fun placeRealCallOrDial(context: Context, rawNumberOrMmi: String): Result<String> {
        val cleaned = rawNumberOrMmi.trim()
        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a valid phone number or MMI code."))
        }

        val hasCallPhone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val uri = Uri.fromParts("tel", cleaned, null)

        if (hasCallPhone) {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (telecom != null) {
                val placed = runCatching {
                    telecom.placeCall(uri, Bundle())
                }.isSuccess
                if (placed) {
                    return Result.success("Dialing $cleaned")
                }
            }
            val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val called = runCatching { context.startActivity(callIntent) }.isSuccess
            if (called) {
                return Result.success("Dialing $cleaned")
            }
        }

        val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(dialIntent)
            "Opened dial prompt for $cleaned (grant Phone permission for instant 1-tap calling)"
        }
    }

    @SuppressLint("MissingPermission")
    fun executeMmiOrUssd(
        context: Context,
        mmiCode: String,
        onResult: (String) -> Unit
    ) {
        val hasCallPhone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCallPhone) {
            placeRealCallOrDial(context, mmiCode)
            onResult("Opened $mmiCode in dialer. Grant CALL_PHONE permission for direct carrier requests.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm != null) {
                val callback = object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager,
                        request: String,
                        response: CharSequence
                    ) {
                        onResult("Carrier response ($request): $response")
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager,
                        request: String,
                        failureCode: Int
                    ) {
                        // Many GSM supplementary codes (*43#, *21*...#) must go through TelecomManager.placeCall
                        // instead of sendUssdRequest; automatically route to network MMI call!
                        val dialRes = placeRealCallOrDial(context, mmiCode)
                        dialRes.onSuccess {
                            onResult("Sent carrier MMI code $mmiCode to network.")
                        }.onFailure { err ->
                            onResult("Carrier request failed (code $failureCode): ${err.localizedMessage}")
                        }
                    }
                }
                val sent = runCatching {
                    tm.sendUssdRequest(mmiCode, callback, Handler(Looper.getMainLooper()))
                }.isSuccess
                if (sent) return
            }
        }

        val dialRes = placeRealCallOrDial(context, mmiCode)
        dialRes.onSuccess {
            onResult("Sent carrier MMI code $mmiCode to network.")
        }.onFailure { err ->
            onResult(err.localizedMessage ?: "Unable to execute $mmiCode")
        }
    }

    fun buildForwardActivateCode(condition: ForwardCondition, forwardToNumber: String): String {
        val sanitized = forwardToNumber.filter { it.isDigit() || it == '+' }
        return "${condition.activatePrefix}${sanitized}#"
    }

    const val MMI_CALL_WAITING_ENABLE = "*43#"
    const val MMI_CALL_WAITING_DISABLE = "#43#"
    const val MMI_CALL_WAITING_STATUS = "*#43#"
    const val MMI_CANCEL_ALL_FORWARDING = "##002#"
}

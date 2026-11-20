package com.example.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.example.AppContainer
import com.example.service.RecordingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val container = AppContainer.get(context)

        when (action) {
            Intent.ACTION_NEW_OUTGOING_CALL -> {
                @Suppress("DEPRECATION")
                val outgoingNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                container.callStateManager.onOutgoingCallPlaced(outgoingNumber)
            }

            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val stateExtra = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                @Suppress("DEPRECATION")
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                container.callStateManager.onPhoneStateBroadcast(stateExtra, incomingNumber)
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val settings = container.settingsDataStore.getSettingsOnce()
                        if (settings.autoRecordEnabled) {
                            container.callStateManager.registerTelephonyObserver()
                            RecordingService.postMonitorReadyNotification(context)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}

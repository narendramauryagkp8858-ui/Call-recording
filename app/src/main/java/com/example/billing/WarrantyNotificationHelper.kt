package com.example.billing

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.Invoice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WarrantyNotificationHelper {

    private const val CHANNEL_ID = "narendra_warranty_alerts"
    private const val CHANNEL_NAME = "Warranty Expiry Alerts"
    private const val CHANNEL_DESC = "Alerts 7 days before a bill warranty expires"
    private const val PREFS_NAME = "narendra_warranty_notified_prefs"

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = CHANNEL_DESC
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Posts at most one system notification per expiring invoice per calendar day,
     * only when notification permission is granted. In-app alerts always remain active.
     */
    fun notifyExpiringWarrantiesIfNeeded(
        context: Context,
        expiring: List<Pair<Invoice, WarrantyEvaluation>>,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (expiring.isEmpty() || !hasNotificationPermission(context)) return
        ensureChannel(context)

        val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val manager = NotificationManagerCompat.from(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            401,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val editor = prefs.edit()
        var postedAny = false

        expiring.take(5).forEach { (invoice, eval) ->
            val prefKey = "notified_${invoice.id}"
            if (prefs.getString(prefKey, null) != dayKey) {
                val title = "Warranty Expiring: ${invoice.invoiceNumber}"
                val body = "${invoice.customerName} • ${eval.badgeText}"
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n${eval.displayLabel}"))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()

                try {
                    manager.notify(invoice.id.hashCode(), notification)
                    editor.putString(prefKey, dayKey)
                    postedAny = true
                } catch (_: SecurityException) {
                    // Permission revoked between check and notify; in-app alerts remain active.
                }
            }
        }

        if (postedAny) {
            editor.apply()
        }
    }
}

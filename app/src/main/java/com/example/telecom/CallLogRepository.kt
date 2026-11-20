package com.example.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class CallLogEntryType(val label: String) {
    INCOMING("Incoming"),
    OUTGOING("Outgoing"),
    MISSED("Missed"),
    REJECTED("Rejected"),
    BLOCKED("Blocked"),
    UNKNOWN("Call")
}

data class CallLogItem(
    val id: Long,
    val number: String,
    val cachedName: String,
    val type: CallLogEntryType,
    val dateMillis: Long,
    val durationSeconds: Long,
    val geocodedLocation: String
)

class CallLogRepository(private val context: Context) {

    fun hasReadCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasWriteCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun loadRecentCallLogs(limit: Int = 250): List<CallLogItem> = withContext(Dispatchers.IO) {
        if (!hasReadCallLogPermission()) return@withContext emptyList()

        val items = mutableListOf<CallLogItem>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.GEOCODED_LOCATION
        )

        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val locIdx = cursor.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)

                while (cursor.moveToNext() && items.size < limit) {
                    val rawType = if (typeIdx >= 0) cursor.getInt(typeIdx) else 0
                    val mappedType = when (rawType) {
                        CallLog.Calls.INCOMING_TYPE -> CallLogEntryType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallLogEntryType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallLogEntryType.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallLogEntryType.REJECTED
                        CallLog.Calls.BLOCKED_TYPE -> CallLogEntryType.BLOCKED
                        else -> CallLogEntryType.UNKNOWN
                    }
                    items += CallLogItem(
                        id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L,
                        number = if (numIdx >= 0) cursor.getString(numIdx).orEmpty() else "",
                        cachedName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else "",
                        type = mappedType,
                        dateMillis = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L,
                        durationSeconds = if (durIdx >= 0) cursor.getLong(durIdx) else 0L,
                        geocodedLocation = if (locIdx >= 0) cursor.getString(locIdx).orEmpty() else ""
                    )
                }
            }
        }
        items
    }

    suspend fun deleteCallLogEntry(id: Long): Boolean = withContext(Dispatchers.IO) {
        if (!hasWriteCallLogPermission()) return@withContext false
        runCatching {
            val rows = context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} = ?",
                arrayOf(id.toString())
            )
            rows > 0
        }.getOrDefault(false)
    }

    suspend fun clearAllCallLogs(): Boolean = withContext(Dispatchers.IO) {
        if (!hasWriteCallLogPermission()) return@withContext false
        runCatching {
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
            true
        }.getOrDefault(false)
    }
}

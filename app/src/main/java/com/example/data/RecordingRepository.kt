package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RecordingRepository(
    private val context: Context,
    private val recordingDao: RecordingDao,
    private val contactRuleDao: ContactRuleDao
) {
    val recordingsDir: File
        get() = File(context.filesDir, "recordings").apply { if (!exists()) mkdirs() }

    fun observeRecordings(): Flow<List<RecordingEntity>> = recordingDao.observeAllRecordings()

    fun observeContactRules(): Flow<List<ContactRuleEntity>> = contactRuleDao.observeAllRules()

    suspend fun getAllRecordingsOnce(): List<RecordingEntity> = withContext(Dispatchers.IO) {
        recordingDao.getAllRecordingsOnce()
    }

    suspend fun getAllRulesOnce(): List<ContactRuleEntity> = withContext(Dispatchers.IO) {
        contactRuleDao.getAllRulesOnce()
    }

    suspend fun insertRecording(recording: RecordingEntity): Long = withContext(Dispatchers.IO) {
        recordingDao.insertRecording(recording)
    }

    suspend fun renameRecording(id: Long, newTitle: String) = withContext(Dispatchers.IO) {
        val cleaned = newTitle.trim()
        if (cleaned.isNotEmpty()) {
            recordingDao.renameRecording(id, cleaned)
        }
    }

    suspend fun deleteRecording(recording: RecordingEntity) = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(recording.filePath)
            if (file.exists()) file.delete()
        }
        recordingDao.deleteRecordingById(recording.id)
    }

    suspend fun deleteAllRecordings() = withContext(Dispatchers.IO) {
        val all = recordingDao.getAllRecordingsOnce()
        all.forEach { rec ->
            runCatching {
                val file = File(rec.filePath)
                if (file.exists()) file.delete()
            }
        }
        recordingsDir.listFiles()?.forEach { runCatching { it.delete() } }
        recordingDao.deleteAllRecordings()
    }

    suspend fun addContactRule(phoneNumber: String, displayName: String, ruleType: ContactRuleType) {
        withContext(Dispatchers.IO) {
            val normalized = normalizePhoneNumber(phoneNumber)
            if (normalized.isNotEmpty()) {
                contactRuleDao.insertRule(
                    ContactRuleEntity(
                        phoneNumber = normalized,
                        displayName = displayName.ifBlank { phoneNumber },
                        ruleType = ruleType
                    )
                )
            }
        }
    }

    suspend fun deleteContactRule(id: Long) = withContext(Dispatchers.IO) {
        contactRuleDao.deleteRuleById(id)
    }

    suspend fun shouldRecordCall(
        direction: CallDirection,
        phoneNumber: String,
        settings: AppSettings
    ): Boolean = withContext(Dispatchers.IO) {
        if (!settings.autoRecordEnabled) return@withContext false
        if (direction == CallDirection.INCOMING && !settings.autoRecordIncoming) return@withContext false
        if (direction == CallDirection.OUTGOING && !settings.autoRecordOutgoing) return@withContext false

        val rules = contactRuleDao.getAllRulesOnce()
        val normalizedIncoming = normalizePhoneNumber(phoneNumber)

        when (settings.filterMode) {
            AutoRecordFilterMode.ALL_CALLS -> true
            AutoRecordFilterMode.SELECTED_ONLY -> {
                if (normalizedIncoming.isEmpty()) return@withContext false
                rules.any {
                    it.ruleType == ContactRuleType.INCLUDE &&
                        numbersMatch(it.phoneNumber, normalizedIncoming)
                }
            }
            AutoRecordFilterMode.EXCLUDE_SELECTED -> {
                if (normalizedIncoming.isEmpty()) return@withContext true
                rules.none {
                    it.ruleType == ContactRuleType.EXCLUDE &&
                        numbersMatch(it.phoneNumber, normalizedIncoming)
                }
            }
        }
    }

    fun resolveContactName(phoneNumber: String): String {
        if (phoneNumber.isBlank()) return ""
        val hasContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContacts) return ""

        return runCatching {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0) ?: ""
                } else {
                    ""
                }
            } ?: ""
        }.getOrDefault("")
    }

    fun createOutputFile(format: AudioFormatOption, direction: CallDirection): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val prefix = when (direction) {
            CallDirection.INCOMING -> "IN"
            CallDirection.OUTGOING -> "OUT"
            CallDirection.MANUAL -> "REC"
        }
        return File(recordingsDir, "${prefix}_${stamp}.${format.extension}")
    }

    fun calculateTotalStorageBytes(): Long {
        return recordingsDir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
    }

    companion object {
        fun normalizePhoneNumber(raw: String): String {
            return raw.filter { it.isDigit() || it == '+' }
        }

        fun numbersMatch(ruleNumber: String, callNumber: String): Boolean {
            val a = ruleNumber.filter { it.isDigit() }
            val b = callNumber.filter { it.isDigit() }
            if (a.isEmpty() || b.isEmpty()) return false
            val tailA = if (a.length > 7) a.takeLast(7) else a
            val tailB = if (b.length > 7) b.takeLast(7) else b
            return a == b || tailA == tailB
        }
    }
}

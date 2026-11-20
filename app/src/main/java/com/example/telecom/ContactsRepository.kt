package com.example.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.data.RecordingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceContact(
    val id: Long,
    val name: String,
    val number: String,
    val isStarred: Boolean,
    val label: String
)

class ContactsRepository(private val context: Context) {

    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun loadContacts(): List<DeviceContact> = withContext(Dispatchers.IO) {
        if (!hasContactsPermission()) return@withContext emptyList()

        val result = mutableListOf<DeviceContact>()
        val seenKeys = HashSet<String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL
        )

        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val starIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
                val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)

                while (cursor.moveToNext()) {
                    val number = if (numIdx >= 0) cursor.getString(numIdx).orEmpty().trim() else ""
                    if (number.isBlank()) continue
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else number
                    val norm = RecordingRepository.normalizePhoneNumber(number)
                    val dedupKey = "$name|$norm"
                    if (!seenKeys.add(dedupKey)) continue

                    val typeInt = if (typeIdx >= 0) cursor.getInt(typeIdx) else 0
                    val customLabel = if (labelIdx >= 0) cursor.getString(labelIdx) else null
                    val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources,
                        typeInt,
                        customLabel
                    ).toString()

                    result += DeviceContact(
                        id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L,
                        name = name.ifBlank { number },
                        number = number,
                        isStarred = if (starIdx >= 0) cursor.getInt(starIdx) == 1 else false,
                        label = typeLabel
                    )
                }
            }
        }
        result
    }
}

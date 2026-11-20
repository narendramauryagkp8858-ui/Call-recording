package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.printerDataStore by preferencesDataStore(name = "narendra_printer_prefs")

data class PrinterConfig(
    val deviceName: String = "",
    val deviceAddress: String = "",
    val paperWidthMm: Int = 58
)

class PrinterPreferences(private val context: Context) {
    private val keyName = stringPreferencesKey("printer_name")
    private val keyAddress = stringPreferencesKey("printer_address")
    private val keyPaperMm = intPreferencesKey("paper_width_mm")

    val configFlow: Flow<PrinterConfig> = context.printerDataStore.data.map { prefs ->
        PrinterConfig(
            deviceName = prefs[keyName].orEmpty(),
            deviceAddress = prefs[keyAddress].orEmpty(),
            paperWidthMm = prefs[keyPaperMm] ?: 58
        )
    }

    suspend fun setPrinter(name: String, address: String) {
        context.printerDataStore.edit { prefs ->
            prefs[keyName] = name
            prefs[keyAddress] = address
        }
    }

    suspend fun setPaperWidth(widthMm: Int) {
        context.printerDataStore.edit { prefs ->
            prefs[keyPaperMm] = if (widthMm >= 80) 80 else 58
        }
    }
}

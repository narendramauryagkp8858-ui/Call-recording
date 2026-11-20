package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "narendra_call_recorder_settings"
)

data class AppSettings(
    val autoRecordEnabled: Boolean = false,
    val autoRecordIncoming: Boolean = true,
    val autoRecordOutgoing: Boolean = true,
    val filterMode: AutoRecordFilterMode = AutoRecordFilterMode.ALL_CALLS,
    val audioFormat: AudioFormatOption = AudioFormatOption.M4A,
    val audioQuality: AudioQualityOption = AudioQualityOption.HIGH,
    val preferTwoWayAttempt: Boolean = true,
    val darkModeEnabled: Boolean = true,
    val showRecordingNotification: Boolean = true,
    val showSavedNotification: Boolean = true,
    val pinLockEnabled: Boolean = false,
    val pinHash: String = "",
    val biometricLockEnabled: Boolean = false,
    val consentAccepted: Boolean = false,
    val lastPendingCallDirection: CallDirection? = null,
    val lastPendingNumber: String = ""
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val AUTO_RECORD_ENABLED = booleanPreferencesKey("auto_record_enabled")
        val AUTO_RECORD_INCOMING = booleanPreferencesKey("auto_record_incoming")
        val AUTO_RECORD_OUTGOING = booleanPreferencesKey("auto_record_outgoing")
        val FILTER_MODE = stringPreferencesKey("filter_mode")
        val AUDIO_FORMAT = stringPreferencesKey("audio_format")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val PREFER_TWO_WAY = booleanPreferencesKey("prefer_two_way")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val SHOW_REC_NOTIF = booleanPreferencesKey("show_rec_notif")
        val SHOW_SAVED_NOTIF = booleanPreferencesKey("show_saved_notif")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val CONSENT_ACCEPTED = booleanPreferencesKey("consent_accepted")
        val PENDING_DIRECTION = stringPreferencesKey("pending_direction")
        val PENDING_NUMBER = stringPreferencesKey("pending_number")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            autoRecordEnabled = prefs[Keys.AUTO_RECORD_ENABLED] ?: false,
            autoRecordIncoming = prefs[Keys.AUTO_RECORD_INCOMING] ?: true,
            autoRecordOutgoing = prefs[Keys.AUTO_RECORD_OUTGOING] ?: true,
            filterMode = prefs[Keys.FILTER_MODE]?.let {
                runCatching { AutoRecordFilterMode.valueOf(it) }.getOrNull()
            } ?: AutoRecordFilterMode.ALL_CALLS,
            audioFormat = prefs[Keys.AUDIO_FORMAT]?.let {
                runCatching { AudioFormatOption.valueOf(it) }.getOrNull()
            } ?: AudioFormatOption.M4A,
            audioQuality = prefs[Keys.AUDIO_QUALITY]?.let {
                runCatching { AudioQualityOption.valueOf(it) }.getOrNull()
            } ?: AudioQualityOption.HIGH,
            preferTwoWayAttempt = prefs[Keys.PREFER_TWO_WAY] ?: true,
            darkModeEnabled = prefs[Keys.DARK_MODE] ?: true,
            showRecordingNotification = prefs[Keys.SHOW_REC_NOTIF] ?: true,
            showSavedNotification = prefs[Keys.SHOW_SAVED_NOTIF] ?: true,
            pinLockEnabled = prefs[Keys.PIN_ENABLED] ?: false,
            pinHash = prefs[Keys.PIN_HASH] ?: "",
            biometricLockEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            consentAccepted = prefs[Keys.CONSENT_ACCEPTED] ?: false,
            lastPendingCallDirection = prefs[Keys.PENDING_DIRECTION]?.takeIf { it.isNotEmpty() }?.let {
                runCatching { CallDirection.valueOf(it) }.getOrNull()
            },
            lastPendingNumber = prefs[Keys.PENDING_NUMBER] ?: ""
        )
    }

    suspend fun getSettingsOnce(): AppSettings = settingsFlow.first()

    suspend fun setAutoRecordEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_RECORD_ENABLED] = enabled }
    }

    suspend fun setAutoRecordIncoming(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_RECORD_INCOMING] = enabled }
    }

    suspend fun setAutoRecordOutgoing(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_RECORD_OUTGOING] = enabled }
    }

    suspend fun setFilterMode(mode: AutoRecordFilterMode) {
        context.settingsDataStore.edit { it[Keys.FILTER_MODE] = mode.name }
    }

    suspend fun setAudioFormat(format: AudioFormatOption) {
        context.settingsDataStore.edit { it[Keys.AUDIO_FORMAT] = format.name }
    }

    suspend fun setAudioQuality(quality: AudioQualityOption) {
        context.settingsDataStore.edit { it[Keys.AUDIO_QUALITY] = quality.name }
    }

    suspend fun setPreferTwoWayAttempt(prefer: Boolean) {
        context.settingsDataStore.edit { it[Keys.PREFER_TWO_WAY] = prefer }
    }

    suspend fun setDarkModeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setShowRecordingNotification(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_REC_NOTIF] = enabled }
    }

    suspend fun setShowSavedNotification(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_SAVED_NOTIF] = enabled }
    }

    suspend fun setPinLock(enabled: Boolean, pin: String? = null) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.PIN_ENABLED] = enabled
            if (!enabled) {
                prefs[Keys.PIN_HASH] = ""
            } else if (pin != null) {
                prefs[Keys.PIN_HASH] = hashPin(pin)
            }
        }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setConsentAccepted(accepted: Boolean) {
        context.settingsDataStore.edit { it[Keys.CONSENT_ACCEPTED] = accepted }
    }

    suspend fun setPendingCallInfo(direction: CallDirection?, number: String) {
        context.settingsDataStore.edit {
            it[Keys.PENDING_DIRECTION] = direction?.name ?: ""
            it[Keys.PENDING_NUMBER] = number
        }
    }

    fun verifyPin(rawPin: String, storedHash: String): Boolean {
        if (storedHash.isBlank()) return false
        return hashPin(rawPin) == storedHash
    }

    companion object {
        fun hashPin(pin: String): String {
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest(("narendra_cr_salt:$pin").toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

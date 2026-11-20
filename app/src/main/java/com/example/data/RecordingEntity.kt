package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class CallDirection(val label: String) {
    INCOMING("Incoming"),
    OUTGOING("Outgoing"),
    MANUAL("Manual")
}

enum class AudioFormatOption(
    val extension: String,
    val mimeType: String,
    val displayName: String,
    val subtitle: String
) {
    M4A("m4a", "audio/mp4", "M4A", "MPEG-4 AAC · Compact & Universal"),
    AAC("aac", "audio/aac", "AAC", "ADTS Raw AAC · Efficient Voice"),
    WAV("wav", "audio/wav", "WAV", "16-bit PCM · Uncompressed Studio")
}

enum class AudioQualityOption(
    val displayName: String,
    val sampleRateHz: Int,
    val bitRateBps: Int,
    val subtitle: String
) {
    STANDARD("Standard", 16000, 64000, "16 kHz · 64 kbps · Low storage"),
    HIGH("High", 44100, 128000, "44.1 kHz · 128 kbps · Balanced"),
    STUDIO("Studio", 48000, 192000, "48 kHz · 192 kbps · Maximum clarity")
}

enum class AudioSourceMode(val displayName: String) {
    VOICE_CALL_SYSTEM("Two-Way Call Line"),
    VOICE_COMMUNICATION("Voice Communication Mic"),
    MICROPHONE("Device Microphone")
}

enum class AutoRecordFilterMode(val displayName: String, val description: String) {
    ALL_CALLS("Record All Calls", "Attempt recording for all incoming & outgoing calls"),
    SELECTED_ONLY("Selected Contacts Only", "Only record calls matching your Include list"),
    EXCLUDE_SELECTED("Exclude Selected Contacts", "Record all calls except contacts on your Exclude list")
}

enum class ContactRuleType {
    INCLUDE,
    EXCLUDE
}

enum class SortOption(val label: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    DURATION_DESC("Longest Duration"),
    CONTACT_ASC("Contact A–Z")
}

data class CompatibilityStatus(
    val androidVersionName: String,
    val apiLevel: Int,
    val hasRecordAudioPermission: Boolean,
    val hasPhoneStatePermission: Boolean,
    val hasCallLogPermission: Boolean,
    val hasContactsPermission: Boolean,
    val hasNotificationPermission: Boolean,
    val hasPrivilegedCaptureAudioOutput: Boolean,
    val isTwoWaySupported: Boolean,
    val recommendedAudioSource: AudioSourceMode,
    val compatibilityTitle: String,
    val compatibilitySummary: String,
    val backgroundRestrictionNote: String
)

@Serializable
@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val phoneNumber: String,
    val contactName: String,
    val callDirection: CallDirection,
    val filePath: String,
    val format: AudioFormatOption,
    val sampleRateHz: Int,
    val bitRateBps: Int,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val createdAtMillis: Long,
    val audioSourceMode: AudioSourceMode,
    val isTwoWayVerified: Boolean
)

@Serializable
@Entity(tableName = "contact_rules")
data class ContactRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val displayName: String,
    val ruleType: ContactRuleType,
    val addedAtMillis: Long = System.currentTimeMillis()
)

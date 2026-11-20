package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class RecordingTypeConverters {
    @TypeConverter
    fun fromCallDirection(value: CallDirection): String = value.name

    @TypeConverter
    fun toCallDirection(value: String): CallDirection =
        runCatching { CallDirection.valueOf(value) }.getOrDefault(CallDirection.MANUAL)

    @TypeConverter
    fun fromAudioFormat(value: AudioFormatOption): String = value.name

    @TypeConverter
    fun toAudioFormat(value: String): AudioFormatOption =
        runCatching { AudioFormatOption.valueOf(value) }.getOrDefault(AudioFormatOption.M4A)

    @TypeConverter
    fun fromAudioSourceMode(value: AudioSourceMode): String = value.name

    @TypeConverter
    fun toAudioSourceMode(value: String): AudioSourceMode =
        runCatching { AudioSourceMode.valueOf(value) }.getOrDefault(AudioSourceMode.MICROPHONE)

    @TypeConverter
    fun fromContactRuleType(value: ContactRuleType): String = value.name

    @TypeConverter
    fun toContactRuleType(value: String): ContactRuleType =
        runCatching { ContactRuleType.valueOf(value) }.getOrDefault(ContactRuleType.INCLUDE)
}

@Database(
    entities = [RecordingEntity::class, ContactRuleEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(RecordingTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun contactRuleDao(): ContactRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "narendra_call_recorder.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

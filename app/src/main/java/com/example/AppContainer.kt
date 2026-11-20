package com.example

import android.content.Context
import com.example.audio.AudioPlayerManager
import com.example.audio.RecordingManager
import com.example.call.CallStateManager
import com.example.data.AppDatabase
import com.example.data.BackupManager
import com.example.data.RecordingRepository
import com.example.data.SettingsDataStore

class AppContainer private constructor(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(appContext) }
    val repository: RecordingRepository by lazy {
        RecordingRepository(
            context = appContext,
            recordingDao = database.recordingDao(),
            contactRuleDao = database.contactRuleDao()
        )
    }
    val recordingManager: RecordingManager by lazy { RecordingManager(appContext) }
    val audioPlayerManager: AudioPlayerManager by lazy { AudioPlayerManager() }
    val backupManager: BackupManager by lazy { BackupManager(appContext, repository) }
    val callStateManager: CallStateManager by lazy {
        CallStateManager(
            context = appContext,
            repository = repository,
            settingsDataStore = settingsDataStore
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: AppContainer? = null

        fun get(context: Context): AppContainer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppContainer(context).also { INSTANCE = it }
            }
        }
    }
}

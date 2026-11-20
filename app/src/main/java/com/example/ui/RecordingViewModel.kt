package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.R
import com.example.audio.ActiveRecordingSession
import com.example.audio.PlaybackState
import com.example.audio.RecordingManager
import com.example.call.CallMonitorStatus
import com.example.data.AppSettings
import com.example.data.AudioFormatOption
import com.example.data.AudioQualityOption
import com.example.data.AutoRecordFilterMode
import com.example.data.CallDirection
import com.example.data.CompatibilityStatus
import com.example.data.ContactRuleEntity
import com.example.data.ContactRuleType
import com.example.data.RecordingEntity
import com.example.data.SortOption
import com.example.service.RecordingService
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardStats(
    val totalRecordings: Int = 0,
    val todaysRecordings: Int = 0,
    val incomingCalls: Int = 0,
    val outgoingCalls: Int = 0,
    val manualRecordings: Int = 0,
    val storageUsedBytes: Long = 0L,
    val recentRecordings: List<RecordingEntity> = emptyList()
)

class RecordingViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer.get(application)
    private val repository = container.repository
    private val settingsStore = container.settingsDataStore
    private val recordingManager = container.recordingManager
    private val playerManager = container.audioPlayerManager
    private val backupManager = container.backupManager
    private val callStateManager = container.callStateManager

    val settings: StateFlow<AppSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val allRecordings: StateFlow<List<RecordingEntity>> = repository.observeRecordings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contactRules: StateFlow<List<ContactRuleEntity>> = repository.observeContactRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSession: StateFlow<ActiveRecordingSession> = recordingManager.session
    val playbackState: StateFlow<PlaybackState> = playerManager.state
    val callMonitorStatus: StateFlow<CallMonitorStatus> = callStateManager.monitorStatus

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.DATE_DESC)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _directionFilter = MutableStateFlow<CallDirection?>(null)
    val directionFilter: StateFlow<CallDirection?> = _directionFilter.asStateFlow()

    private val _compatibility = MutableStateFlow(RecordingManager.detectCompatibility(application))
    val compatibility: StateFlow<CompatibilityStatus> = _compatibility.asStateFlow()

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private val _isBackupBusy = MutableStateFlow(false)
    val isBackupBusy: StateFlow<Boolean> = _isBackupBusy.asStateFlow()

    val dashboardStats: StateFlow<DashboardStats> = allRecordings.map { list ->
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        DashboardStats(
            totalRecordings = list.size,
            todaysRecordings = list.count { it.createdAtMillis >= startOfDay },
            incomingCalls = list.count { it.callDirection == CallDirection.INCOMING },
            outgoingCalls = list.count { it.callDirection == CallDirection.OUTGOING },
            manualRecordings = list.count { it.callDirection == CallDirection.MANUAL },
            storageUsedBytes = repository.calculateTotalStorageBytes().coerceAtLeast(
                list.sumOf { it.fileSizeBytes }
            ),
            recentRecordings = list.take(5)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    val filteredRecordings: StateFlow<List<RecordingEntity>> = combine(
        allRecordings,
        _searchQuery,
        _sortOption,
        _directionFilter
    ) { list, query, sort, direction ->
        val trimmed = query.trim().lowercase()
        val filtered = list.filter { rec ->
            val matchesDirection = direction == null || rec.callDirection == direction
            val matchesQuery = trimmed.isEmpty() ||
                rec.title.lowercase().contains(trimmed) ||
                rec.contactName.lowercase().contains(trimmed) ||
                rec.phoneNumber.lowercase().contains(trimmed) ||
                rec.format.displayName.lowercase().contains(trimmed)
            matchesDirection && matchesQuery
        }

        when (sort) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.createdAtMillis }
            SortOption.DATE_ASC -> filtered.sortedBy { it.createdAtMillis }
            SortOption.DURATION_DESC -> filtered.sortedByDescending { it.durationMs }
            SortOption.CONTACT_ASC -> filtered.sortedBy {
                it.contactName.ifBlank { it.title }.lowercase()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val initialSettings = settingsStore.settingsFlow.first()
            if (initialSettings.pinLockEnabled || initialSettings.biometricLockEnabled) {
                _isAppLocked.value = true
            }
            if (initialSettings.autoRecordEnabled) {
                callStateManager.registerTelephonyObserver()
            }
        }

        viewModelScope.launch {
            RecordingService.lastServiceError.collect { err ->
                if (!err.isNullOrBlank()) {
                    _uiMessage.value = err
                    RecordingService.clearServiceError()
                }
            }
        }
    }

    fun refreshCompatibility() {
        val status = RecordingManager.detectCompatibility(getApplication())
        _compatibility.value = status
        if (status.hasPhoneStatePermission && settings.value.autoRecordEnabled) {
            callStateManager.registerTelephonyObserver()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun updateDirectionFilter(direction: CallDirection?) {
        _directionFilter.value = direction
    }

    fun startManualRecording() {
        val compat = RecordingManager.detectCompatibility(getApplication())
        _compatibility.value = compat
        if (!compat.hasRecordAudioPermission) {
            _uiMessage.value = "Microphone permission is required to record audio."
            return
        }
        RecordingService.startManualRecording(getApplication())
    }

    fun pauseRecording() {
        RecordingService.pauseRecording(getApplication())
    }

    fun resumeRecording() {
        RecordingService.resumeRecording(getApplication())
    }

    fun stopRecording() {
        RecordingService.stopRecording(getApplication())
    }

    fun playOrToggle(recording: RecordingEntity) {
        playerManager.playOrToggle(recording, viewModelScope)
    }

    fun seekPlayback(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun stopPlayback() {
        playerManager.stop()
    }

    fun renameRecording(id: Long, newTitle: String) {
        viewModelScope.launch {
            repository.renameRecording(id, newTitle)
            _uiMessage.value = "Recording renamed"
        }
    }

    fun deleteRecording(recording: RecordingEntity) {
        viewModelScope.launch {
            if (playbackState.value.activeRecordingId == recording.id) {
                playerManager.stop()
            }
            repository.deleteRecording(recording)
            _uiMessage.value = "Recording deleted"
        }
    }

    fun deleteAllRecordings() {
        viewModelScope.launch {
            playerManager.stop()
            repository.deleteAllRecordings()
            _uiMessage.value = "All recordings deleted from local storage"
        }
    }

    fun buildShareIntent(recording: RecordingEntity): Intent? {
        val ctx = getApplication<Application>()
        val file = File(recording.filePath)
        if (!file.exists()) {
            _uiMessage.value = "Recording file is missing on disk."
            return null
        }
        val uri = FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = recording.format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, recording.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, ctx.getString(R.string.share_recording_title))
    }

    fun setAutoRecordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setAutoRecordEnabled(enabled)
            if (enabled) {
                callStateManager.registerTelephonyObserver()
            } else {
                callStateManager.unregisterTelephonyObserver()
            }
        }
    }

    fun setAutoRecordIncoming(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoRecordIncoming(enabled) }
    }

    fun setAutoRecordOutgoing(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoRecordOutgoing(enabled) }
    }

    fun setFilterMode(mode: AutoRecordFilterMode) {
        viewModelScope.launch { settingsStore.setFilterMode(mode) }
    }

    fun addContactRule(phoneNumber: String, displayName: String, ruleType: ContactRuleType) {
        viewModelScope.launch {
            repository.addContactRule(phoneNumber, displayName, ruleType)
            _uiMessage.value = "Contact rule saved"
        }
    }

    fun deleteContactRule(id: Long) {
        viewModelScope.launch { repository.deleteContactRule(id) }
    }

    fun setAudioFormat(format: AudioFormatOption) {
        viewModelScope.launch { settingsStore.setAudioFormat(format) }
    }

    fun setAudioQuality(quality: AudioQualityOption) {
        viewModelScope.launch { settingsStore.setAudioQuality(quality) }
    }

    fun setPreferTwoWayAttempt(prefer: Boolean) {
        viewModelScope.launch { settingsStore.setPreferTwoWayAttempt(prefer) }
    }

    fun setDarkModeEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDarkModeEnabled(enabled) }
    }

    fun setShowRecordingNotification(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setShowRecordingNotification(enabled) }
    }

    fun setShowSavedNotification(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setShowSavedNotification(enabled) }
    }

    fun enablePinLock(pin: String) {
        viewModelScope.launch {
            settingsStore.setPinLock(enabled = true, pin = pin)
            _uiMessage.value = "PIN lock enabled"
        }
    }

    fun disablePinLock() {
        viewModelScope.launch {
            settingsStore.setPinLock(enabled = false)
            if (!settings.value.biometricLockEnabled) {
                _isAppLocked.value = false
            }
            _uiMessage.value = "PIN lock disabled"
        }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setBiometricLockEnabled(enabled)
            _uiMessage.value = if (enabled) "Fingerprint lock enabled" else "Fingerprint lock disabled"
        }
    }

    fun verifyPin(rawPin: String): Boolean {
        val ok = settingsStore.verifyPin(rawPin, settings.value.pinHash)
        if (ok) {
            _isAppLocked.value = false
        }
        return ok
    }

    fun unlockWithBiometrics() {
        _isAppLocked.value = false
    }

    fun lockNowIfConfigured() {
        val s = settings.value
        if (s.pinLockEnabled || s.biometricLockEnabled) {
            _isAppLocked.value = true
        }
    }

    fun acceptConsent() {
        viewModelScope.launch {
            settingsStore.setConsentAccepted(true)
        }
    }

    fun exportBackup(destinationUri: Uri) {
        viewModelScope.launch {
            _isBackupBusy.value = true
            val result = backupManager.exportBackupToUri(destinationUri)
            _isBackupBusy.value = false
            result.onSuccess {
                _uiMessage.value = it.message
            }.onFailure {
                _uiMessage.value = "Backup failed: ${it.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun restoreBackup(sourceUri: Uri) {
        viewModelScope.launch {
            _isBackupBusy.value = true
            val result = backupManager.restoreBackupFromUri(sourceUri)
            _isBackupBusy.value = false
            result.onSuccess {
                _uiMessage.value = it.message
            }.onFailure {
                _uiMessage.value = "Restore failed: ${it.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun consumeMessage() {
        _uiMessage.value = null
        playerManager.clearError()
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.stop()
    }
}

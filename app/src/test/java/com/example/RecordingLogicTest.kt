package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.RecordingManager
import com.example.audio.WavAudioRecorder
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.AudioFormatOption
import com.example.data.AudioSourceMode
import com.example.data.AutoRecordFilterMode
import com.example.data.CallDirection
import com.example.data.ContactRuleType
import com.example.data.RecordingEntity
import com.example.data.RecordingRepository
import com.example.data.SettingsDataStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordingLogicTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: RecordingRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RecordingRepository(
            context = context,
            recordingDao = db.recordingDao(),
            contactRuleDao = db.contactRuleDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `wav header generates valid RIFF WAVE 44-byte header`() {
        val header = WavAudioRecorder.createWavHeader(
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            dataLength = 1000L
        )
        assertEquals(44, header.size)
        assertEquals("RIFF", String(header.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(header.copyOfRange(8, 12), Charsets.US_ASCII))
        assertEquals("fmt ", String(header.copyOfRange(12, 16), Charsets.US_ASCII))
        assertEquals("data", String(header.copyOfRange(36, 40), Charsets.US_ASCII))
    }

    @Test
    fun `compatibility check does not falsely claim two-way support without CAPTURE_AUDIO_OUTPUT`() {
        val compat = RecordingManager.detectCompatibility(context)
        assertFalse(compat.hasPrivilegedCaptureAudioOutput)
        assertFalse(compat.isTwoWaySupported)
        assertEquals(AudioSourceMode.VOICE_COMMUNICATION, compat.recommendedAudioSource)
    }

    @Test
    fun `phone number matching handles country code differences`() {
        assertTrue(RecordingRepository.numbersMatch("+91 98765 43210", "09876543210"))
        assertFalse(RecordingRepository.numbersMatch("9876543210", "1112223333"))
    }

    @Test
    fun `shouldRecordCall respects include and exclude contact rules`() = runTest {
        repository.addContactRule("+1 555 123 4567", "Alice", ContactRuleType.INCLUDE)
        repository.addContactRule("+1 555 999 0000", "Spam", ContactRuleType.EXCLUDE)

        val includeSettings = AppSettings(
            autoRecordEnabled = true,
            filterMode = AutoRecordFilterMode.SELECTED_ONLY
        )
        assertTrue(repository.shouldRecordCall(CallDirection.INCOMING, "5551234567", includeSettings))
        assertFalse(repository.shouldRecordCall(CallDirection.INCOMING, "5550001111", includeSettings))

        val excludeSettings = AppSettings(
            autoRecordEnabled = true,
            filterMode = AutoRecordFilterMode.EXCLUDE_SELECTED
        )
        assertFalse(repository.shouldRecordCall(CallDirection.OUTGOING, "5559990000", excludeSettings))
        assertTrue(repository.shouldRecordCall(CallDirection.OUTGOING, "5552223333", excludeSettings))
    }

    @Test
    fun `pin hash verification validates matching PIN only`() {
        val store = SettingsDataStore(context)
        val hash = SettingsDataStore.hashPin("2580")
        assertTrue(store.verifyPin("2580", hash))
        assertFalse(store.verifyPin("1234", hash))
    }

    @Test
    fun `repository inserts and renames recording in Room`() = runTest {
        val id = repository.insertRecording(
            RecordingEntity(
                title = "Initial Call",
                phoneNumber = "9876543210",
                contactName = "Narendra",
                callDirection = CallDirection.INCOMING,
                filePath = "/tmp/test.m4a",
                format = AudioFormatOption.M4A,
                sampleRateHz = 44100,
                bitRateBps = 128000,
                durationMs = 15000L,
                fileSizeBytes = 240000L,
                createdAtMillis = 1700000000000L,
                audioSourceMode = AudioSourceMode.VOICE_COMMUNICATION,
                isTwoWayVerified = false
            )
        )
        repository.renameRecording(id, "Important Client Call")
        val all = repository.getAllRecordingsOnce()
        assertEquals(1, all.size)
        assertEquals("Important Client Call", all.first().title)
    }
}

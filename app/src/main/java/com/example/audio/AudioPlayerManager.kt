package com.example.audio

import android.media.MediaPlayer
import com.example.data.RecordingEntity
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackState(
    val activeRecordingId: Long? = null,
    val activeTitle: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

class AudioPlayerManager {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    fun playOrToggle(recording: RecordingEntity, scope: CoroutineScope) {
        val current = _state.value
        if (current.activeRecordingId == recording.id && mediaPlayer != null) {
            if (current.isPlaying) pause() else resume(scope)
            return
        }

        stop()

        val file = File(recording.filePath)
        if (!file.exists()) {
            _state.value = PlaybackState(
                errorMessage = "Audio file not found on device: ${file.name}"
            )
            return
        }

        runCatching {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    progressJob?.cancel()
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        positionMs = 0L
                    )
                    runCatching { seekTo(0) }
                }
                start()
            }
            mediaPlayer = player
            _state.value = PlaybackState(
                activeRecordingId = recording.id,
                activeTitle = recording.title,
                isPlaying = true,
                positionMs = 0L,
                durationMs = player.duration.toLong().coerceAtLeast(recording.durationMs),
                errorMessage = null
            )
            startProgressLoop(scope)
        }.onFailure { err ->
            stop()
            _state.value = PlaybackState(
                errorMessage = "Playback failed: ${err.localizedMessage ?: "Unsupported format"}"
            )
        }
    }

    fun pause() {
        val player = mediaPlayer ?: return
        runCatching {
            if (player.isPlaying) {
                player.pause()
            }
            progressJob?.cancel()
            _state.value = _state.value.copy(
                isPlaying = false,
                positionMs = player.currentPosition.toLong()
            )
        }
    }

    fun resume(scope: CoroutineScope) {
        val player = mediaPlayer ?: return
        runCatching {
            player.start()
            _state.value = _state.value.copy(isPlaying = true, errorMessage = null)
            startProgressLoop(scope)
        }
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        runCatching {
            val clamped = positionMs.toInt().coerceIn(0, player.duration.coerceAtLeast(0))
            player.seekTo(clamped)
            _state.value = _state.value.copy(positionMs = clamped.toLong())
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        _state.value = PlaybackState()
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private fun startProgressLoop(scope: CoroutineScope) {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    _state.value = _state.value.copy(
                        positionMs = player.currentPosition.toLong(),
                        durationMs = player.duration.toLong().coerceAtLeast(_state.value.durationMs)
                    )
                }
                delay(120L)
            }
        }
    }
}

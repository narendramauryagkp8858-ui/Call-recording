package com.example.data

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupManifest(
    val version: Int = 1,
    val exportedAtMillis: Long,
    val recordings: List<BackupRecordingItem>
)

@Serializable
data class BackupRecordingItem(
    val fileName: String,
    val title: String,
    val phoneNumber: String,
    val contactName: String,
    val callDirection: CallDirection,
    val format: AudioFormatOption,
    val sampleRateHz: Int,
    val bitRateBps: Int,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val createdAtMillis: Long,
    val audioSourceMode: AudioSourceMode,
    val isTwoWayVerified: Boolean
)

data class BackupResult(
    val count: Int,
    val message: String
)

class BackupManager(
    private val context: Context,
    private val repository: RecordingRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    suspend fun exportBackupToUri(destinationUri: Uri): Result<BackupResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val recordings = repository.getAllRecordingsOnce()
                if (recordings.isEmpty()) {
                    return@runCatching BackupResult(0, "No recordings available to back up.")
                }

                val backupItems = mutableListOf<BackupRecordingItem>()
                val outputStream = context.contentResolver.openOutputStream(destinationUri)
                    ?: error("Unable to open destination file for backup.")

                ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                    recordings.forEach { rec ->
                        val audioFile = File(rec.filePath)
                        if (audioFile.exists() && audioFile.isFile) {
                            val entryName = "audio/${audioFile.name}"
                            zipOut.putNextEntry(ZipEntry(entryName))
                            BufferedInputStream(FileInputStream(audioFile)).use { input ->
                                input.copyTo(zipOut)
                            }
                            zipOut.closeEntry()

                            backupItems += BackupRecordingItem(
                                fileName = audioFile.name,
                                title = rec.title,
                                phoneNumber = rec.phoneNumber,
                                contactName = rec.contactName,
                                callDirection = rec.callDirection,
                                format = rec.format,
                                sampleRateHz = rec.sampleRateHz,
                                bitRateBps = rec.bitRateBps,
                                durationMs = rec.durationMs,
                                fileSizeBytes = audioFile.length(),
                                createdAtMillis = rec.createdAtMillis,
                                audioSourceMode = rec.audioSourceMode,
                                isTwoWayVerified = rec.isTwoWayVerified
                            )
                        }
                    }

                    val manifest = BackupManifest(
                        exportedAtMillis = System.currentTimeMillis(),
                        recordings = backupItems
                    )
                    zipOut.putNextEntry(ZipEntry("manifest.json"))
                    zipOut.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                }

                BackupResult(
                    count = backupItems.size,
                    message = "Backed up ${backupItems.size} recording(s) to selected archive."
                )
            }
        }

    suspend fun restoreBackupFromUri(sourceUri: Uri): Result<BackupResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                    ?: error("Unable to open selected backup archive.")

                var manifest: BackupManifest? = null
                val extractedFiles = mutableMapOf<String, File>()
                val tempDir = File(context.cacheDir, "restore_tmp_${System.currentTimeMillis()}").apply {
                    mkdirs()
                }

                try {
                    ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                        var entry: ZipEntry? = zipIn.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val safeName = File(entry.name).name
                                if (entry.name == "manifest.json") {
                                    val bytes = zipIn.readBytes()
                                    manifest = json.decodeFromString<BackupManifest>(
                                        bytes.toString(Charsets.UTF_8)
                                    )
                                } else if (entry.name.startsWith("audio/") && safeName.isNotEmpty()) {
                                    val tempFile = File(tempDir, safeName)
                                    FileOutputStream(tempFile).use { out ->
                                        zipIn.copyTo(out)
                                    }
                                    extractedFiles[safeName] = tempFile
                                }
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }

                    val parsedManifest = manifest ?: error("Invalid backup archive: missing manifest.json")
                    val existingPaths = repository.getAllRecordingsOnce().map { File(it.filePath).name }.toSet()

                    var restoredCount = 0
                    parsedManifest.recordings.forEach { item ->
                        val tempAudio = extractedFiles[item.fileName] ?: return@forEach
                        val targetName = if (item.fileName in existingPaths) {
                            "restored_${System.currentTimeMillis()}_${item.fileName}"
                        } else {
                            item.fileName
                        }
                        val destFile = File(repository.recordingsDir, targetName)
                        tempAudio.copyTo(destFile, overwrite = true)

                        repository.insertRecording(
                            RecordingEntity(
                                title = item.title,
                                phoneNumber = item.phoneNumber,
                                contactName = item.contactName,
                                callDirection = item.callDirection,
                                filePath = destFile.absolutePath,
                                format = item.format,
                                sampleRateHz = item.sampleRateHz,
                                bitRateBps = item.bitRateBps,
                                durationMs = item.durationMs,
                                fileSizeBytes = destFile.length(),
                                createdAtMillis = item.createdAtMillis,
                                audioSourceMode = item.audioSourceMode,
                                isTwoWayVerified = item.isTwoWayVerified
                            )
                        )
                        restoredCount++
                    }

                    BackupResult(
                        count = restoredCount,
                        message = "Restored $restoredCount recording(s) from archive."
                    )
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }
}

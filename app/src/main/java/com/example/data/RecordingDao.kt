package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAtMillis DESC")
    fun observeAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY createdAtMillis DESC")
    suspend fun getAllRecordingsOnce(): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getRecordingById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE filePath = :path LIMIT 1")
    suspend fun getRecordingByFilePath(path: String): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Update
    suspend fun updateRecording(recording: RecordingEntity)

    @Query("UPDATE recordings SET title = :newTitle WHERE id = :id")
    suspend fun renameRecording(id: Long, newTitle: String)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Long)

    @Query("DELETE FROM recordings")
    suspend fun deleteAllRecordings()
}

@Dao
interface ContactRuleDao {
    @Query("SELECT * FROM contact_rules ORDER BY displayName ASC")
    fun observeAllRules(): Flow<List<ContactRuleEntity>>

    @Query("SELECT * FROM contact_rules")
    suspend fun getAllRulesOnce(): List<ContactRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: ContactRuleEntity): Long

    @Query("DELETE FROM contact_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)

    @Query("DELETE FROM contact_rules")
    suspend fun deleteAllRules()
}

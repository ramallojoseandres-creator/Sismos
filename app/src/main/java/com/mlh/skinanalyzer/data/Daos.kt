package com.mlh.skinanalyzer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Patient>>

    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getById(id: Long): Patient?

    @Query("SELECT * FROM patients WHERE name LIKE '%' || :q || '%' OR phone LIKE '%' || :q || '%' ORDER BY createdAt DESC")
    fun search(q: String): Flow<List<Patient>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(patient: Patient): Long

    @Update
    suspend fun update(patient: Patient)

    @Delete
    suspend fun delete(patient: Patient)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM analysis_sessions WHERE patientId = :patientId ORDER BY createdAt DESC")
    fun observeForPatient(patientId: Long): Flow<List<AnalysisSession>>

    @Query("SELECT * FROM analysis_sessions WHERE id = :id")
    suspend fun getById(id: Long): AnalysisSession?

    @Query("SELECT * FROM analysis_sessions ORDER BY createdAt DESC LIMIT 30")
    fun observeRecent(): Flow<List<AnalysisSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: AnalysisSession): Long

    @Delete
    suspend fun delete(session: AnalysisSession)
}

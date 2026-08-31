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
    @Query("SELECT * FROM patients ORDER BY lastName COLLATE NOCASE ASC, firstName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Patient>>

    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getById(id: Long): Patient?

    @Query("SELECT * FROM patients WHERE phone = :phone LIMIT 1")
    suspend fun findByPhone(phone: String): Patient?

    @Query(
        """
        SELECT * FROM patients
        WHERE firstName LIKE '%' || :q || '%'
           OR lastName LIKE '%' || :q || '%'
           OR phone LIKE '%' || :q || '%'
           OR phoneRaw LIKE '%' || :q || '%'
           OR address LIKE '%' || :q || '%'
        ORDER BY lastName COLLATE NOCASE ASC, firstName COLLATE NOCASE ASC
        """,
    )
    fun search(q: String): Flow<List<Patient>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(patient: Patient): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(patient: Patient): Long

    @Update
    suspend fun update(patient: Patient)

    @Delete
    suspend fun delete(patient: Patient)

    @Query("DELETE FROM analysis_sessions WHERE patientId = :patientId")
    suspend fun deleteSessionsForPatient(patientId: Long)

    @Query("SELECT COUNT(*) FROM analysis_sessions WHERE patientId = :patientId")
    suspend fun sessionCount(patientId: Long): Int

    @Query(
        """
        SELECT createdAt FROM analysis_sessions
        WHERE patientId = :patientId
        ORDER BY createdAt DESC LIMIT 1
        """,
    )
    suspend fun lastSessionAt(patientId: Long): Long?
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM analysis_sessions WHERE patientId = :patientId ORDER BY createdAt DESC")
    fun observeForPatient(patientId: Long): Flow<List<AnalysisSession>>

    @Query("SELECT * FROM analysis_sessions WHERE patientId = :patientId ORDER BY createdAt DESC")
    suspend fun listForPatient(patientId: Long): List<AnalysisSession>

    @Query("SELECT * FROM analysis_sessions WHERE id = :id")
    suspend fun getById(id: Long): AnalysisSession?

    @Query("SELECT * FROM analysis_sessions ORDER BY createdAt DESC LIMIT 40")
    fun observeRecent(): Flow<List<AnalysisSession>>

    @Query("SELECT * FROM analysis_sessions ORDER BY createdAt DESC LIMIT 40")
    suspend fun listRecent(): List<AnalysisSession>

    @Query("SELECT COUNT(*) FROM analysis_sessions WHERE patientId = :patientId")
    suspend fun countForPatient(patientId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: AnalysisSession): Long

    @Query("UPDATE analysis_sessions SET oemIndicatorsJson = :json WHERE id = :id")
    suspend fun updateOemIndicators(id: Long, json: String)

    @Query("UPDATE analysis_sessions SET editableRecommendations = :text WHERE id = :id")
    suspend fun updateEditableRecommendations(id: Long, text: String)

    @Delete
    suspend fun delete(session: AnalysisSession)
}

@Dao
interface PendingImportDao {
    @Query("SELECT * FROM pending_patient_imports ORDER BY receivedAt ASC")
    fun observeAll(): Flow<List<PendingPatientImport>>

    @Query("SELECT * FROM pending_patient_imports ORDER BY receivedAt ASC")
    suspend fun listAll(): List<PendingPatientImport>

    @Query("SELECT COUNT(*) FROM pending_patient_imports")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insertAll(items: List<PendingPatientImport>)

    @Query("DELETE FROM pending_patient_imports")
    suspend fun clear()

    @Delete
    suspend fun delete(item: PendingPatientImport)
}

@Dao
interface ClinicDao {
    @Query("SELECT * FROM clinic_profile WHERE id = 1 LIMIT 1")
    fun observe(): Flow<ClinicProfile?>

    @Query("SELECT * FROM clinic_profile WHERE id = 1 LIMIT 1")
    suspend fun get(): ClinicProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ClinicProfile)
}

@Dao
interface IndicatorPrefDao {
    @Query("SELECT * FROM indicator_prefs ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<IndicatorPref>>

    @Query("SELECT * FROM indicator_prefs ORDER BY sortOrder ASC")
    suspend fun listAll(): List<IndicatorPref>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<IndicatorPref>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: IndicatorPref)
}

@Dao
interface CareGuideDao {
    @Query("SELECT * FROM care_guides WHERE metricKey IN (:keys)")
    suspend fun forMetrics(keys: List<String>): List<CareGuide>

    @Query("SELECT * FROM care_guides ORDER BY id ASC")
    suspend fun listAll(): List<CareGuide>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CareGuide>)

    @Query("SELECT COUNT(*) FROM care_guides")
    suspend fun count(): Int
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE metricKey IN (:keys)")
    suspend fun forMetrics(keys: List<String>): List<ProductRec>

    @Query("SELECT * FROM products ORDER BY id ASC")
    suspend fun listAll(): List<ProductRec>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProductRec>)

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int
}

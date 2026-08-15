package com.mlh.skinanalyzer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Patient::class,
        AnalysisSession::class,
        ClinicProfile::class,
        IndicatorPref::class,
        CareGuide::class,
        ProductRec::class,
        PendingPatientImport::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun sessionDao(): SessionDao
    abstract fun clinicDao(): ClinicDao
    abstract fun indicatorPrefDao(): IndicatorPrefDao
    abstract fun careGuideDao(): CareGuideDao
    abstract fun productDao(): ProductDao
    abstract fun pendingImportDao(): PendingImportDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS patients_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        firstName TEXT NOT NULL,
                        lastName TEXT NOT NULL,
                        birthDate TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        phoneRaw TEXT NOT NULL,
                        sex TEXT NOT NULL,
                        address TEXT NOT NULL,
                        email TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        photoPath TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO patients_new (
                        id, firstName, lastName, birthDate, phone, phoneRaw, sex,
                        address, email, notes, photoPath, createdAt, updatedAt
                    )
                    SELECT
                        id,
                        CASE
                            WHEN instr(name, ' ') > 0 THEN substr(name, 1, instr(name, ' ') - 1)
                            ELSE name
                        END,
                        CASE
                            WHEN instr(name, ' ') > 0 THEN substr(name, instr(name, ' ') + 1)
                            ELSE ''
                        END,
                        date('now', '-' || CAST(age AS TEXT) || ' years'),
                        CASE
                            WHEN phone IS NULL OR trim(phone) = '' THEN 'legacy-' || CAST(id AS TEXT)
                            ELSE replace(replace(replace(replace(phone, ' ', ''), '-', ''), '(', ''), ')', '')
                        END,
                        COALESCE(phone, ''),
                        CASE
                            WHEN lower(gender) LIKE '%m%' AND lower(gender) NOT LIKE '%f%' THEN 'M'
                            ELSE 'F'
                        END,
                        '',
                        COALESCE(email, ''),
                        COALESCE(notes, ''),
                        COALESCE(photoPath, ''),
                        createdAt,
                        updatedAt
                    FROM patients
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE patients")
                db.execSQL("ALTER TABLE patients_new RENAME TO patients")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_patients_phone ON patients(phone)",
                )

                db.execSQL(
                    "ALTER TABLE analysis_sessions ADD COLUMN ageAtAnalysis INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE analysis_sessions ADD COLUMN editableRecommendations TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    """
                    UPDATE analysis_sessions
                    SET ageAtAnalysis = CASE
                        WHEN skinAge BETWEEN 1 AND 100 THEN skinAge
                        ELSE 30
                    END
                    WHERE ageAtAnalysis = 0
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_patient_imports (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        firstName TEXT NOT NULL,
                        lastName TEXT NOT NULL,
                        birthDate TEXT NOT NULL,
                        phoneRaw TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        sex TEXT NOT NULL,
                        address TEXT NOT NULL,
                        receivedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mlh_skin_analyzer.db",
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .addCallback(SeedCallback(context.applicationContext))
                    .build()
                    .also { instance = it }
            }
    }

    private class SeedCallback(private val appContext: Context) : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { LocalCatalog.seed(get(appContext)) }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { LocalCatalog.ensureSeeded(get(appContext)) }
            }
        }
    }
}

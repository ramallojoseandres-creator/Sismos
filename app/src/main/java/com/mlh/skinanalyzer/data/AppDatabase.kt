package com.mlh.skinanalyzer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun sessionDao(): SessionDao
    abstract fun clinicDao(): ClinicDao
    abstract fun indicatorPrefDao(): IndicatorPrefDao
    abstract fun careGuideDao(): CareGuideDao
    abstract fun productDao(): ProductDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mlh_skin_analyzer.db",
                )
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

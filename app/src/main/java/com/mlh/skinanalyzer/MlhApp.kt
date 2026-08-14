package com.mlh.skinanalyzer

import android.app.Application
import android.util.Log
import com.mlh.skinanalyzer.data.AppDatabase
import com.mlh.skinanalyzer.data.PatientDao
import com.mlh.skinanalyzer.data.SessionDao

class MlhApp : Application() {
    lateinit var db: AppDatabase
        private set
    val patients: PatientDao get() = db.patientDao()
    val sessions: SessionDao get() = db.sessionDao()

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = try {
            AppDatabase.get(this)
        } catch (e: Exception) {
            Log.e("MLH", "DB init failed", e)
            throw e
        }
    }

    companion object {
        @Volatile
        private var instance: MlhApp? = null

        fun get(): MlhApp =
            instance ?: error("MlhApp not initialized")
    }
}

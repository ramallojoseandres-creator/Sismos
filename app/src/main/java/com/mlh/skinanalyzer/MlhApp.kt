package com.mlh.skinanalyzer

import android.app.Application
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
        db = AppDatabase.get(this)
    }

    companion object {
        lateinit var instance: MlhApp
            private set
    }
}

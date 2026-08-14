package com.mlh.skinanalyzer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "patients")
data class Patient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val gender: String,
    val age: Int,
    val phone: String = "",
    val email: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "analysis_sessions")
data class AnalysisSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val skinType: String = "",
    val skinAge: Int = 0,
    val overview: String = "",
    val metricsJson: String = "",
    val imagePathsJson: String = "",
    val recommendations: String = "",
    val moisturePercent: Float? = null,
)

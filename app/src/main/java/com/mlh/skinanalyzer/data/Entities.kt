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
    val photoPath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
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
    /** Offline facial proportions (3 courts / 5 eyes) JSON. */
    val facialRatioJson: String = "",
    /** OEM native indicator overlays + scores JSON. */
    val oemIndicatorsJson: String = "",
    val sessionDir: String = "",
    val notes: String = "",
)

/** Clinic / consultorio profile — replaces OEM cloud “shop”. */
@Entity(tableName = "clinic_profile")
data class ClinicProfile(
    @PrimaryKey val id: Int = 1,
    val clinicName: String = "Consultorio Dra. María Laura Hernández",
    val doctorName: String = "Dra. María Laura Hernández",
    val specialty: String = "Médico Cirujano · Estética",
    val phone: String = "",
    val email: String = "",
    val whatsapp: String = "",
    val address: String = "",
    val footerNote: String = "Informe orientativo para consulta estética. No sustituye diagnóstico médico.",
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Which of the 14 indicators appear in reports (OEM SetIndicatorSet). */
@Entity(tableName = "indicator_prefs")
data class IndicatorPref(
    @PrimaryKey val key: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)

/** Local care articles — replaces OEM cloud suggestions (app/article/list). */
@Entity(tableName = "care_guides")
data class CareGuide(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metricKey: String,
    val title: String,
    val body: String,
    val layer: String = "superficial",
)

/** Local product catalog — replaces OEM cloud product recommendations. */
@Entity(tableName = "products")
data class ProductRec(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metricKey: String,
    val name: String,
    val category: String,
    val description: String,
    val howToUse: String = "",
)

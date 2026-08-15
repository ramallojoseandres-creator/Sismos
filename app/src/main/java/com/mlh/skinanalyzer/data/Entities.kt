package com.mlh.skinanalyzer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "patients",
    indices = [Index(value = ["phone"], unique = true)],
)
data class Patient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstName: String,
    val lastName: String,
    /** Fecha de nacimiento ISO `yyyy-MM-dd` — nunca se almacena la edad. */
    val birthDate: String,
    /** Teléfono normalizado (clave de deduplicación). */
    val phone: String,
    /** Teléfono tal como lo escribió el usuario. */
    val phoneRaw: String = "",
    /** `M` o `F` — el motor lo usa. */
    val sex: String = PatientSex.F.code,
    val address: String = "",
    val email: String = "",
    val notes: String = "",
    val photoPath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val displayName: String
        get() = listOf(lastName.trim(), firstName.trim())
            .filter { it.isNotEmpty() }
            .joinToString(", ")
            .ifBlank { firstName.ifBlank { lastName } }

    val fullName: String
        get() = listOf(firstName.trim(), lastName.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    val sexLabel: String get() = PatientSex.fromCode(sex).label

    fun currentAge(): Int = PatientAge.yearsAt(birthDate)

    fun ageAt(millis: Long): Int = PatientAge.yearsAt(birthDate, millis)

    /** Compatibilidad con pantallas/informes que esperaban `name` / `gender` / `age`. */
    @Deprecated("Use displayName / fullName", ReplaceWith("displayName"))
    val name: String get() = displayName

    @Deprecated("Use sex / sexLabel", ReplaceWith("sexLabel"))
    val gender: String get() = sexLabel

    @Deprecated("Use currentAge() — age is never stored", ReplaceWith("currentAge()"))
    val age: Int get() = currentAge()
}

@Entity(tableName = "analysis_sessions")
data class AnalysisSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val skinType: String = "",
    val skinAge: Int = 0,
    /** Edad cronológica del paciente el día de la captura (congelada). */
    val ageAtAnalysis: Int = 0,
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
    /** Recomendaciones editables por la médico antes de exportar. */
    val editableRecommendations: String = "",
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
    val footerNote: String = "Análisis cosmético de piel. No constituye diagnóstico médico. Cualquier lesión sospechosa requiere evaluación dermatológica presencial.",
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

/** Cola WiFi de fichas recibidas, aún no confirmadas en Room. */
@Entity(tableName = "pending_patient_imports")
data class PendingPatientImport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstName: String,
    val lastName: String,
    val birthDate: String,
    val phoneRaw: String,
    val phone: String,
    val sex: String,
    val address: String = "",
    val receivedAt: Long = System.currentTimeMillis(),
)

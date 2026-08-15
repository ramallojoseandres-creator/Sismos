package com.mlh.skinanalyzer.data

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeParseException

object PatientAge {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Edad en [atMillis] a partir de fecha ISO `yyyy-MM-dd`. */
    fun yearsAt(birthDateIso: String, atMillis: Long = System.currentTimeMillis()): Int {
        val birth = parseBirthDate(birthDateIso) ?: return 0
        val at = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
        if (at.isBefore(birth)) return 0
        return Period.between(birth, at).years.coerceAtLeast(0)
    }

    fun parseBirthDate(iso: String): LocalDate? =
        try {
            LocalDate.parse(iso.trim())
        } catch (_: DateTimeParseException) {
            null
        }

    fun isValidBirthDate(iso: String): Boolean {
        val d = parseBirthDate(iso) ?: return false
        return !d.isAfter(LocalDate.now(zone))
    }

    /** Aproxima DOB desde edad entera legacy (1 de enero de ese año). */
    fun approximateBirthDateFromAge(age: Int, nowMillis: Long = System.currentTimeMillis()): String {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val year = (today.year - age.coerceIn(0, 120)).coerceAtLeast(1900)
        return LocalDate.of(year, 1, 1).toString()
    }
}

object PhoneNormalizer {
    /** Quita espacios, guiones y paréntesis; conserva dígitos y un '+' inicial. */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val digits = buildString {
            trimmed.forEach { c ->
                when {
                    c.isDigit() -> append(c)
                    c == '+' && isEmpty() -> append(c)
                }
            }
        }
        return digits
    }
}

enum class PatientSex(val code: String, val label: String) {
    F("F", "Femenino"),
    M("M", "Masculino"),
    ;

    companion object {
        fun fromCode(code: String): PatientSex =
            entries.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
                ?: when {
                    code.contains("masc", ignoreCase = true) ||
                        code.equals("hombre", ignoreCase = true) ||
                        code.equals("m", ignoreCase = true) -> M
                    else -> F
                }

        fun fromLabel(label: String): PatientSex = fromCode(label)
    }
}

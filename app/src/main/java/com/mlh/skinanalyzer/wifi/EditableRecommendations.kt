package com.mlh.skinanalyzer.wifi

/**
 * Recomendaciones editadas por la médico desde el PC (medicamentos, rutinas, observaciones).
 * Se guardan en [com.mlh.skinanalyzer.data.AnalysisSession.editableRecommendations].
 */
data class EditableRecommendations(
    val medicamentos: String = "",
    val rutinas: String = "",
    val observaciones: String = "",
) {
    fun isBlank(): Boolean = medicamentos.isBlank() && rutinas.isBlank() && observaciones.isBlank()

    fun toStorageText(): String = buildString {
        if (medicamentos.isNotBlank()) {
            append(MARK_MED)
            append('\n')
            append(medicamentos.trim())
            append('\n')
        }
        if (rutinas.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append(MARK_RUT)
            append('\n')
            append(rutinas.trim())
            append('\n')
        }
        if (observaciones.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append(MARK_OBS)
            append('\n')
            append(observaciones.trim())
        }
    }.trim()

    fun displayText(): String = buildString {
        if (medicamentos.isNotBlank()) {
            appendLine("Medicamentos / tratamiento farmacológico")
            appendLine(medicamentos.trim())
            appendLine()
        }
        if (rutinas.isNotBlank()) {
            appendLine("Rutinas y cuidados")
            appendLine(rutinas.trim())
            appendLine()
        }
        if (observaciones.isNotBlank()) {
            appendLine("Observaciones")
            appendLine(observaciones.trim())
        }
    }.trim()

    companion object {
        private const val MARK_MED = "---MEDICAMENTOS---"
        private const val MARK_RUT = "---RUTINAS---"
        private const val MARK_OBS = "---OBSERVACIONES---"

        fun parse(raw: String): EditableRecommendations {
            if (raw.isBlank()) return EditableRecommendations()
            if (!raw.contains(MARK_MED) && !raw.contains(MARK_RUT) && !raw.contains(MARK_OBS)) {
                return EditableRecommendations(observaciones = raw.trim())
            }
            fun section(mark: String, nextMarks: List<String>): String {
                val start = raw.indexOf(mark)
                if (start < 0) return ""
                val bodyStart = start + mark.length
                val end = nextMarks.mapNotNull { m ->
                    val i = raw.indexOf(m, bodyStart)
                    if (i >= 0) i else null
                }.minOrNull() ?: raw.length
                return raw.substring(bodyStart, end).trim()
            }
            return EditableRecommendations(
                medicamentos = section(MARK_MED, listOf(MARK_RUT, MARK_OBS)),
                rutinas = section(MARK_RUT, listOf(MARK_OBS)),
                observaciones = section(MARK_OBS, emptyList()),
            )
        }
    }
}

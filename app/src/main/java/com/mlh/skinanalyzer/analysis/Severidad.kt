package com.mlh.skinanalyzer.analysis

/**
 * Escala OEM de 5 niveles. Umbrales ajustables tras comparar con la app original.
 * [mayorEsPeor]: true → valor alto = más atención (acné); false → valor alto = mejor (humedad).
 */
object Severidad {
    /** Cortes sobre la puntuación de atención 0–100 (tras invertir si hace falta). */
    val cortes: List<Pair<Float, CareLevel>> = listOf(
        85f to CareLevel.URGENTE,
        70f to CareLevel.GRAVE,
        50f to CareLevel.MODERADO,
        25f to CareLevel.LEVE,
    )

    fun attentionScore(raw: Float, mayorEsPeor: Boolean): Float {
        val v = raw.coerceIn(0f, 100f)
        return if (mayorEsPeor) v else (100f - v)
    }

    fun clasificar(raw: Float, mayorEsPeor: Boolean): CareLevel {
        val v = attentionScore(raw, mayorEsPeor)
        return cortes.firstOrNull { v >= it.first }?.second ?: CareLevel.MINIMO
    }
}

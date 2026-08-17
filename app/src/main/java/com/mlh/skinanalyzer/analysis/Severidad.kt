package com.mlh.skinanalyzer.analysis

/**
 * Cortes de severidad por parámetro (unidad real: % o conteo).
 * Calibrables comparando contra la app OEM con la misma cara.
 */
data class CortesSeveridad(
    val urgente: Float,
    val grave: Float,
    val moderado: Float,
    val leve: Float,
    val mayorEsPeor: Boolean = true,
)

object Severidad {
    val CORTES: Map<String, CortesSeveridad> = mapOf(
        // Superficie
        "sebum" to CortesSeveridad(80f, 60f, 40f, 20f),
        "pigmentation" to CortesSeveridad(400f, 250f, 120f, 40f),
        "pores" to CortesSeveridad(200f, 140f, 80f, 30f),
        // "Arrugas" usa skinElasticity como proxy (no hay skinWrinkles en el SDK):
        // elasticidad ALTA = piel lisa = BUENO → misma escala que collagen/moisture.
        "wrinkles" to CortesSeveridad(20f, 35f, 50f, 70f, mayorEsPeor = false),
        "scars" to CortesSeveridad(20f, 12f, 6f, 2f),
        "dark_circles" to CortesSeveridad(20f, 35f, 50f, 70f, mayorEsPeor = false),
        "blackheads" to CortesSeveridad(30f, 20f, 12f, 5f),
        // Profundo
        "moisture" to CortesSeveridad(20f, 35f, 50f, 70f, mayorEsPeor = false),
        "sensitivity" to CortesSeveridad(80f, 60f, 40f, 20f),
        "acne" to CortesSeveridad(40f, 25f, 12f, 4f),
        "collagen" to CortesSeveridad(15f, 25f, 40f, 60f, mayorEsPeor = false),
        "uv_spots" to CortesSeveridad(400f, 250f, 120f, 40f),
        "deep_pigment" to CortesSeveridad(80f, 60f, 40f, 20f),
        "deep_acne" to CortesSeveridad(100f, 60f, 30f, 10f),
        "porphyrin" to CortesSeveridad(4000f, 3000f, 2000f, 1000f),
        // Compat Gushang score 0–100
        "elasticity" to CortesSeveridad(20f, 35f, 50f, 70f, mayorEsPeor = false),
        "tone" to CortesSeveridad(20f, 35f, 50f, 70f, mayorEsPeor = false),
        "cuticle" to CortesSeveridad(80f, 60f, 40f, 20f),
        "exudates" to CortesSeveridad(4000f, 3000f, 2000f, 1000f),
        "heavy_metal" to CortesSeveridad(400f, 250f, 120f, 40f),
        "hair" to CortesSeveridad(80f, 60f, 40f, 20f),
    )

    /** Escala genérica 0–100 (fallback). */
    val cortesGenericos: List<Pair<Float, CareLevel>> = listOf(
        85f to CareLevel.URGENTE,
        70f to CareLevel.GRAVE,
        50f to CareLevel.MODERADO,
        25f to CareLevel.LEVE,
    )

    fun cortesFor(key: String): CortesSeveridad =
        CORTES[key] ?: CortesSeveridad(85f, 70f, 50f, 25f, mayorEsPeor = true)

    fun attentionScore(raw: Float, mayorEsPeor: Boolean): Float {
        val v = raw.coerceAtLeast(0f)
        return if (mayorEsPeor) v else v // for per-parameter cortes, invert via threshold order
    }

    fun clasificar(key: String, raw: Float): CareLevel {
        val c = cortesFor(key)
        val v = raw.coerceAtLeast(0f)
        return if (c.mayorEsPeor) {
            when {
                v >= c.urgente -> CareLevel.URGENTE
                v >= c.grave -> CareLevel.GRAVE
                v >= c.moderado -> CareLevel.MODERADO
                v >= c.leve -> CareLevel.LEVE
                else -> CareLevel.MINIMO
            }
        } else {
            // Valor alto = bueno → umbrales invertidos (urgente si está por debajo)
            when {
                v <= c.urgente -> CareLevel.URGENTE
                v <= c.grave -> CareLevel.GRAVE
                v <= c.moderado -> CareLevel.MODERADO
                v <= c.leve -> CareLevel.LEVE
                else -> CareLevel.MINIMO
            }
        }
    }

    /** Compat: asume score 0–100 y mayor = peor salvo [mayorEsPeor]=false. */
    fun clasificar(raw: Float, mayorEsPeor: Boolean): CareLevel {
        val v = raw.coerceIn(0f, 100f)
        val attention = if (mayorEsPeor) v else (100f - v)
        return cortesGenericos.firstOrNull { attention >= it.first }?.second ?: CareLevel.MINIMO
    }

    fun mayorEsPeor(key: String): Boolean = cortesFor(key).mayorEsPeor
}

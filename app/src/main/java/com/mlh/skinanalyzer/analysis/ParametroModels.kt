package com.mlh.skinanalyzer.analysis

/**
 * Modelo de parámetros con zonas y unidades (spec CURSOR_PARAMETROS_DETALLE).
 */
enum class Zona(val label: String) {
    CARA_IZQ("Cara izquierda"),
    CARA_DER("Cara derecha"),
    ZONA_T("Zona T"),
    MANDIBULA("Mandíbula"),
    FRENTE("Frente"),
    ALREDEDORES("Alrededores"),
    CENO("Ceño"),
    OJO_IZQ("Ojo izquierdo"),
    OJO_DER("Ojo derecho"),
    GLOBAL("Total"),
}

enum class Unidad(val label: String) {
    CANTIDAD("unidad"),
    PORCENTAJE("%"),
    TIRA("tira"),
}

enum class Grupo { SUPERFICIE, PROFUNDO }

enum class EstiloOverlay {
    MASCARA_SOLIDA,
    CONTORNO_MANCHA,
    PUNTOS,
    CIRCULOS,
    LINEAS_COLOR,
    HEATMAP_MONO,
    OVALOS_OJERAS,
}

data class Medicion(
    val zona: Zona,
    val valor: Float,
    val unidad: Unidad,
)

data class ParametroDetalle(
    val key: String,
    val nombre: String,
    val grupo: Grupo,
    val mediciones: List<Medicion>,
    val severidad: CareLevel,
    val espectroFile: String,
    val espectroLabel: String,
    val estilo: EstiloOverlay,
    val score: Float,
    val overlayPath: String? = null,
)

data class FacialMmMeasures(
    val faceLengthMm: Float? = null,
    val cheekWidthMm: Float? = null,
    val temporalWidthMm: Float? = null,
    val mandibleAngleWidthMm: Float? = null,
    val jawAngleDeg: Float? = null,
    val upperThirdMm: Float? = null,
    val middleThirdMm: Float? = null,
    val lowerThirdMm: Float? = null,
    val innerEyeSpaceMm: Float? = null,
    val rightEyeWidthMm: Float? = null,
    val leftEyeWidthMm: Float? = null,
    val leftZygomaSpaceMm: Float? = null,
    val rightZygomaSpaceMm: Float? = null,
    val goldenTriangleDeg: Float? = null,
    val chinLengthMm: Float? = null,
    val chinWidthMm: Float? = null,
    val browWidthMm: Float? = null,
    val browThickMm: Float? = null,
    val browHeightMm: Float? = null,
    val noseAlaWidthMm: Float? = null,
    val lipHeightMm: Float? = null,
    val source: String = "",
) {
    fun hasAny(): Boolean = listOfNotNull(
        faceLengthMm, cheekWidthMm, temporalWidthMm, mandibleAngleWidthMm, jawAngleDeg,
        upperThirdMm, middleThirdMm, lowerThirdMm, innerEyeSpaceMm,
        rightEyeWidthMm, leftEyeWidthMm, goldenTriangleDeg, chinLengthMm, chinWidthMm,
        browWidthMm, browThickMm, browHeightMm, noseAlaWidthMm, lipHeightMm,
    ).isNotEmpty()
}

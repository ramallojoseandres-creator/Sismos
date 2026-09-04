package com.mlh.skinanalyzer.analysis.oem

import com.mlh.skinanalyzer.analysis.EstiloOverlay
import com.mlh.skinanalyzer.analysis.Grupo
import com.mlh.skinanalyzer.analysis.Unidad

object OemIndicatorCatalog {
    private data class Meta(
        val key: String,
        val name: String,
        val layer: String,
        val grupo: Grupo,
        val unidad: Unidad,
        val estilo: EstiloOverlay,
        val espectro: String,
        val espectroLabel: String,
    )

    private val byOemType = mapOf(
        "oily_gloss" to Meta("sebum", "Aceite", "superficial", Grupo.SUPERFICIE, Unidad.PORCENTAJE, EstiloOverlay.MASCARA_SOLIDA, OemCaptureFiles.POSITIVE, "Polarizada paralela"),
        "skin_pigmentation" to Meta("pigmentation", "Pigmentación", "superficial", Grupo.SUPERFICIE, Unidad.CANTIDAD, EstiloOverlay.CONTORNO_MANCHA, OemCaptureFiles.WHITE, "Luz blanca"),
        "skin_pore" to Meta("pores", "Poros", "superficial", Grupo.SUPERFICIE, Unidad.CANTIDAD, EstiloOverlay.PUNTOS, OemCaptureFiles.POSITIVE, "Polarizada paralela"),
        "skin_wrinkle" to Meta("wrinkles", "Arrugas", "superficial", Grupo.SUPERFICIE, Unidad.TIRA, EstiloOverlay.LINEAS_COLOR, OemCaptureFiles.WHITE, "Luz blanca"),
        "skin_acne_scar" to Meta("scars", "Marcas de acné", "superficial", Grupo.SUPERFICIE, Unidad.CANTIDAD, EstiloOverlay.CIRCULOS, OemCaptureFiles.NEGATIVE, "Polarizada cruzada"),
        "black_eye" to Meta("dark_circles", "Ojeras", "superficial", Grupo.SUPERFICIE, Unidad.PORCENTAJE, EstiloOverlay.OVALOS_OJERAS, OemCaptureFiles.WHITE, "Luz blanca"),
        "skin_blackhead" to Meta("blackheads", "Puntos negros", "superficial", Grupo.SUPERFICIE, Unidad.CANTIDAD, EstiloOverlay.PUNTOS, OemCaptureFiles.POSITIVE, "Polarizada paralela"),
        "skin_moisture" to Meta("moisture", "Humedad", "profunda", Grupo.PROFUNDO, Unidad.PORCENTAJE, EstiloOverlay.MASCARA_SOLIDA, OemCaptureFiles.NEGATIVE, "Polarizada cruzada"),
        "skin_sensitivity" to Meta("sensitivity", "Sensibilidad", "profunda", Grupo.PROFUNDO, Unidad.PORCENTAJE, EstiloOverlay.HEATMAP_MONO, OemCaptureFiles.RED, "Luz roja"),
        "skin_acne" to Meta("acne", "Acné", "profunda", Grupo.PROFUNDO, Unidad.CANTIDAD, EstiloOverlay.CIRCULOS, OemCaptureFiles.BLUE, "Luz azul"),
        "skin_collagen" to Meta("collagen", "Colágeno", "profunda", Grupo.PROFUNDO, Unidad.PORCENTAJE, EstiloOverlay.MASCARA_SOLIDA, OemCaptureFiles.NEGATIVE, "Polarizada cruzada"),
        "UV_spot" to Meta("uv_spots", "Manchas UV", "profunda", Grupo.PROFUNDO, Unidad.CANTIDAD, EstiloOverlay.CONTORNO_MANCHA, OemCaptureFiles.UV, "Ultravioleta"),
        "skin_spot" to Meta("deep_pigment", "Pigmentación profunda", "profunda", Grupo.PROFUNDO, Unidad.PORCENTAJE, EstiloOverlay.HEATMAP_MONO, OemCaptureFiles.WSG, "Luz de Wood"),
        "UV_acne" to Meta("deep_acne", "Acné (inflamación)", "profunda", Grupo.PROFUNDO, Unidad.CANTIDAD, EstiloOverlay.CIRCULOS, OemCaptureFiles.BLUE, "Luz azul"),
        "skin_porphyrin" to Meta("porphyrin", "Porfirinas", "profunda", Grupo.PROFUNDO, Unidad.CANTIDAD, EstiloOverlay.PUNTOS, OemCaptureFiles.UV, "Ultravioleta"),
    )

    private val byKey = byOemType.values.associateBy { it.key }

    fun keyFor(oemType: String?): String =
        byOemType[oemType]?.key ?: oemType.orEmpty()

    fun displayName(oemType: String?): String =
        byOemType[oemType]?.name ?: (oemType ?: "Indicador")

    fun layer(oemType: String?): String =
        byOemType[oemType]?.layer ?: "superficial"

    fun grupoFor(key: String): Grupo =
        byKey[key]?.grupo ?: Grupo.SUPERFICIE

    fun unidadFor(key: String): Unidad =
        byKey[key]?.unidad ?: Unidad.CANTIDAD

    fun estiloFor(key: String): EstiloOverlay =
        byKey[key]?.estilo ?: EstiloOverlay.PUNTOS

    fun espectroFileFor(key: String): String =
        byKey[key]?.espectro ?: OemCaptureFiles.WHITE

    fun espectroLabelFor(key: String): String =
        byKey[key]?.espectroLabel ?: "Luz blanca"

    fun baseCaptureFilename(oemType: String?): String =
        byOemType[oemType]?.espectro ?: OemCaptureFiles.WHITE

    fun levelToCareLevel(level: String?): Int = when (level?.lowercase()) {
        "1", "i", "minimo", "mínimo", "leve" -> 1
        "2", "ii" -> 2
        "3", "iii", "moderado" -> 3
        "4", "iv", "grave" -> 4
        "5", "v", "urgente", "severo" -> 5
        else -> ((level?.toIntOrNull() ?: 2).coerceIn(1, 5))
    }
}

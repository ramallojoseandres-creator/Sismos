package com.mlh.skinanalyzer.analysis.oem

object OemIndicatorCatalog {
    private val meta = mapOf(
        "skin_collagen" to Triple("collagen", "Pérdida de colágeno", "profunda"),
        "UV_acne" to Triple("deep_acne", "Acné profundo (UV)", "profunda"),
        "UV_spot" to Triple("uv_spots", "Manchas UV", "profunda"),
        "skin_porphyrin" to Triple("porphyrin", "Porfirinas", "profunda"),
        "skin_moisture" to Triple("moisture", "Hidratación", "superficial"),
        "skin_wrinkle" to Triple("wrinkles", "Arrugas", "superficial"),
        "skin_pigmentation" to Triple("pigmentation", "Pigmentación", "superficial"),
        "skin_sensitivity" to Triple("sensitivity", "Sensibilidad", "profunda"),
        "skin_blackhead" to Triple("blackheads", "Puntos negros", "superficial"),
        "skin_pore" to Triple("pores", "Poros", "superficial"),
        "black_eye" to Triple("dark_circles", "Ojeras", "superficial"),
        "skin_spot" to Triple("spots", "Manchas", "superficial"),
        "oily_gloss" to Triple("sebum", "Sebo / brillo", "superficial"),
        "skin_acne" to Triple("acne", "Acné", "superficial"),
        "skin_acne_scar" to Triple("acne_scar", "Cicatrices de acné", "superficial"),
    )

    fun keyFor(oemType: String?): String =
        meta[oemType]?.first ?: oemType.orEmpty()

    fun displayName(oemType: String?): String =
        meta[oemType]?.second ?: (oemType ?: "Indicador")

    fun layer(oemType: String?): String =
        meta[oemType]?.third ?: "superficial"

    /**
     * Which spectral capture is the face underlay for [oemType] maps
     * (matches landmark light used in OemSkinEngine).
     */
    fun baseCaptureFilename(oemType: String?): String = when (oemType) {
        "skin_pigmentation", "skin_sensitivity" -> OemCaptureFiles.POSITIVE
        "skin_blackhead", "skin_pore", "black_eye", "skin_spot",
        "oily_gloss", "skin_acne", "skin_acne_scar",
        -> OemCaptureFiles.NEGATIVE
        else -> OemCaptureFiles.WHITE
    }

    fun levelToCareLevel(level: String?): Int = when (level?.lowercase()) {
        "1", "i", "leve" -> 1
        "2", "ii" -> 2
        "3", "iii", "moderado" -> 3
        "4", "iv" -> 4
        "5", "v", "grave", "severo" -> 5
        else -> ((level?.toIntOrNull() ?: 2).coerceIn(1, 5))
    }
}

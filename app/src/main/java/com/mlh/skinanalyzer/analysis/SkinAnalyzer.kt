package com.mlh.skinanalyzer.analysis

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class CareLevel(val value: Int, val label: String, val colorHex: String) {
    L1(1, "Leve", "#2E9E6A"),
    L2(2, "Leve a moderado", "#3A7BD5"),
    L3(3, "Moderado", "#D4A017"),
    L4(4, "Severo", "#E07A3D"),
    L5(5, "Grave", "#C0392B");

    companion object {
        fun fromScore(score: Float): CareLevel = when {
            score < 20f -> L1
            score < 40f -> L2
            score < 60f -> L3
            score < 80f -> L4
            else -> L5
        }
    }
}

data class SkinMetric(
    val key: String,
    val name: String,
    val layer: String, // "superficial" | "profunda"
    val score: Float,
    val level: CareLevel,
    val description: String,
    val causes: String,
    val precautions: String,
    val recommendation: String,
)

data class SkinAnalysisResult(
    val metrics: List<SkinMetric>,
    val skinType: String,
    val skinAge: Int,
    val overview: String,
    val facialRatioNote: String,
    val priorityKeys: List<String>,
    val facial: FacialProportionAnalyzer.Result? = null,
)

/**
 * Offline multi-spectral skin analysis.
 * Uses RGB / texture heuristics on White, XPL, PPL, UV and Wood frames.
 * Not a medical diagnosis — clinical support for aesthetic consultation.
 */
object SkinAnalyzer {

    fun analyze(
        images: Map<String, Bitmap>,
        patientAge: Int,
        moisturePercent: Float? = null,
    ): SkinAnalysisResult {
        val white = images["White"] ?: images.values.firstOrNull()
        val xpl = images["XPL"] ?: white
        val ppl = images["PPL"] ?: white
        val uv = images["UV"] ?: white
        val woods = images["Wood's"] ?: uv

        require(white != null)

        val oil = sebumScore(white)
        val pores = poreScore(xpl ?: white)
        val spots = spotScore(ppl ?: white)
        val wrinkles = wrinkleScore(ppl ?: white)
        val acne = acneScore(ppl ?: white, uv ?: white)
        val blackheads = blackheadScore(xpl ?: white)
        val darkCircles = darkCircleScore(white)
        val sensitivity = sensitivityScore(woods ?: white)
        val uvSpots = uvSpotScore(uv ?: white)
        val deepPigment = deepPigmentScore(woods ?: white)
        val deepAcne = deepAcneScore(uv ?: white)
        val collagen = collagenLossScore(uv ?: white, wrinkles)
        val moisture = moistureScore(moisturePercent, white)
        val texture = textureScore(white)

        val metrics = listOf(
            metric("sebum", "Sebo / grasa", "superficial", oil,
                "Distribución de lípidos libres en superficie bajo luz blanca.",
                "Hormonas, clima, limpieza insuficiente o excesiva.",
                "Limpiar sin desengrasar en exceso; evitar tocarse la cara.",
                "Limpieza equilibrada, control de sebo y mascarillas de arcilla suaves."),
            metric("pores", "Poros dilatados", "superficial", pores,
                "Polarización revela poros abiertos y textura irregular.",
                "Exceso de sebo, envejecimiento, exposición solar.",
                "No exprimir; usar protector solar diario.",
                "Limpieza profunda, retinoides suaves y sellado de poros."),
            metric("pigmentation", "Manchas / pigmentación", "superficial", spots,
                "PPL filtra reflejos y muestra freckles, melasma y otras manchas.",
                "Sol, inflamación, cambios hormonales.",
                "FPS diario, evitar irritantes agresivos.",
                "Despigmentantes, vitamina C y fotoprotección estricta."),
            metric("wrinkles", "Arrugas y líneas finas", "superficial", wrinkles,
                "Textura y surcos visibles en polarización.",
                "Pérdida de colágeno, deshidratación, expresión facial.",
                "Hidratación nocturna y sueño adecuado.",
                "Péptidos, retinoides y tratamientos de estímulo dérmico."),
            metric("acne", "Acné superficial", "superficial", acne,
                "Lesiones inflamatorias y comedones en PPL/UV.",
                "Propionibacterium, sebo, hiperqueratina.",
                "No manipular lesiones; higiene de manos y fundas de almohada.",
                "Activos antiacné (BHA, peróxido) y control de inflamación."),
            metric("blackheads", "Puntos negros", "superficial", blackheads,
                "Comedones abiertos oxidados en polarización.",
                "Sebo + queratina en poros, contaminación.",
                "Evitar extracción casera agresiva.",
                "BHA, extracción profesional y limpieza de poros."),
            metric("dark_circles", "Ojeras", "superficial", darkCircles,
                "Pigmento y sombra vascular en región periocular.",
                "Genética, fatiga, alergias, adelgazamiento cutáneo.",
                "Descanso y cuidado suave del contorno.",
                "Contorno con cafeína/péptidos; valorar vascular vs pigmentario."),
            metric("sensitivity", "Sensibilidad", "profunda", sensitivity,
                "Reactividad y eritema bajo luz de Wood.",
                "Barrera alterada, activos agresivos, rosácea.",
                "Cosméticos mínimamente irritantes; agua tibia.",
                "Reparación de barrera, ceramidas y evitar alcohol."),
            metric("uv_spots", "Manchas UV", "profunda", uvSpots,
                "Daño actínico latente visible en UV.",
                "Exposición solar acumulada.",
                "FPS amplio espectro todo el año.",
                "Antioxidantes y tratamientos despigmentantes profundos."),
            metric("deep_pigment", "Pigmento profundo", "profunda", deepPigment,
                "Melanina en capas profundas (Wood).",
                "Melasma, PIH, fotoenvejecimiento.",
                "Evitar calor excesivo y sol directo.",
                "Protocolo despigmentante médico supervisado."),
            metric("deep_acne", "Acné profundo / flora", "profunda", deepAcne,
                "Fluorescencia brick-red de P. acnes en UV.",
                "Colonización bacteriana folicular.",
                "No automedicarse con antibióticos tópicos prolongados.",
                "Tratamiento antibacteriano y regulación sebácea."),
            metric("collagen", "Pérdida de colágeno", "profunda", collagen,
                "Fluorescencia de fibras y elasticidad estimada.",
                "Edad, sol, tabaco, estrés oxidativo.",
                "Protección solar y hábitos anti-aging.",
                "Bioestimulación, radiofrecuencia o láser según criterio médico."),
            metric("moisture", "Hidratación", "superficial", moisture,
                moisturePercent?.let { "Medición de humedad: ${"%.1f".format(it)}%." }
                    ?: "Estimación por brillo y textura (sin lápiz de humedad).",
                "Clima, barrera cutánea, edad.",
                "Humidificar ambiente; evitar lavados muy calientes.",
                "Humectantes (HA, glicerina) y oclusivos ligeros."),
            metric("texture", "Textura / suavidad", "superficial", texture,
                "Homogeneidad de la superficie bajo luz blanca.",
                "Descamación, poros, cicatrices leves.",
                "Exfoliación suave 1–2 veces/semana.",
                "Renovación celular controlada y mascarillas calmantes."),
        )

        val skinType = classifySkinType(oil, moisture, sensitivity)
        val avgSeverity = metrics.map { it.score }.average().toFloat()
        val skinAge = estimateSkinAge(patientAge, avgSeverity, wrinkles, collagen, uvSpots)
        val priority = metrics.sortedByDescending { it.score }.take(3).map { it.key }
        val overview = buildOverview(skinType, skinAge, priority, metrics)
        val facial = FacialProportionAnalyzer.analyze(white)

        return SkinAnalysisResult(
            metrics = metrics,
            skinType = skinType,
            skinAge = skinAge,
            overview = overview,
            facialRatioNote = facial.note,
            priorityKeys = priority,
            facial = facial,
        )
    }

    /** Create Blue / Brown / Red visualization bitmaps from UV / Wood / White. */
    fun deriveSpectralMaps(
        white: Bitmap?,
        uv: Bitmap?,
        woods: Bitmap?,
    ): Map<String, Bitmap> {
        val out = LinkedHashMap<String, Bitmap>()
        uv?.let { out["Blue"] = mapChannel(it, mode = SpectralMap.BLUE) }
        woods?.let { out["Orange"] = mapChannel(it, mode = SpectralMap.BROWN) }
            ?: white?.let { out["Orange"] = mapChannel(it, mode = SpectralMap.BROWN) }
        out["Orange"]?.let { out["Brown"] = it }
        white?.let { out["Red"] = mapChannel(it, mode = SpectralMap.RED) }
            ?: uv?.let { out["Red"] = mapChannel(it, mode = SpectralMap.RED) }
        return out
    }

    private enum class SpectralMap { BLUE, BROWN, RED }

    private fun mapChannel(src: Bitmap, mode: SpectralMap): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val step = max(1, min(w, h) / 320)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val c = pixels[y * w + x]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val mapped = when (mode) {
                    SpectralMap.BLUE -> {
                        val fluo = ((b * 1.4f - r * 0.4f).coerceIn(0f, 255f)).toInt()
                        Color.rgb(fluo / 4, fluo / 2, fluo)
                    }
                    SpectralMap.BROWN -> {
                        val melanin = ((r * 0.6f + g * 0.3f - b * 0.2f).coerceIn(0f, 255f)).toInt()
                        Color.rgb(melanin, (melanin * 0.65f).toInt(), (melanin * 0.35f).toInt())
                    }
                    SpectralMap.RED -> {
                        val hb = ((r * 1.2f - g * 0.5f - b * 0.2f).coerceIn(0f, 255f)).toInt()
                        Color.rgb(hb, hb / 5, hb / 6)
                    }
                }
                for (dy in 0 until step) {
                    for (dx in 0 until step) {
                        val xx = x + dx
                        val yy = y + dy
                        if (xx < w && yy < h) out.setPixel(xx, yy, mapped)
                    }
                }
            }
        }
        return out
    }

    private fun metric(
        key: String,
        name: String,
        layer: String,
        score: Float,
        description: String,
        causes: String,
        precautions: String,
        recommendation: String,
    ) = SkinMetric(
        key = key,
        name = name,
        layer = layer,
        score = score.coerceIn(0f, 100f),
        level = CareLevel.fromScore(score),
        description = description,
        causes = causes,
        precautions = precautions,
        recommendation = recommendation,
    )

    private fun sampleStats(bmp: Bitmap, faceBias: Boolean = true): FloatArray {
        // returns [avgR, avgG, avgB, luminanceVar, edgeDensity, warmRatio, darkRatio]
        val w = bmp.width
        val h = bmp.height
        val x0 = if (faceBias) w / 5 else 0
        val x1 = if (faceBias) w * 4 / 5 else w
        val y0 = if (faceBias) h / 8 else 0
        val y1 = if (faceBias) h * 7 / 8 else h
        val step = max(2, min(w, h) / 180)
        var n = 0
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var sumL = 0.0
        var sumL2 = 0.0
        var edges = 0
        var warm = 0
        var dark = 0
        var prevL = -1.0
        for (y in y0 until y1 step step) {
            for (x in x0 until x1 step step) {
                val c = bmp.getPixel(x, y)
                val r = Color.red(c).toDouble()
                val g = Color.green(c).toDouble()
                val b = Color.blue(c).toDouble()
                val l = 0.2126 * r + 0.7152 * g + 0.0722 * b
                sumR += r; sumG += g; sumB += b; sumL += l; sumL2 += l * l
                if (r > g + 8 && r > b + 8) warm++
                if (l < 55) dark++
                if (prevL >= 0 && abs(l - prevL) > 18) edges++
                prevL = l
                n++
            }
        }
        if (n == 0) return floatArrayOf(128f, 110f, 100f, 20f, 0.1f, 0.3f, 0.1f)
        val avgL = sumL / n
        val varL = (sumL2 / n - avgL * avgL).toFloat().coerceAtLeast(0f)
        return floatArrayOf(
            (sumR / n).toFloat(),
            (sumG / n).toFloat(),
            (sumB / n).toFloat(),
            varL,
            edges.toFloat() / n,
            warm.toFloat() / n,
            dark.toFloat() / n,
        )
    }

    private fun sebumScore(bmp: Bitmap): Float {
        val s = sampleStats(bmp)
        // glossy / yellow-warm highlights
        return ((s[0] - s[2]) * 0.45f + s[3] * 0.15f + s[5] * 55f).coerceIn(5f, 95f)
    }

    private fun poreScore(bmp: Bitmap): Float {
        val s = sampleStats(bmp)
        return (s[4] * 220f + s[3] * 0.25f).coerceIn(5f, 95f)
    }

    private fun spotScore(bmp: Bitmap): Float {
        val s = sampleStats(bmp)
        return (s[5] * 70f + s[6] * 80f + (s[0] - s[1]) * 0.3f).coerceIn(5f, 95f)
    }

    private fun wrinkleScore(bmp: Bitmap): Float {
        val s = sampleStats(bmp)
        return (s[4] * 260f + s[3] * 0.2f).coerceIn(5f, 95f)
    }

    private fun acneScore(ppl: Bitmap, uv: Bitmap): Float {
        val a = sampleStats(ppl)
        val b = sampleStats(uv)
        return (a[5] * 50f + b[0] * 0.12f + a[6] * 40f).coerceIn(5f, 95f)
    }

    private fun blackheadScore(bmp: Bitmap): Float {
        val s = sampleStats(bmp)
        return (s[6] * 90f + s[4] * 80f).coerceIn(5f, 95f)
    }

    private fun darkCircleScore(bmp: Bitmap): Float {
        // sample lower central third more
        val w = bmp.width
        val h = bmp.height
        var sum = 0.0
        var n = 0
        val step = max(2, min(w, h) / 160)
        for (y in (h * 0.42).toInt() until (h * 0.62).toInt() step step) {
            for (x in (w * 0.25).toInt() until (w * 0.75).toInt() step step) {
                val c = bmp.getPixel(x, y)
                val l = 0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)
                sum += l
                n++
            }
        }
        val avg = if (n == 0) 120.0 else sum / n
        return (100.0 - avg * 0.55).toFloat().coerceIn(5f, 95f)
    }

    private fun sensitivityScore(bmp: Bitmap): Float {
        val s = sampleStats(bmp)
        return (s[5] * 75f + (s[0] - s[1]) * 0.5f).coerceIn(5f, 95f)
    }

    private fun uvSpotScore(bmp: Bitmap): Float {
        val s = sampleStats(bmp)
        return (s[6] * 85f + s[3] * 0.3f + s[4] * 60f).coerceIn(5f, 95f)
    }

    private fun deepPigmentScore(bmp: Bitmap): Float {
        val s = sampleStats(bmp)
        return (s[6] * 70f + s[5] * 40f).coerceIn(5f, 95f)
    }

    private fun deepAcneScore(bmp: Bitmap): Float {
        val s = sampleStats(bmp)
        // brick-red fluorescence proxy
        return (s[5] * 65f + s[0] * 0.15f).coerceIn(5f, 95f)
    }

    private fun collagenLossScore(uv: Bitmap, wrinkle: Float): Float {
        val s = sampleStats(uv)
        return (wrinkle * 0.55f + s[4] * 100f + (60f - s[1] * 0.15f)).coerceIn(5f, 95f)
    }

    private fun moistureScore(measured: Float?, bmp: Bitmap): Float {
        if (measured != null) {
            // high moisture -> low problem score
            return (100f - measured.coerceIn(0f, 100f)).coerceIn(5f, 95f)
        }
        val s = sampleStats(bmp)
        return (55f - s[3] * 0.15f + s[6] * 40f).coerceIn(5f, 95f)
    }

    private fun textureScore(bmp: Bitmap): Float {
        val s = sampleStats(bmp)
        return (s[4] * 200f + s[3] * 0.22f).coerceIn(5f, 95f)
    }

    private fun classifySkinType(oil: Float, moisture: Float, sensitivity: Float): String {
        return when {
            sensitivity > 65f -> "Sensible"
            oil > 60f && moisture > 55f -> "Grasa"
            oil > 55f && moisture < 45f -> "Mixta"
            oil < 35f || moisture > 60f -> "Seca"
            else -> "Normal"
        }
    }

    private fun estimateSkinAge(
        chronological: Int,
        avgSeverity: Float,
        wrinkles: Float,
        collagen: Float,
        uvSpots: Float,
    ): Int {
        val delta = ((wrinkles + collagen + uvSpots) / 3f - 40f) * 0.12f + (avgSeverity - 45f) * 0.05f
        return (chronological + delta).roundToInt().coerceIn(max(18, chronological - 8), chronological + 15)
    }

    private fun buildOverview(
        skinType: String,
        skinAge: Int,
        priority: List<String>,
        metrics: List<SkinMetric>,
    ): String {
        val names = priority.mapNotNull { key -> metrics.find { it.key == key }?.name }
        return "Tipo de piel: $skinType. Edad cutánea estimada: $skinAge años. " +
            "Prioridad de cuidado: ${names.joinToString(", ")}. " +
            "El informe combina imágenes multiespectrales (blanca, PPL, XPL, UV, Wood) " +
            "y mapas derivados (azul, marrón, rojo) para orientar el plan clínico-estético."
    }
}

package com.mlh.skinanalyzer.analysis.oem

import com.google.gson.annotations.SerializedName
import com.mlh.skinanalyzer.analysis.EstiloOverlay
import com.mlh.skinanalyzer.analysis.Grupo
import com.mlh.skinanalyzer.analysis.Medicion
import com.mlh.skinanalyzer.analysis.Unidad
import com.mlh.skinanalyzer.analysis.Zona

/** Gson models for `libsalon.so` JSON (matches OEM `FaceSkinDetectBean.ImageBean`). */
data class OemImageEnvelope(
    @SerializedName("result") val result: OemImageBean?,
)

data class OemAcneScarEnvelope(
    @SerializedName("result") val result: OemImageBean?,
)

data class OemImageBean(
    @SerializedName("type") val type: String? = null,
    @SerializedName("score") val score: Int = 0,
    @SerializedName("level") val level: String? = null,
    @SerializedName("urls") val urls: String? = null,
    @SerializedName("black_url") val blackUrl: String? = null,
    @SerializedName("age") val age: Int = 0,
    @SerializedName("result") val nested: OemImageBean? = null,
    @SerializedName("result_two") val nestedTwo: OemImageBean? = null,
    @SerializedName("value") val value: OemValueBean? = null,
)

data class OemValueBean(
    @SerializedName("all_count") val allCount: Double = 0.0,
    @SerializedName("center_count") val centerCount: Int = 0,
    @SerializedName("f_count") val fCount: Int = 0,
    @SerializedName("jaw_count") val jawCount: Double = 0.0,
    @SerializedName("l_black_eye") val lBlackEye: Int = 0,
    @SerializedName("l_count") val lCount: Int = 0,
    @SerializedName("l_face_count") val lFaceCount: Double = 0.0,
    @SerializedName("r_black_eye") val rBlackEye: Int = 0,
    @SerializedName("r_count") val rCount: Int = 0,
    @SerializedName("r_face_count") val rFaceCount: Double = 0.0,
    @SerializedName("t_count") val tCount: Double = 0.0,
)

data class OemFaceProportionEnvelope(
    @SerializedName("three_parts") val threeParts: OemThreeParts? = null,
    @SerializedName("five_eyes") val fiveEyes: OemFiveEyes? = null,
    @SerializedName("face") val face: OemFaceBean? = null,
    @SerializedName("jaw") val jaw: OemJawBean? = null,
    @SerializedName("eyebrow") val eyebrow: OemEyebrowBean? = null,
    @SerializedName("eyes") val eyes: OemEyesBean? = null,
    @SerializedName("mouth") val mouth: OemMouthBean? = null,
    @SerializedName("nose") val nose: OemNoseBean? = null,
    @SerializedName("golden_triangle") val goldenTriangle: OemGoldenTriangleBean? = null,
)

data class OemThreeParts(
    @SerializedName("upper") val upper: Float = 0f,
    @SerializedName("middle") val middle: Float = 0f,
    @SerializedName("lower") val lower: Float = 0f,
    @SerializedName("one_part") val onePart: OemPartLength? = null,
    @SerializedName("two_part") val twoPart: OemPartLength? = null,
    @SerializedName("three_part") val threePart: OemPartLength? = null,
)

data class OemPartLength(
    @SerializedName("faceup_length") val faceUpLength: Double = 0.0,
    @SerializedName("facemid_length") val faceMidLength: Double = 0.0,
    @SerializedName("facedown_length") val faceDownLength: Double = 0.0,
)

data class OemFiveEyes(
    @SerializedName("eye_width") val eyeWidth: Float = 0f,
    @SerializedName("face_width") val faceWidth: Float = 0f,
    @SerializedName("lefteye") val leftEye: Double = 0.0,
    @SerializedName("righteye") val rightEye: Double = 0.0,
    @SerializedName("five_eye") val fiveEye: OemEyeEmpty? = null,
    @SerializedName("one_eye") val oneEye: OemEyeEmpty? = null,
    @SerializedName("three_eye") val threeEye: OemEyeEmpty? = null,
)

data class OemEyeEmpty(
    @SerializedName("eye_empty_length") val eyeEmptyLength: Double = 0.0,
)

data class OemFaceBean(
    @SerializedName("face_length") val faceLength: Double = 0.0,
    @SerializedName("zygoma_length") val zygomaLength: Double = 0.0,
    @SerializedName("tempus_length") val tempusLength: Double = 0.0,
    @SerializedName("mandible_length") val mandibleLength: Double = 0.0,
)

data class OemJawBean(
    @SerializedName("jaw_angle") val jawAngle: Double = 0.0,
    @SerializedName("jaw_length") val jawLength: Double = 0.0,
    @SerializedName("jaw_width") val jawWidth: Double = 0.0,
)

data class OemEyebrowBean(
    @SerializedName("brow_width") val browWidth: Double = 0.0,
    @SerializedName("brow_thick") val browThick: Double = 0.0,
    @SerializedName("brow_height") val browHeight: Double = 0.0,
)

data class OemEyesBean(
    @SerializedName("eye_w") val eyeW: Double = 0.0,
    @SerializedName("angulus_oculi_medialis") val innerCorner: Double = 0.0,
)

data class OemMouthBean(
    @SerializedName("mouth_height") val mouthHeight: Double = 0.0,
    @SerializedName("lip_thickness") val lipThickness: Double = 0.0,
)

data class OemNoseBean(
    @SerializedName("nose_width") val noseWidth: Double = 0.0,
)

data class OemGoldenTriangleBean(
    @SerializedName("golden_triangle") val goldenTriangle: Double = 0.0,
)

data class OemIndicatorResult(
    val key: String,
    val oemType: String,
    val displayName: String,
    val layer: String,
    val score: Int,
    val levelLabel: String,
    val overlayPath: String?,
    val blackOverlayPath: String? = null,
    val mediciones: List<Medicion> = emptyList(),
    val unidad: Unidad = Unidad.CANTIDAD,
    val estilo: EstiloOverlay = EstiloOverlay.PUNTOS,
    val grupo: Grupo = Grupo.SUPERFICIE,
)

data class OemAnalysisBundle(
    val indicators: List<OemIndicatorResult>,
    val skinAge: Int,
    val overview: String,
    val facialRatioJson: String,
    val sessionDir: String,
)

object OemZoneParser {
    fun medicionesFor(key: String, value: OemValueBean?): List<Medicion> {
        if (value == null) return emptyList()
        val unidad = OemIndicatorCatalog.unidadFor(key)
        val out = mutableListOf<Medicion>()
        fun add(zona: Zona, v: Double) {
            if (v > 0.0 || key == "moisture" || key == "collagen" || key == "deep_pigment" || key == "sebum") {
                out += Medicion(zona, v.toFloat(), unidad)
            }
        }
        when (key) {
            "dark_circles" -> {
                add(Zona.OJO_IZQ, value.lBlackEye.toDouble())
                add(Zona.OJO_DER, value.rBlackEye.toDouble())
            }
            "wrinkles" -> {
                add(Zona.FRENTE, value.fCount.toDouble())
                add(Zona.ALREDEDORES, value.lCount.toDouble() + value.rCount.toDouble())
                add(Zona.CENO, value.centerCount.toDouble())
                if (value.allCount > 0) add(Zona.GLOBAL, value.allCount)
            }
            "blackheads", "scars", "acne", "deep_acne", "porphyrin", "sensitivity" -> {
                if (value.allCount > 0) add(Zona.GLOBAL, value.allCount)
                else if (value.lCount + value.rCount > 0) {
                    add(Zona.CARA_IZQ, value.lCount.toDouble())
                    add(Zona.CARA_DER, value.rCount.toDouble())
                }
            }
            else -> {
                // 4 zonas típicas: izq/der/T/mandíbula
                val l = if (value.lFaceCount != 0.0) value.lFaceCount else value.lCount.toDouble()
                val r = if (value.rFaceCount != 0.0) value.rFaceCount else value.rCount.toDouble()
                if (l != 0.0 || r != 0.0 || value.tCount != 0.0 || value.jawCount != 0.0) {
                    add(Zona.CARA_IZQ, l)
                    add(Zona.CARA_DER, r)
                    add(Zona.ZONA_T, value.tCount)
                    add(Zona.MANDIBULA, value.jawCount)
                } else if (value.allCount > 0) {
                    add(Zona.GLOBAL, value.allCount)
                }
            }
        }
        return out
    }

    fun primaryValue(key: String, value: OemValueBean?, scoreFallback: Float): Float {
        val meds = medicionesFor(key, value)
        if (meds.isEmpty()) return scoreFallback
        val global = meds.firstOrNull { it.zona == Zona.GLOBAL }
        if (global != null) return global.valor
        return when (OemIndicatorCatalog.unidadFor(key)) {
            Unidad.PORCENTAJE -> meds.map { it.valor }.average().toFloat()
            else -> meds.sumOf { it.valor.toDouble() }.toFloat()
        }
    }
}

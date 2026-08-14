package com.mlh.skinanalyzer.analysis.oem

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.mlh.skinanalyzer.analysis.CareLevel
import com.mlh.skinanalyzer.analysis.FacialProportionAnalyzer
import com.mlh.skinanalyzer.analysis.SkinAnalysisResult
import com.mlh.skinanalyzer.analysis.SkinMetric
import com.zeze.faceDetection.FaceDetectionJni
import java.io.File

/**
 * Runs the OEM `libsalon.so` pipeline locally (no ai.aiskin.vip).
 * Requires session folder with white/negative/positive/uv/... jpegs.
 */
class OemSkinEngine(context: Context) {
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val landmarks = OemFaceLandmarks(appContext)

    val isNativeLoaded: Boolean = runCatching {
        FaceDetectionJni.LANDMARK_COUNT
        true
    }.getOrDefault(false)

    fun canAnalyze(sessionDir: String): Boolean {
        val dir = File(sessionDir)
        if (!dir.isDirectory) return false
        return OemCaptureFiles.requiredSources.all { File(dir, it).exists() } &&
            OemFaceLandmarks.isAvailable(appContext)
    }

    fun analyze(sessionDir: String, patientAge: Int): OemAnalysisBundle? {
        if (!canAnalyze(sessionDir)) return null
        val dir = sessionDir.ensureTrailingSlash()
        val lm = landmarks.extract(sessionDir) ?: return null
        return try {
            val indicators = mutableListOf<OemIndicatorResult>()
            fun addFromJson(json: String, landmarkX: FloatArray, landmarkY: FloatArray) {
                parseBean(json)?.let { addIndicator(indicators, it, dir) }
            }
            fun run(fn: (String, String, FloatArray, FloatArray) -> String, lx: FloatArray, ly: FloatArray) {
                addFromJson(fn(dir, dir, lx, ly), lx, ly)
            }

            run(FaceDetectionJni::skinCollagen, lm.whiteX, lm.whiteY)
            run({ p, p2, x, y -> FaceDetectionJni.skinUVAcne(p, p2, x, y).replace("values", "value") }, lm.whiteX, lm.whiteY)
            run(FaceDetectionJni::skinUVspot, lm.whiteX, lm.whiteY)
            run(FaceDetectionJni::skinPorphyrin, lm.whiteX, lm.whiteY)
            run(FaceDetectionJni::skinMoisture, lm.whiteX, lm.whiteY)
            run(FaceDetectionJni::skinWrinkle, lm.whiteX, lm.whiteY)
            run(FaceDetectionJni::skinPigmentation, lm.positiveX, lm.positiveY)
            run(FaceDetectionJni::skinSensitivity, lm.positiveX, lm.positiveY)
            run(FaceDetectionJni::skinBlackhead, lm.negativeX, lm.negativeY)
            run(FaceDetectionJni::skinPore, lm.negativeX, lm.negativeY)
            run(FaceDetectionJni::skinBlackeye, lm.negativeX, lm.negativeY)
            run(FaceDetectionJni::skinSpot, lm.negativeX, lm.negativeY)
            run(FaceDetectionJni::skinOilyGloss, lm.negativeX, lm.negativeY)

            val acneJson = FaceDetectionJni.skinAcneScar(dir, dir, lm.negativeX, lm.negativeY)
                .replace("result2", "result_two")
            parseBean(acneJson)?.let { scarRoot ->
                scarRoot.nested?.let { addIndicator(indicators, it, dir) }
                scarRoot.nestedTwo?.let { addIndicator(indicators, it, dir) }
            }

            val faceJson = FaceDetectionJni.skinFaceLandMark(dir, dir, lm.whiteX, lm.whiteY)
            val skinAge = indicators.firstOrNull { it.oemType == "skin_collagen" }?.score?.let {
                // Prefer native skinAge if available
                runCatching {
                    gson.fromJson(FaceDetectionJni.skinAge(dir, dir, lm.whiteX, lm.whiteY), OemImageEnvelope::class.java)
                        .result?.age
                }.getOrNull()
            } ?: patientAge

            val overview = buildOverview(indicators)
            OemAnalysisBundle(
                indicators = indicators,
                skinAge = skinAge.coerceIn(18, 75),
                overview = overview,
                facialRatioJson = faceJson,
                sessionDir = sessionDir,
            )
        } catch (e: Exception) {
            Log.e(TAG, "OEM analysis failed", e)
            null
        }
    }

    fun toSkinAnalysisResult(bundle: OemAnalysisBundle, patientAge: Int): SkinAnalysisResult {
        val metrics = bundle.indicators.map { ind ->
            val score = ind.score.coerceIn(0, 100).toFloat()
            val levelNum = OemIndicatorCatalog.levelToCareLevel(ind.levelLabel)
            val care = CareLevel.entries.firstOrNull { it.value == levelNum } ?: CareLevel.fromScore(score)
            SkinMetric(
                key = ind.key,
                name = ind.displayName,
                layer = ind.layer,
                score = score,
                level = care,
                description = "Mapa OEM offline (${ind.oemType}).",
                causes = "Ver informe visual del indicador.",
                precautions = "Consulta estética; no sustituye diagnóstico médico.",
                recommendation = defaultRecommendation(ind.key),
            )
        }
        val facial = parseFacial(bundle.facialRatioJson)
        val priority = metrics.sortedByDescending { it.score }.take(3).map { it.key }
        return SkinAnalysisResult(
            metrics = metrics,
            skinType = inferSkinType(metrics),
            skinAge = bundle.skinAge,
            overview = bundle.overview,
            facialRatioNote = facial.note,
            priorityKeys = priority,
            facial = facial,
        )
    }

    fun overlayMap(sessionDir: String, oemType: String): String? {
        val path = OemCaptureFiles.overlayPath(sessionDir, oemType)
        return path.takeIf { File(it).exists() }
    }

    private fun parseFacial(json: String): FacialProportionAnalyzer.Result {
        return runCatching {
            val env = gson.fromJson(json, OemFaceProportionEnvelope::class.java)
            val tp = env.threeParts
            val fe = env.fiveEyes
            if (tp != null) {
                FacialProportionAnalyzer.Result(
                    upperThird = tp.upper,
                    middleThird = tp.middle,
                    lowerThird = tp.lower,
                    eyeUnits = fe?.eyeWidth ?: 5f,
                    faceWidthUnits = fe?.faceWidth ?: 5f,
                    symmetryScore = 0.85f,
                    note = "Proporciones 3/5 (motor OEM nativo skinFaceLandMark).",
                    summary = "Análisis facial OEM offline.",
                )
            } else {
                FacialProportionAnalyzer.Result(
                    0.33f, 0.34f, 0.33f, 5f, 5f, 0.5f,
                    "Proporciones OEM (JSON sin tercios parseados).",
                    json.take(200),
                )
            }
        }.getOrElse {
            FacialProportionAnalyzer.Result(
                0.33f, 0.34f, 0.33f, 5f, 5f, 0.5f,
                "Proporciones OEM nativas.",
                json.take(120),
            )
        }
    }

    private fun parseBean(json: String): OemImageBean? = runCatching {
        gson.fromJson(json, OemImageEnvelope::class.java).result
    }.getOrNull()

    private fun addIndicator(list: MutableList<OemIndicatorResult>, bean: OemImageBean, dir: String) {
        val type = bean.type ?: return
        val overlay = bean.urls?.let { resolvePath(dir, it) }
            ?: OemCaptureFiles.overlayPath(dir.trimEnd('/'), type).takeIf { File(it).exists() }
        list += OemIndicatorResult(
            key = OemIndicatorCatalog.keyFor(type),
            oemType = type,
            displayName = OemIndicatorCatalog.displayName(type),
            layer = OemIndicatorCatalog.layer(type),
            score = bean.score.coerceIn(0, 100),
            levelLabel = bean.level ?: "2",
            overlayPath = overlay,
            blackOverlayPath = bean.blackUrl?.let { resolvePath(dir, it) },
        )
    }

    private fun resolvePath(dir: String, relative: String): String {
        if (relative.startsWith("/")) return relative
        return File(dir.trimEnd('/'), relative.removePrefix("./")).absolutePath
    }

    private fun buildOverview(indicators: List<OemIndicatorResult>): String {
        val top = indicators.sortedByDescending { it.score }.take(3)
        if (top.isEmpty()) return "Análisis OEM completado."
        return "Prioridades: ${top.joinToString(", ") { "${it.displayName} (nivel ${it.levelLabel})" }}."
    }

    private fun inferSkinType(metrics: List<SkinMetric>): String {
        val sebum = metrics.firstOrNull { it.key == "sebum" }?.score ?: 30f
        val moisture = metrics.firstOrNull { it.key == "moisture" }?.score ?: 30f
        return when {
            sebum > 55f && moisture < 40f -> "Mixta grasa"
            sebum > 55f -> "Grasa"
            moisture > 55f -> "Seca"
            else -> "Normal"
        }
    }

    private fun defaultRecommendation(key: String): String = when (key) {
        "sebum" -> "Limpieza equilibrada y control de sebo."
        "pores" -> "BHA suave y fotoprotección."
        "pigmentation" -> "Vitamina C + FPS estricto."
        "wrinkles" -> "Retinoides suaves e hidratación."
        "acne", "deep_acne" -> "Tratamiento antiinflamatorio; no manipular."
        "porphyrin" -> "Higiene profunda y protocolo antibacteriano suave."
        "moisture" -> "Humectantes + barrera cutánea."
        "sensitivity" -> "Calmar barrera; evitar activos fuertes."
        else -> "Ver mapa del indicador y catálogo local de cuidado."
    }

    private fun String.ensureTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"

    fun close() = landmarks.close()

    companion object {
        private const val TAG = "OemSkinEngine"
    }
}

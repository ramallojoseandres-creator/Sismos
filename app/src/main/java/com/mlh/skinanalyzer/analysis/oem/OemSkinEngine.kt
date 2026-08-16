package com.mlh.skinanalyzer.analysis.oem

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.mlh.skinanalyzer.analysis.CareLevel
import com.mlh.skinanalyzer.analysis.FacialMmMeasures
import com.mlh.skinanalyzer.analysis.FacialProportionAnalyzer
import com.mlh.skinanalyzer.analysis.ParametroDetalle
import com.mlh.skinanalyzer.analysis.Severidad
import com.mlh.skinanalyzer.analysis.SkinAnalysisResult
import com.mlh.skinanalyzer.analysis.SkinMetric
import com.zeze.faceDetection.FaceDetectionJni
import java.io.File

/**
 * Runs the OEM `libsalon.so` pipeline locally (no ai.aiskin.vip).
 * Produces zone mediciones + overlays used by the informe detallado.
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
            fun run(fn: (String, String, FloatArray, FloatArray) -> String, lx: FloatArray, ly: FloatArray) {
                parseBean(fn(dir, dir, lx, ly))?.let { addIndicator(indicators, it, dir) }
            }

            // Superficie
            run(FaceDetectionJni::skinOilyGloss, lm.positiveX, lm.positiveY)
            run(FaceDetectionJni::skinPigmentation, lm.whiteX, lm.whiteY)
            run(FaceDetectionJni::skinPore, lm.positiveX, lm.positiveY)
            run(FaceDetectionJni::skinWrinkle, lm.whiteX, lm.whiteY)
            run(FaceDetectionJni::skinBlackeye, lm.whiteX, lm.whiteY)
            run(FaceDetectionJni::skinBlackhead, lm.positiveX, lm.positiveY)

            val acneJson = FaceDetectionJni.skinAcneScar(dir, dir, lm.negativeX, lm.negativeY)
                .replace("result2", "result_two")
            parseBean(acneJson)?.let { scarRoot ->
                scarRoot.nested?.let { addIndicator(indicators, it, dir) }
                scarRoot.nestedTwo?.let { addIndicator(indicators, it, dir) }
            }

            // Profundo — espectros correctos (UV / XPL / rojo)
            run(FaceDetectionJni::skinMoisture, lm.negativeX, lm.negativeY)
            run(FaceDetectionJni::skinSensitivity, lm.redX, lm.redY)
            run(FaceDetectionJni::skinCollagen, lm.negativeX, lm.negativeY)
            run(FaceDetectionJni::skinUVspot, lm.uvX, lm.uvY)
            run(FaceDetectionJni::skinSpot, lm.wsgX, lm.wsgY)
            run(FaceDetectionJni::skinPorphyrin, lm.uvX, lm.uvY)
            run({ p, p2, x, y -> FaceDetectionJni.skinUVAcne(p, p2, x, y).replace("values", "value") }, lm.uvX, lm.uvY)

            val faceJson = FaceDetectionJni.skinFaceLandMark(dir, dir, lm.whiteX, lm.whiteY)
            val skinAge = runCatching {
                gson.fromJson(FaceDetectionJni.skinAge(dir, dir, lm.whiteX, lm.whiteY), OemImageEnvelope::class.java)
                    .result?.age
            }.getOrNull() ?: patientAge

            OemAnalysisBundle(
                indicators = indicators,
                skinAge = skinAge.coerceIn(18, 75),
                overview = buildOverview(indicators),
                facialRatioJson = faceJson,
                sessionDir = sessionDir,
            )
        } catch (e: Exception) {
            Log.e(TAG, "OEM analysis failed", e)
            null
        }
    }

    fun toParametros(bundle: OemAnalysisBundle): List<ParametroDetalle> =
        bundle.indicators.mapNotNull { ind ->
            if (ind.key == "deep_acne" && ind.score <= 0 && ind.mediciones.isEmpty()) {
                return@mapNotNull null
            }
            val rawForSeverity = if (ind.mediciones.isNotEmpty()) {
                when (ind.unidad) {
                    com.mlh.skinanalyzer.analysis.Unidad.PORCENTAJE ->
                        ind.mediciones.map { it.valor }.average().toFloat()
                    else -> ind.mediciones.firstOrNull {
                        it.zona == com.mlh.skinanalyzer.analysis.Zona.GLOBAL
                    }?.valor ?: ind.mediciones.sumOf { it.valor.toDouble() }.toFloat()
                }
            } else {
                ind.score.toFloat()
            }
            ParametroDetalle(
                key = ind.key,
                nombre = ind.displayName,
                grupo = ind.grupo,
                mediciones = ind.mediciones,
                severidad = Severidad.clasificar(ind.key, rawForSeverity),
                espectroFile = OemIndicatorCatalog.espectroFileFor(ind.key),
                espectroLabel = OemIndicatorCatalog.espectroLabelFor(ind.key),
                estilo = ind.estilo,
                score = rawForSeverity,
                overlayPath = ind.overlayPath,
            )
        }

    fun parseFacialMm(json: String): FacialMmMeasures {
        return runCatching {
            val env = gson.fromJson(json, OemFaceProportionEnvelope::class.java)
            FacialMmMeasures(
                faceLengthMm = env.face?.faceLength?.toFloat()?.takeIf { it > 0 },
                cheekWidthMm = env.face?.zygomaLength?.toFloat()?.takeIf { it > 0 },
                temporalWidthMm = env.face?.tempusLength?.toFloat()?.takeIf { it > 0 },
                mandibleAngleWidthMm = env.face?.mandibleLength?.toFloat()?.takeIf { it > 0 },
                jawAngleDeg = env.jaw?.jawAngle?.toFloat()?.takeIf { it > 0 },
                upperThirdMm = env.threeParts?.onePart?.faceUpLength?.toFloat()?.takeIf { it > 0 }
                    ?: env.threeParts?.upper?.takeIf { it > 1f },
                middleThirdMm = env.threeParts?.twoPart?.faceMidLength?.toFloat()?.takeIf { it > 0 }
                    ?: env.threeParts?.middle?.takeIf { it > 1f },
                lowerThirdMm = env.threeParts?.threePart?.faceDownLength?.toFloat()?.takeIf { it > 0 }
                    ?: env.threeParts?.lower?.takeIf { it > 1f },
                innerEyeSpaceMm = env.eyes?.innerCorner?.toFloat()?.takeIf { it > 0 }
                    ?: env.fiveEyes?.fiveEye?.eyeEmptyLength?.toFloat()?.takeIf { it > 0 },
                rightEyeWidthMm = env.fiveEyes?.rightEye?.toFloat()?.takeIf { it > 0 },
                leftEyeWidthMm = env.fiveEyes?.leftEye?.toFloat()?.takeIf { it > 0 },
                goldenTriangleDeg = env.goldenTriangle?.goldenTriangle?.toFloat()?.takeIf { it > 0 },
                chinLengthMm = env.jaw?.jawLength?.toFloat()?.takeIf { it > 0 },
                chinWidthMm = env.jaw?.jawWidth?.toFloat()?.takeIf { it > 0 },
                browWidthMm = env.eyebrow?.browWidth?.toFloat()?.takeIf { it > 0 },
                browThickMm = env.eyebrow?.browThick?.toFloat()?.takeIf { it > 0 },
                browHeightMm = env.eyebrow?.browHeight?.toFloat()?.takeIf { it > 0 },
                noseAlaWidthMm = env.nose?.noseWidth?.toFloat()?.takeIf { it > 0 },
                lipHeightMm = env.mouth?.mouthHeight?.toFloat()?.takeIf { it > 0 }
                    ?: env.mouth?.lipThickness?.toFloat()?.takeIf { it > 0 },
                source = "OEM skinFaceLandMark",
            )
        }.getOrElse { FacialMmMeasures(source = "parse error") }
    }

    fun toSkinAnalysisResult(bundle: OemAnalysisBundle, patientAge: Int): SkinAnalysisResult {
        val parametros = toParametros(bundle)
        val metrics = parametros.map { p ->
            SkinMetric(
                key = p.key,
                name = p.nombre,
                layer = if (p.grupo == com.mlh.skinanalyzer.analysis.Grupo.PROFUNDO) "profunda" else "superficial",
                score = p.score.coerceIn(0f, 10_000f),
                level = p.severidad,
                description = "Mapa OEM offline · ${p.espectroLabel} · ${p.estilo.name}.",
                causes = "Ver desglose por zona e informe visual.",
                precautions = "Consulta estética; no sustituye diagnóstico médico.",
                recommendation = defaultRecommendation(p.key),
                higherIsWorse = Severidad.mayorEsPeor(p.key),
                spectrumLabel = p.espectroLabel,
                spectrumFile = p.espectroFile,
                mediciones = p.mediciones,
                unidad = OemIndicatorCatalog.unidadFor(p.key),
                estilo = p.estilo,
            )
        }
        val facial = parseFacial(bundle.facialRatioJson)
        val facialMm = parseFacialMm(bundle.facialRatioJson)
        val priority = metrics
            .sortedByDescending { Severidad.clasificar(it.key, it.score).value }
            .take(3)
            .map { it.key }
        return SkinAnalysisResult(
            metrics = metrics,
            skinType = inferSkinType(metrics),
            skinAge = bundle.skinAge.coerceIn(18, 75).takeIf { it > 0 } ?: patientAge.coerceIn(18, 75),
            overview = bundle.overview,
            facialRatioNote = facial.note,
            priorityKeys = priority,
            facial = facial,
            facialMm = facialMm.takeIf { it.hasAny() },
            parametros = parametros,
            analysisEngine = "OEM libsalon (zonas)",
            isClinicalLicensed = false,
        )
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
        val key = OemIndicatorCatalog.keyFor(type)
        val overlay = bean.urls?.let { resolvePath(dir, it) }
            ?: OemCaptureFiles.overlayPath(dir.trimEnd('/'), type).takeIf { File(it).exists() }
        val meds = OemZoneParser.medicionesFor(key, bean.value)
        list += OemIndicatorResult(
            key = key,
            oemType = type,
            displayName = OemIndicatorCatalog.displayName(type),
            layer = OemIndicatorCatalog.layer(type),
            score = bean.score.coerceIn(0, 100),
            levelLabel = bean.level ?: "2",
            overlayPath = overlay,
            blackOverlayPath = bean.blackUrl?.let { resolvePath(dir, it) },
            mediciones = meds,
            unidad = OemIndicatorCatalog.unidadFor(key),
            estilo = OemIndicatorCatalog.estiloFor(key),
            grupo = OemIndicatorCatalog.grupoFor(key),
        )
        Log.i(TAG, "OEM $key score=${bean.score} zones=${meds.joinToString { "${it.zona.label}=${it.valor}" }}")
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
            moisture < 40f -> "Seca"
            else -> "Normal"
        }
    }

    private fun defaultRecommendation(key: String): String = when (key) {
        "sebum" -> "Limpieza equilibrada y control de sebo."
        "pores" -> "BHA suave y fotoprotección."
        "pigmentation", "deep_pigment" -> "Vitamina C + FPS estricto."
        "wrinkles" -> "Retinoides suaves e hidratación."
        "acne", "deep_acne", "scars" -> "Tratamiento antiinflamatorio; no manipular."
        "porphyrin" -> "Higiene profunda y protocolo antibacteriano suave."
        "moisture" -> "Humectantes + barrera cutánea."
        "sensitivity" -> "Calmar barrera; evitar activos fuertes."
        "collagen" -> "Estímulo de colágeno y fotoprotección."
        "uv_spots" -> "FPS amplio espectro y antioxidantes."
        "blackheads" -> "BHA y limpieza enzimática."
        "dark_circles" -> "Contorno y descanso; valorar vascular/pigmento."
        else -> "Ver mapa del indicador y catálogo local de cuidado."
    }

    private fun String.ensureTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"

    fun close() = landmarks.close()

    companion object {
        private const val TAG = "OemSkinEngine"
    }
}

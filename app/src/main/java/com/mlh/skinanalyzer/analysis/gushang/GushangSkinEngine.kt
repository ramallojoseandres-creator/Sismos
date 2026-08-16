package com.mlh.skinanalyzer.analysis.gushang

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.gushang.skindetect.JniInterface
import com.mlh.skinanalyzer.analysis.Severidad
import com.mlh.skinanalyzer.analysis.SkinAnalysisResult
import com.mlh.skinanalyzer.analysis.SkinMetric
import com.mlh.skinanalyzer.analysis.oem.OemCaptureFiles
import com.mlh.skinanalyzer.analysis.oem.OemFaceLandmarks
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Motor Gushang SkinDetect — 15 parámetros OEM (7 Superficie + 8 Profundo).
 * LEC = variantes profundas cuando existen en libSkinDetect.so.
 */
class GushangSkinEngine(context: Context) {
    private val app = context.applicationContext
    private val landmarks = OemFaceLandmarks(app)

    fun canAnalyze(sessionDir: String): Boolean {
        if (!GushangLicense.ensureRegistered()) return false
        val dir = File(sessionDir)
        if (!dir.isDirectory) return false
        return OemCaptureFiles.requiredSources.all { File(dir, it).exists() } &&
            OemFaceLandmarks.isAvailable(app)
    }

    fun analyze(sessionDir: String, patientAge: Int): SkinAnalysisResult? {
        if (!GushangLicense.ensureRegistered()) {
            Log.e(TAG, "analyze blocked — license not activated")
            return null
        }
        if (!canAnalyze(sessionDir)) return null
        val lm = landmarks.extract(sessionDir) ?: return null
        val dir = File(sessionDir)
        val outDir = File(dir, "gushang").also { it.mkdirs() }

        return try {
            val metrics = mutableListOf<SkinMetric>()

            fun pack(xs: FloatArray, ys: FloatArray, imageFile: File): Triple<ByteArray, Int, Int> {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
                val w = bounds.outWidth.coerceAtLeast(1)
                val h = bounds.outHeight.coerceAtLeast(1)
                val n = minOf(xs.size, ys.size, 478)
                val buf = ByteBuffer.allocate(n * 8).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until n) {
                    buf.putFloat(xs[i] * w)
                    buf.putFloat(ys[i] * h)
                }
                return Triple(buf.array(), w, h)
            }

            fun callJni(
                label: String,
                primary: (String, String, ByteArray, Int, Int) -> Float,
                fallback: ((String, String, ByteArray, Int, Int) -> Float)?,
                src: String,
                out: String,
                bytes: ByteArray,
                w: Int,
                h: Int,
            ): Float {
                return try {
                    primary(src, out, bytes, w, h)
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "$label LEC/native missing — fallback", e)
                    if (fallback != null) {
                        try {
                            fallback(src, out, bytes, w, h)
                        } catch (e2: Throwable) {
                            Log.e(TAG, "$label fallback failed", e2)
                            -1f
                        }
                    } else {
                        -1f
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "$label failed", e)
                    -1f
                }
            }

            fun runMetric(
                key: String,
                name: String,
                layer: String,
                srcName: String,
                spectrumLabel: String,
                xs: FloatArray,
                ys: FloatArray,
                mayorEsPeor: Boolean,
                primary: (String, String, ByteArray, Int, Int) -> Float,
                fallback: ((String, String, ByteArray, Int, Int) -> Float)? = null,
            ) {
                val src = File(dir, srcName)
                if (!src.exists()) {
                    Log.w(TAG, "$key skipped — missing $srcName")
                    return
                }
                val (bytes, w, h) = pack(xs, ys, src)
                val out = File(outDir, "${key}_out.jpg").absolutePath
                val score = callJni(key, primary, fallback, src.absolutePath, out, bytes, w, h)
                if (score < 0f) return
                val s = score.coerceIn(0f, 100f)
                val level = Severidad.clasificar(s, mayorEsPeor)
                metrics += SkinMetric(
                    key = key,
                    name = name,
                    layer = layer,
                    score = s,
                    level = level,
                    description = "Indicador Gushang SkinDetect · espectro $spectrumLabel.",
                    causes = "Ver mapa / valor cuantitativo del motor licenciado.",
                    precautions = "Análisis cosmético; no es diagnóstico médico.",
                    recommendation = "Consulte el informe y el criterio clínico.",
                    higherIsWorse = mayorEsPeor,
                    spectrumLabel = spectrumLabel,
                    spectrumFile = srcName,
                )
                Log.i(TAG, "$key=$s level=${level.label} spectrum=$spectrumLabel file=$srcName")
            }

            // ── Superficie (7) ──────────────────────────────────────────
            runMetric(
                "sebum", "Aceite", "superficial", OemCaptureFiles.POSITIVE, "Polarizada paralela (PPL)",
                lm.positiveX, lm.positiveY, mayorEsPeor = true,
                primary = JniInterface::skinOilContent,
            )
            runMetric(
                "pigmentation", "Pigmentación", "superficial", OemCaptureFiles.WHITE, "Luz blanca",
                lm.whiteX, lm.whiteY, mayorEsPeor = true,
                primary = JniInterface::skinSplotColor,
            )
            runMetric(
                "pores", "Poros", "superficial", OemCaptureFiles.POSITIVE, "Polarizada paralela (PPL)",
                lm.positiveX, lm.positiveY, mayorEsPeor = true,
                primary = JniInterface::skinCuticle,
            )
            runMetric(
                "wrinkles", "Arrugas", "superficial", OemCaptureFiles.WHITE, "Luz blanca",
                lm.whiteX, lm.whiteY, mayorEsPeor = false,
                primary = JniInterface::skinElasticity,
            )
            runMetric(
                "scars", "Marcas de acné", "superficial", OemCaptureFiles.NEGATIVE, "Polarizada cruzada (XPL)",
                lm.negativeX, lm.negativeY, mayorEsPeor = true,
                primary = JniInterface::skinScars,
            )
            runMetric(
                "dark_circles", "Ojeras", "superficial", OemCaptureFiles.WHITE, "Luz blanca",
                lm.whiteX, lm.whiteY, mayorEsPeor = false,
                primary = JniInterface::skinWhiteness,
            )
            runMetric(
                "blackheads", "Puntos negros", "superficial", OemCaptureFiles.POSITIVE, "Polarizada paralela (PPL)",
                lm.positiveX, lm.positiveY, mayorEsPeor = true,
                primary = JniInterface::skinBlackheads,
            )

            // ── Profundo (8) ────────────────────────────────────────────
            runMetric(
                "moisture", "Humedad", "profunda", OemCaptureFiles.NEGATIVE, "Polarizada cruzada (XPL)",
                lm.negativeX, lm.negativeY, mayorEsPeor = false,
                primary = JniInterface::skinWaterContent,
            )
            runMetric(
                "sensitivity", "Sensibilidad", "profunda", OemCaptureFiles.RED, "Luz roja",
                lm.redX, lm.redY, mayorEsPeor = true,
                primary = JniInterface::skinSensitivityLEC,
                fallback = JniInterface::skinSensitivity,
            )
            runMetric(
                "acne", "Acné", "profunda", OemCaptureFiles.BLUE, "Luz azul",
                lm.blueX, lm.blueY, mayorEsPeor = true,
                primary = JniInterface::skinAcneLEC,
                fallback = JniInterface::skinAcne,
            )
            runMetric(
                "collagen", "Colágeno", "profunda", OemCaptureFiles.NEGATIVE, "Polarizada cruzada (XPL)",
                lm.negativeX, lm.negativeY, mayorEsPeor = false,
                primary = JniInterface::skinElasticityLEC,
                fallback = JniInterface::skinElasticity,
            )
            runMetric(
                "uv_spots", "Manchas UV", "profunda", OemCaptureFiles.UV, "Ultravioleta",
                lm.uvX, lm.uvY, mayorEsPeor = true,
                primary = JniInterface::skinHeavyMetal,
            )
            runMetric(
                "deep_pigment", "Pigmentación profunda", "profunda", OemCaptureFiles.WSG, "Luz de Wood",
                lm.wsgX, lm.wsgY, mayorEsPeor = true,
                primary = JniInterface::skinSplotColor,
            )
            runMetric(
                "deep_acne", "Acné (inflamación)", "profunda", OemCaptureFiles.BLUE, "Luz azul",
                lm.blueX, lm.blueY, mayorEsPeor = true,
                primary = JniInterface::skinAcneInflammation,
            )
            runMetric(
                "porphyrin", "Porfirinas", "profunda", OemCaptureFiles.UV, "Ultravioleta",
                lm.uvX, lm.uvY, mayorEsPeor = true,
                primary = JniInterface::skinExudates,
            )

            // Mapas faciales
            val whiteSrc = File(dir, OemCaptureFiles.WHITE)
            if (whiteSrc.exists()) {
                val (bytes, w, h) = pack(lm.whiteX, lm.whiteY, whiteSrc)
                val heatOut = File(outDir, "heatmap.jpg").absolutePath
                val threeDOut = File(outDir, "three_d.jpg").absolutePath
                val heatRc = runCatching {
                    JniInterface.skinHeatMap(whiteSrc.absolutePath, heatOut, bytes, w, h)
                }.onFailure { Log.e(TAG, "skinHeatMap failed", it) }.getOrDefault(-1)
                val threeRc = runCatching {
                    JniInterface.skinThreeDImage(whiteSrc.absolutePath, threeDOut, bytes, w, h)
                }.onFailure { Log.e(TAG, "skinThreeDImage failed", it) }.getOrDefault(-1)
                Log.i(TAG, "skinHeatMap rc=$heatRc outExists=${File(heatOut).exists()}")
                Log.i(TAG, "skinThreeDImage rc=$threeRc outExists=${File(threeDOut).exists()}")
            }

            if (metrics.isEmpty()) return null
            val priority = metrics
                .sortedByDescending { Severidad.attentionScore(it.score, it.higherIsWorse) }
                .take(3)
                .map { it.key }
            SkinAnalysisResult(
                metrics = metrics,
                skinType = inferType(metrics),
                skinAge = patientAge.coerceIn(18, 75),
                overview = "Prioridades: " +
                    metrics.sortedByDescending { Severidad.attentionScore(it.score, it.higherIsWorse) }
                        .take(3)
                        .joinToString { "${it.name} (${it.level.label}, ${it.score.toInt()})" },
                facialRatioNote = "Landmarks MediaPipe → SkinDetect licenciado (15 parámetros).",
                priorityKeys = priority,
                facial = null,
                analysisEngine = SkinAnalysisResult.ENGINE_GUSHANG,
                isClinicalLicensed = true,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gushang analyze failed", e)
            null
        }
    }

    fun close() = landmarks.close()

    private fun inferType(metrics: List<SkinMetric>): String {
        val sebum = metrics.firstOrNull { it.key == "sebum" }?.score ?: 30f
        val moisture = metrics.firstOrNull { it.key == "moisture" }?.score ?: 30f
        return when {
            sebum > 55f && moisture < 40f -> "Mixta grasa"
            sebum > 55f -> "Grasa"
            moisture < 40f -> "Seca"
            else -> "Normal"
        }
    }

    companion object {
        private const val TAG = "GushangSkin"
    }
}

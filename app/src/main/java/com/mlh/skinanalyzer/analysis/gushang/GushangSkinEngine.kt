package com.mlh.skinanalyzer.analysis.gushang

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.gushang.skindetect.JniInterface
import com.mlh.skinanalyzer.analysis.CareLevel
import com.mlh.skinanalyzer.analysis.SkinAnalysisResult
import com.mlh.skinanalyzer.analysis.SkinMetric
import com.mlh.skinanalyzer.analysis.oem.OemCaptureFiles
import com.mlh.skinanalyzer.analysis.oem.OemFaceLandmarks
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Motor Gushang SkinDetect (JNI) sobre las 8 capturas OEM.
 * Landmarks: MediaPipe → byte[] float LE (x,y) en píxeles.
 */
class GushangSkinEngine(context: Context) {
    private val app = context.applicationContext
    private val landmarks = OemFaceLandmarks(app)

    fun canAnalyze(sessionDir: String): Boolean {
        if (!GushangLicense.isActivated) return false
        val dir = File(sessionDir)
        if (!dir.isDirectory) return false
        return OemCaptureFiles.requiredSources.all { File(dir, it).exists() } &&
            OemFaceLandmarks.isAvailable(app)
    }

    fun analyze(sessionDir: String, patientAge: Int): SkinAnalysisResult? {
        // Hard gate: never invent scores without a valid license.
        if (!GushangLicense.isActivated) {
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

            fun runFloat(
                key: String,
                name: String,
                layer: String,
                srcName: String,
                xs: FloatArray,
                ys: FloatArray,
                fn: (String, String, ByteArray, Int, Int) -> Float,
            ) {
                val src = File(dir, srcName)
                if (!src.exists()) return
                val (bytes, w, h) = pack(xs, ys, src)
                val out = File(outDir, "${key}_out.jpg").absolutePath
                val score = runCatching {
                    fn(src.absolutePath, out, bytes, w, h)
                }.onFailure {
                    Log.e(TAG, "JNI $key failed", it)
                }.getOrDefault(-1f)
                if (score < 0f) return
                val s = score.coerceIn(0f, 100f)
                metrics += SkinMetric(
                    key = key,
                    name = name,
                    layer = layer,
                    score = s,
                    level = CareLevel.fromScore(s),
                    description = "Indicador Gushang SkinDetect (offline, licenciado).",
                    causes = "Ver mapa / valor cuantitativo del motor licenciado.",
                    precautions = "Análisis cosmético; no es diagnóstico médico.",
                    recommendation = "Consulte el informe y el criterio clínico.",
                )
                Log.i(TAG, "$key=$score")
            }

            runFloat("sebum", "Sebo", "surface", OemCaptureFiles.NEGATIVE, lm.negativeX, lm.negativeY, JniInterface::skinOilContent)
            runFloat("moisture", "Hidratación", "surface", OemCaptureFiles.WHITE, lm.whiteX, lm.whiteY, JniInterface::skinWaterContent)
            runFloat("tone", "Luminosidad", "surface", OemCaptureFiles.WHITE, lm.whiteX, lm.whiteY, JniInterface::skinWhiteness)
            runFloat("elasticity", "Elasticidad", "deep", OemCaptureFiles.WHITE, lm.whiteX, lm.whiteY, JniInterface::skinElasticity)
            runFloat("sensitivity", "Sensibilidad", "deep", OemCaptureFiles.POSITIVE, lm.positiveX, lm.positiveY, JniInterface::skinSensitivity)
            runFloat("acne", "Acné", "surface", OemCaptureFiles.NEGATIVE, lm.negativeX, lm.negativeY, JniInterface::skinAcne)
            runFloat("blackheads", "Puntos negros", "surface", OemCaptureFiles.NEGATIVE, lm.negativeX, lm.negativeY, JniInterface::skinBlackheads)
            runFloat("scars", "Cicatrices / marcas", "surface", OemCaptureFiles.NEGATIVE, lm.negativeX, lm.negativeY, JniInterface::skinScars)
            runFloat("cuticle", "Cutícula / textura", "surface", OemCaptureFiles.WHITE, lm.whiteX, lm.whiteY, JniInterface::skinCuticle)
            runFloat("pigmentation", "Manchas", "surface", OemCaptureFiles.POSITIVE, lm.positiveX, lm.positiveY, JniInterface::skinSplotColor)
            runFloat("hair", "Vello", "surface", OemCaptureFiles.WHITE, lm.whiteX, lm.whiteY, JniInterface::skinHair)
            runFloat("exudates", "Exudados", "surface", OemCaptureFiles.NEGATIVE, lm.negativeX, lm.negativeY, JniInterface::skinExudates)
            runFloat("heavy_metal", "Metales", "deep", OemCaptureFiles.UV, lm.whiteX, lm.whiteY, JniInterface::skinHeavyMetal)
            runFloat("deep_acne", "Acné profundo", "deep", OemCaptureFiles.UV, lm.whiteX, lm.whiteY, JniInterface::skinAcneInflammation)

            if (metrics.isEmpty()) return null
            val priority = metrics.sortedByDescending { it.score }.take(3).map { it.key }
            SkinAnalysisResult(
                metrics = metrics,
                skinType = inferType(metrics),
                skinAge = patientAge.coerceIn(18, 75),
                overview = "Prioridades Gushang: " +
                    metrics.sortedByDescending { it.score }.take(3)
                        .joinToString { "${it.name} (${it.score.toInt()})" },
                facialRatioNote = "Landmarks MediaPipe → SkinDetect licenciado.",
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
            moisture > 55f -> "Seca"
            else -> "Normal"
        }
    }

    companion object {
        private const val TAG = "GushangSkin"
    }
}

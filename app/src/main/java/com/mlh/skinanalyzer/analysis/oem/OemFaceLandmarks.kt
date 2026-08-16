package com.mlh.skinanalyzer.analysis.oem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.mlh.skinanalyzer.hardware.CapturePrefs
import com.zeze.faceDetection.FaceDetectionJni
import java.io.File

/**
 * Extracts 478 normalized landmarks (OEM FaceMesh format) via MediaPipe Tasks.
 */
class OemFaceLandmarks(context: Context) {
    private val landmarker: FaceLandmarker?

    init {
        landmarker = try {
            val opts = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("face_landmarker.task")
                        .build(),
                )
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .setOutputFaceBlendshapes(false)
                .setOutputFacialTransformationMatrixes(false)
                .build()
            FaceLandmarker.createFromOptions(context, opts)
        } catch (e: Exception) {
            Log.e(TAG, "FaceLandmarker init failed", e)
            null
        }
    }

    data class LandmarkSet(
        val whiteX: FloatArray,
        val whiteY: FloatArray,
        val negativeX: FloatArray,
        val negativeY: FloatArray,
        val positiveX: FloatArray,
        val positiveY: FloatArray,
    )

    fun extract(sessionDir: String): LandmarkSet? {
        val lm = landmarker ?: return null
        val white = loadLandmarks(lm, File(sessionDir, OemCaptureFiles.WHITE)) ?: return null
        val neg = loadLandmarks(lm, File(sessionDir, OemCaptureFiles.NEGATIVE)) ?: white
        val pos = loadLandmarks(lm, File(sessionDir, OemCaptureFiles.POSITIVE)) ?: white
        return LandmarkSet(
            whiteX = white.first,
            whiteY = white.second,
            negativeX = neg.first,
            negativeY = neg.second,
            positiveX = pos.first,
            positiveY = pos.second,
        )
    }

    private fun loadLandmarks(lm: FaceLandmarker, file: File): Pair<FloatArray, FloatArray>? {
        if (!file.exists()) return null
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return try {
            val mp = BitmapImageBuilder(bmp).build()
            val result: FaceLandmarkerResult = lm.detect(mp)
            if (result.faceLandmarks().isEmpty()) return null
            val points = result.faceLandmarks()[0]
            val xs = FloatArray(FaceDetectionJni.LANDMARK_COUNT)
            val ys = FloatArray(FaceDetectionJni.LANDMARK_COUNT)
            val n = minOf(points.size, FaceDetectionJni.LANDMARK_COUNT)
            for (i in 0 until n) {
                xs[i] = points[i].x()
                ys[i] = points[i].y()
            }
            xs to ys
        } catch (e: Exception) {
            Log.e(TAG, "landmarks failed for ${file.name}", e)
            null
        } finally {
            bmp.recycle()
        }
    }

    fun hasFace(bitmap: Bitmap): Boolean {
        val lm = landmarker ?: return false
        return try {
            val mp = BitmapImageBuilder(bitmap).build()
            val result = lm.detect(mp)
            result.faceLandmarks().isNotEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "hasFace failed", e)
            false
        }
    }

    fun close() {
        runCatching { landmarker?.close() }
    }

    companion object {
        private const val TAG = "OemLandmarks"
        fun isAvailable(context: Context): Boolean = runCatching {
            context.assets.open("face_landmarker.task").close()
            true
        }.getOrDefault(false)

        /** Prueba 270/90/180/0 sobre [raw]; guarda el primero con cara. */
        fun detectBestRotation(context: Context, raw: Bitmap): Int {
            val helper = OemFaceLandmarks(context)
            try {
                for (deg in intArrayOf(90, 270, 180, 0)) {
                    val trial = CapturePrefs.transformBitmap(raw, deg, false)
                    val ok = helper.hasFace(trial)
                    if (trial !== raw) runCatching { trial.recycle() }
                    if (ok) {
                        Log.i(TAG, "Rotación detectada: $deg")
                        return deg
                    }
                }
            } finally {
                helper.close()
            }
            Log.w(TAG, "Sin cara en ningún ángulo — usando ${CapturePrefs.DEFAULT_ROTATION_DEG}")
            return CapturePrefs.DEFAULT_ROTATION_DEG
        }
    }
}

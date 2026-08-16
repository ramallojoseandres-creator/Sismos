package com.mlh.skinanalyzer.hardware

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log

/**
 * Preferencias de captura. Rotación en píxeles (no EXIF).
 * Default 90°; se calibra una vez con MediaPipe y se guarda.
 */
object CapturePrefs {
    private const val TAG = "CapturePrefs"
    private const val PREFS = "mlh_prefs"
    private const val KEY_ROTATION = "capture_rotation_deg"
    private const val KEY_ROTATION_SET = "capture_rotation_calibrated"
    private const val KEY_SETTLE_FIRST = "capture_settle_first_ms"
    private const val KEY_SETTLE_BETWEEN = "capture_settle_between_ms"
    private const val KEY_SETTLE_AFTER = "capture_settle_after_ms"
    private const val KEY_PRE_FIRST = "capture_pre_first_ms"

    const val DEFAULT_ROTATION_DEG = 90
    const val MIRROR_HORIZONTAL = false

    const val DEFAULT_SETTLE_FIRST_MS = 2_000L
    const val DEFAULT_SETTLE_BETWEEN_MS = 2_000L
    const val DEFAULT_SETTLE_AFTER_MS = 1_500L
    const val DEFAULT_PRE_FIRST_MS = 2_500L

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun captureRotationDeg(ctx: Context): Int =
        prefs(ctx).getInt(KEY_ROTATION, DEFAULT_ROTATION_DEG)

    fun isRotationCalibrated(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ROTATION_SET, false)

    fun setCaptureRotationDeg(ctx: Context, deg: Int) {
        val allowed = setOf(0, 90, 180, 270)
        val value = if (deg in allowed) deg else DEFAULT_ROTATION_DEG
        prefs(ctx).edit()
            .putInt(KEY_ROTATION, value)
            .putBoolean(KEY_ROTATION_SET, true)
            .apply()
        Log.i(TAG, "Rotación guardada: $value")
    }

    fun clearRotationCalibration(ctx: Context) {
        prefs(ctx).edit()
            .remove(KEY_ROTATION)
            .remove(KEY_ROTATION_SET)
            .apply()
        Log.i(TAG, "Calibración de orientación borrada")
    }

    fun settleFirstMs(ctx: Context): Long = prefs(ctx).getLong(KEY_SETTLE_FIRST, DEFAULT_SETTLE_FIRST_MS)
    fun settleBetweenMs(ctx: Context): Long = prefs(ctx).getLong(KEY_SETTLE_BETWEEN, DEFAULT_SETTLE_BETWEEN_MS)
    fun settleAfterMs(ctx: Context): Long = prefs(ctx).getLong(KEY_SETTLE_AFTER, DEFAULT_SETTLE_AFTER_MS)
    fun preFirstMs(ctx: Context): Long = prefs(ctx).getLong(KEY_PRE_FIRST, DEFAULT_PRE_FIRST_MS)

    fun setTimings(
        ctx: Context,
        settleFirst: Long,
        settleBetween: Long,
        settleAfter: Long,
        preFirst: Long,
    ) {
        prefs(ctx).edit()
            .putLong(KEY_SETTLE_FIRST, settleFirst.coerceAtLeast(1_500L))
            .putLong(KEY_SETTLE_BETWEEN, settleBetween.coerceAtLeast(1_500L))
            .putLong(KEY_SETTLE_AFTER, settleAfter.coerceAtLeast(500L))
            .putLong(KEY_PRE_FIRST, preFirst.coerceAtLeast(0L))
            .apply()
    }

    fun restoreDefaults(ctx: Context) {
        prefs(ctx).edit()
            .putLong(KEY_SETTLE_FIRST, DEFAULT_SETTLE_FIRST_MS)
            .putLong(KEY_SETTLE_BETWEEN, DEFAULT_SETTLE_BETWEEN_MS)
            .putLong(KEY_SETTLE_AFTER, DEFAULT_SETTLE_AFTER_MS)
            .putLong(KEY_PRE_FIRST, DEFAULT_PRE_FIRST_MS)
            .apply()
    }

    /**
     * Rota el bitmap en píxeles. Siempre crea bitmap nuevo.
     * NO usar EXIF — el .so lo ignora.
     */
    fun transformBitmap(src: Bitmap, rotationDeg: Int, mirrorH: Boolean = MIRROR_HORIZONTAL): Bitmap {
        val deg = ((rotationDeg % 360) + 360) % 360
        if (deg == 0 && !mirrorH) {
            return src.copy(Bitmap.Config.ARGB_8888, false)
        }
        val m = Matrix()
        if (deg != 0) m.postRotate(deg.toFloat())
        if (mirrorH) m.postScale(-1f, 1f)
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        Log.d(TAG, "transform $deg° ${src.width}x${src.height} → ${out.width}x${out.height}")
        return out
    }

    fun rotationDeg(ctx: Context): Int = captureRotationDeg(ctx)
    fun mirrorHorizontal(@Suppress("UNUSED_PARAMETER") ctx: Context): Boolean = MIRROR_HORIZONTAL
}

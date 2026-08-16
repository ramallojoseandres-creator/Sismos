package com.mlh.skinanalyzer.hardware

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Matrix

/**
 * Preferencias de captura. Rotación fijada a 90° en píxeles (CORRECCIONES V4).
 * Espejo en archivo: OFF — lateralidad izquierda/derecha del informe.
 */
object CapturePrefs {
    private const val PREFS = "mlh_prefs"
    private const val KEY_SETTLE_FIRST = "capture_settle_first_ms"
    private const val KEY_SETTLE_BETWEEN = "capture_settle_between_ms"
    private const val KEY_SETTLE_AFTER = "capture_settle_after_ms"
    private const val KEY_PRE_FIRST = "capture_pre_first_ms"

    /** Fijo para MJ-008 / ZK-R36A (V4). Se graba en píxeles, no EXIF. */
    const val CAPTURE_ROTATION_DEG = 90
    /** No espejar el JPEG: el motor distingue cara izquierda/derecha. */
    const val MIRROR_HORIZONTAL = false

    const val DEFAULT_SETTLE_FIRST_MS = 2_000L
    const val DEFAULT_SETTLE_BETWEEN_MS = 2_000L
    const val DEFAULT_SETTLE_AFTER_MS = 1_500L
    const val DEFAULT_PRE_FIRST_MS = 2_500L

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun rotationDeg(@Suppress("UNUSED_PARAMETER") ctx: Context): Int = CAPTURE_ROTATION_DEG
    fun mirrorHorizontal(@Suppress("UNUSED_PARAMETER") ctx: Context): Boolean = MIRROR_HORIZONTAL

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
     * Rota el bitmap en píxeles antes de comprimir JPEG.
     * Orden V4: postRotate(90); espejo solo si [MIRROR_HORIZONTAL] (hoy false).
     */
    fun transformBitmap(src: Bitmap, rotationDeg: Int = CAPTURE_ROTATION_DEG, mirrorH: Boolean = MIRROR_HORIZONTAL): Bitmap {
        if (rotationDeg % 360 == 0 && !mirrorH) return src
        val m = Matrix()
        if (rotationDeg % 360 != 0) m.postRotate(rotationDeg.toFloat())
        if (mirrorH) m.postScale(-1f, 1f)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}

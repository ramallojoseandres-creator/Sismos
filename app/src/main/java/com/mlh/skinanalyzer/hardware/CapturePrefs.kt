package com.mlh.skinanalyzer.hardware

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Matrix

/**
 * Preferencias de cámara / captura (calibración en tablet sin recompilar).
 * Defaults sugeridos para MJ-008 frontal: rotación 270 + espejo.
 */
object CapturePrefs {
    private const val PREFS = "mlh_prefs"
    private const val KEY_ROTATION = "camera_rotation_deg"
    private const val KEY_MIRROR = "camera_mirror_h"
    private const val KEY_SETTLE_FIRST = "capture_settle_first_ms"
    private const val KEY_SETTLE_BETWEEN = "capture_settle_between_ms"
    private const val KEY_SETTLE_AFTER = "capture_settle_after_ms"
    private const val KEY_PRE_FIRST = "capture_pre_first_ms"

    /** Defaults from CORRECCIONES PDF (~5 s/luz). */
    const val DEFAULT_ROTATION = 270
    const val DEFAULT_MIRROR = true
    const val DEFAULT_SETTLE_FIRST_MS = 2_000L
    const val DEFAULT_SETTLE_BETWEEN_MS = 2_000L
    const val DEFAULT_SETTLE_AFTER_MS = 1_500L
    const val DEFAULT_PRE_FIRST_MS = 2_500L

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun rotationDeg(ctx: Context): Int = prefs(ctx).getInt(KEY_ROTATION, DEFAULT_ROTATION)
    fun mirrorHorizontal(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_MIRROR, DEFAULT_MIRROR)
    fun settleFirstMs(ctx: Context): Long = prefs(ctx).getLong(KEY_SETTLE_FIRST, DEFAULT_SETTLE_FIRST_MS)
    fun settleBetweenMs(ctx: Context): Long = prefs(ctx).getLong(KEY_SETTLE_BETWEEN, DEFAULT_SETTLE_BETWEEN_MS)
    fun settleAfterMs(ctx: Context): Long = prefs(ctx).getLong(KEY_SETTLE_AFTER, DEFAULT_SETTLE_AFTER_MS)
    fun preFirstMs(ctx: Context): Long = prefs(ctx).getLong(KEY_PRE_FIRST, DEFAULT_PRE_FIRST_MS)

    fun setRotationDeg(ctx: Context, deg: Int) {
        val allowed = setOf(0, 90, 180, 270)
        val value = if (deg in allowed) deg else DEFAULT_ROTATION
        prefs(ctx).edit().putInt(KEY_ROTATION, value).apply()
    }

    fun setMirrorHorizontal(ctx: Context, mirror: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_MIRROR, mirror).apply()
    }

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
            .putInt(KEY_ROTATION, DEFAULT_ROTATION)
            .putBoolean(KEY_MIRROR, DEFAULT_MIRROR)
            .putLong(KEY_SETTLE_FIRST, DEFAULT_SETTLE_FIRST_MS)
            .putLong(KEY_SETTLE_BETWEEN, DEFAULT_SETTLE_BETWEEN_MS)
            .putLong(KEY_SETTLE_AFTER, DEFAULT_SETTLE_AFTER_MS)
            .putLong(KEY_PRE_FIRST, DEFAULT_PRE_FIRST_MS)
            .apply()
    }

    /** Aplica rotación y espejo al bitmap antes de guardar en disco. */
    fun transformBitmap(src: Bitmap, rotationDeg: Int, mirrorH: Boolean): Bitmap {
        if (rotationDeg % 360 == 0 && !mirrorH) return src
        val m = Matrix()
        if (mirrorH) m.preScale(-1f, 1f)
        if (rotationDeg % 360 != 0) m.postRotate(rotationDeg.toFloat())
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}

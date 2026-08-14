package com.mlh.skinanalyzer.analysis

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Offline stand-in for OEM cloud endpoint `landmark-lai/three_five_eyes`
 * (ai.aiskin.vip). Approximates classic 三庭五眼 (3 courts / 5 eyes) ratios from
 * a white-light face frame without uploading images.
 */
object FacialProportionAnalyzer {

    data class Result(
        val upperThird: Float,
        val middleThird: Float,
        val lowerThird: Float,
        val eyeUnits: Float,
        val faceWidthUnits: Float,
        val symmetryScore: Float,
        val note: String,
        val summary: String,
    )

    fun analyze(white: Bitmap?): Result {
        if (white == null) {
            return Result(
                upperThird = 0.33f,
                middleThird = 0.34f,
                lowerThird = 0.33f,
                eyeUnits = 5f,
                faceWidthUnits = 5f,
                symmetryScore = 0.5f,
                note = "Sin imagen blanca para proporciones faciales.",
                summary = "Proporciones no calculadas.",
            )
        }
        val face = estimateFaceBox(white)
        val (l, t, r, b) = face
        val h = max(1, b - t).toFloat()
        val w = max(1, r - l).toFloat()

        // Vertical thirds inside face box (hairline→brow, brow→nose, nose→chin)
        val upper = 0.30f + (luminanceBand(white, l, t, r, t + (h * 0.33f).toInt()) - 0.45f) * 0.08f
        val middle = 0.34f + (edgeDensity(white, l, t + (h * 0.33f).toInt(), r, t + (h * 0.66f).toInt()) - 0.2f) * 0.05f
        val lower = 1f - upper.coerceIn(0.22f, 0.4f) - middle.coerceIn(0.25f, 0.42f)
        val u = upper.coerceIn(0.22f, 0.4f)
        val m = middle.coerceIn(0.25f, 0.42f)
        val lo = (1f - u - m).coerceIn(0.22f, 0.45f)

        // Horizontal: ideal face ≈ 5 eye-widths
        val eyeW = estimateEyeWidth(white, l, t, r, b)
        val eyeUnits = if (eyeW > 1f) (w / eyeW).coerceIn(4.2f, 5.8f) else 5f
        val symmetry = symmetryScore(white, l, t, r, b)

        val note = buildString {
            append("Proporciones 3/5 (offline): ")
            append("tercios ${pct(u)} / ${pct(m)} / ${pct(lo)}; ")
            append("ancho ≈ ${"%.1f".format(eyeUnits)} ojos; ")
            append("simetría ${(symmetry * 100).roundToInt()}%. ")
            append("Ideal clásico ≈ 33/33/33 y 5 ojos.")
        }
        val summary = when {
            abs(u - lo) > 0.08f || abs(eyeUnits - 5f) > 0.45f ->
                "Leve desviación respecto al canon 3/5; útil para plan estético, no diagnóstico."
            else -> "Proporciones cercanas al canon clásico 3 tercios / 5 ojos."
        }
        return Result(u, m, lo, eyeUnits, 5f, symmetry, note, summary)
    }

    private fun pct(v: Float) = "${(v * 100).roundToInt()}%"

    private fun estimateFaceBox(bmp: Bitmap): IntArray {
        val w = bmp.width
        val h = bmp.height
        // Skin-tone bounding box (center-weighted)
        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0
        val step = max(2, min(w, h) / 120)
        var hits = 0
        var y = (h * 0.08f).toInt()
        while (y < h * 0.95f) {
            var x = (w * 0.12f).toInt()
            while (x < w * 0.88f) {
                if (isSkin(bmp.getPixel(x, y))) {
                    hits++
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
                x += step
            }
            y += step
        }
        if (hits < 40) {
            return intArrayOf((w * 0.2f).toInt(), (h * 0.12f).toInt(), (w * 0.8f).toInt(), (h * 0.92f).toInt())
        }
        val padX = ((maxX - minX) * 0.04f).toInt()
        val padY = ((maxY - minY) * 0.04f).toInt()
        return intArrayOf(
            (minX - padX).coerceAtLeast(0),
            (minY - padY).coerceAtLeast(0),
            (maxX + padX).coerceAtMost(w - 1),
            (maxY + padY).coerceAtMost(h - 1),
        )
    }

    private fun isSkin(c: Int): Boolean {
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        return r > 60 && g > 40 && b > 20 &&
            r > b && r > g - 15 &&
            abs(r - g) > 8 &&
            (r - b) > 15
    }

    private fun luminanceBand(bmp: Bitmap, l: Int, t: Int, r: Int, b: Int): Float {
        var sum = 0.0
        var n = 0
        val step = 4
        var y = t.coerceAtLeast(0)
        val yMax = b.coerceAtMost(bmp.height - 1)
        val xMax = r.coerceAtMost(bmp.width - 1)
        while (y <= yMax) {
            var x = l.coerceAtLeast(0)
            while (x <= xMax) {
                val p = bmp.getPixel(x, y)
                sum += (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)) / 255.0
                n++
                x += step
            }
            y += step
        }
        return if (n == 0) 0.5f else (sum / n).toFloat()
    }

    private fun edgeDensity(bmp: Bitmap, l: Int, t: Int, r: Int, b: Int): Float {
        var edges = 0
        var n = 0
        val step = 3
        var y = (t + 1).coerceAtLeast(1)
        val yMax = (b - 1).coerceAtMost(bmp.height - 2)
        val xMax = (r - 1).coerceAtMost(bmp.width - 2)
        while (y <= yMax) {
            var x = (l + 1).coerceAtLeast(1)
            while (x <= xMax) {
                val c = lum(bmp.getPixel(x, y))
                val dx = abs(c - lum(bmp.getPixel(x + 1, y)))
                val dy = abs(c - lum(bmp.getPixel(x, y + 1)))
                if (dx + dy > 40) edges++
                n++
                x += step
            }
            y += step
        }
        return if (n == 0) 0.2f else edges.toFloat() / n
    }

    private fun estimateEyeWidth(bmp: Bitmap, l: Int, t: Int, r: Int, b: Int): Float {
        val midY0 = t + ((b - t) * 0.28f).toInt()
        val midY1 = t + ((b - t) * 0.48f).toInt()
        // Find dark clusters (eyes) in mid band
        var darkRuns = mutableListOf<Int>()
        var run = 0
        val midY = (midY0 + midY1) / 2
        for (x in l until r) {
            val dark = lum(bmp.getPixel(x.coerceIn(0, bmp.width - 1), midY.coerceIn(0, bmp.height - 1))) < 90
            if (dark) run++ else {
                if (run in 4..40) darkRuns.add(run)
                run = 0
            }
        }
        if (run in 4..40) darkRuns.add(run)
        return (darkRuns.average().takeIf { !it.isNaN() } ?: ((r - l) / 5.0)).toFloat()
    }

    private fun symmetryScore(bmp: Bitmap, l: Int, t: Int, r: Int, b: Int): Float {
        val mid = (l + r) / 2
        var diff = 0.0
        var n = 0
        val step = 4
        var y = t
        while (y < b) {
            var dx = 2
            while (mid - dx >= l && mid + dx < r) {
                diff += abs(lum(bmp.getPixel(mid - dx, y.coerceIn(0, bmp.height - 1))) -
                    lum(bmp.getPixel(mid + dx, y.coerceIn(0, bmp.height - 1))))
                n++
                dx += step
            }
            y += step
        }
        if (n == 0) return 0.5f
        val mean = diff / n
        return (1.0 - (mean / 80.0)).coerceIn(0.15, 0.98).toFloat()
    }

    private fun lum(c: Int): Int =
        (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).toInt()
}

package com.mlh.skinanalyzer.analysis

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.mlh.skinanalyzer.hardware.LightMode
import java.io.File

/**
 * Synthetic face frames for **Demo / Simulación** on phone or Android Emulator
 * when there is no MJ-008 USB camera.
 */
object DemoFrameGenerator {
    fun createFrame(mode: LightMode, outFile: File, width: Int = 640, height: Int = 480): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val tint = tintFor(mode)
        canvas.drawColor(Color.rgb(18, 18, 22))

        val cx = width / 2f
        val cy = height / 2f + 10f
        val faceW = width * 0.28f
        val faceH = height * 0.42f

        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx,
                cy - faceH * 0.1f,
                faceH,
                intArrayOf(tint, Color.rgb(Color.red(tint) / 3, Color.green(tint) / 3, Color.blue(tint) / 3)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawOval(cx - faceW, cy - faceH, cx + faceW, cy + faceH, facePaint)

        val feature = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 20, 12, 8)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        // Eyes
        canvas.drawOval(cx - faceW * 0.45f, cy - faceH * 0.25f, cx - faceW * 0.15f, cy - faceH * 0.05f, feature)
        canvas.drawOval(cx + faceW * 0.15f, cy - faceH * 0.25f, cx + faceW * 0.45f, cy - faceH * 0.05f, feature)
        // Mouth
        canvas.drawArc(
            cx - faceW * 0.25f,
            cy + faceH * 0.15f,
            cx + faceW * 0.25f,
            cy + faceH * 0.45f,
            20f,
            140f,
            false,
            feature,
        )

        // Speckle “spots” so heuristic metrics vary by light mode
        val speck = Paint(Paint.ANTI_ALIAS_FLAG)
        val seed = mode.ordinal * 17 + 3
        for (i in 0 until 18) {
            val x = cx + ((seed * (i + 1) * 13) % 100 - 50) / 100f * faceW * 1.4f
            val y = cy + ((seed * (i + 3) * 7) % 100 - 50) / 100f * faceH * 1.4f
            speck.color = Color.argb(70 + (i % 5) * 10, 180, 90, 40)
            canvas.drawCircle(x, y, 3f + (i % 4), speck)
        }

        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            isFakeBoldText = true
        }
        canvas.drawText("DEMO · ${mode.shortName}", 24f, 40f, label)

        outFile.parentFile?.mkdirs()
        outFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return bmp
    }

    private fun tintFor(mode: LightMode): Int = when (mode) {
        LightMode.WHITE -> Color.rgb(220, 190, 170)
        LightMode.XPL -> Color.rgb(200, 185, 175)
        LightMode.PPL -> Color.rgb(210, 175, 160)
        LightMode.WOODS -> Color.rgb(120, 200, 140)
        LightMode.UV -> Color.rgb(90, 70, 180)
        LightMode.BLUE -> Color.rgb(80, 120, 220)
        LightMode.ORANGE -> Color.rgb(230, 140, 60)
        LightMode.RED -> Color.rgb(200, 60, 70)
    }
}

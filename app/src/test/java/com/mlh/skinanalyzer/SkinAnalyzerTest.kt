package com.mlh.skinanalyzer

import android.graphics.Bitmap
import android.graphics.Color
import com.mlh.skinanalyzer.analysis.SkinAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkinAnalyzerTest {
    @Test
    fun analyzeReturnsFourteenMetrics() {
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(190, 150, 130))
        val result = SkinAnalyzer.analyze(mapOf("White" to bmp), patientAge = 35)
        assertEquals(14, result.metrics.size)
        assertTrue(result.skinAge in 18..60)
        assertTrue(result.skinType.isNotBlank())
    }

    @Test
    fun deriveSpectralMapsCreatesBlueBrownRed() {
        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(120, 80, 60))
        val maps = SkinAnalyzer.deriveSpectralMaps(bmp, bmp, bmp)
        assertTrue(maps.containsKey("Blue"))
        assertTrue(maps.containsKey("Brown"))
        assertTrue(maps.containsKey("Red"))
    }
}

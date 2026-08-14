package com.mlh.skinanalyzer.analysis.oem

import com.mlh.skinanalyzer.hardware.LightMode

/**
 * OEM capture filenames (`ConstantMain.src_*`) required by `libsalon.so`.
 */
object OemCaptureFiles {
    const val WHITE = "white.jpg"
    const val UV = "uv.jpg"
    const val BLUE = "blue.jpg"
    const val NEGATIVE = "negative.jpg"
    const val POSITIVE = "positive.jpg"
    const val WSG = "wsg.jpg"
    const val ORANGE = "orange.jpg"
    const val RED = "red.jpg"

    fun filenameFor(mode: LightMode): String = when (mode) {
        LightMode.WHITE -> WHITE
        LightMode.UV -> UV
        LightMode.BLUE -> BLUE
        LightMode.XPL -> NEGATIVE
        LightMode.PPL -> POSITIVE
        LightMode.WOODS -> WSG
        LightMode.ORANGE -> ORANGE
        LightMode.RED -> RED
    }

    fun overlayPath(sessionDir: String, type: String): String =
        "$sessionDir/dst_$type.jpg"

    val requiredSources = listOf(WHITE, NEGATIVE, POSITIVE, UV, BLUE, WSG, ORANGE, RED)
}

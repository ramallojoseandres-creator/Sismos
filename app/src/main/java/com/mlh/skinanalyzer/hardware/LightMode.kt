package com.mlh.skinanalyzer.hardware

/**
 * Spectral capture modes for MJ-008 / Moji AI Skin Tester compatible devices.
 * Physical LED groups (W/N/P/WS/UV) are driven over /dev/ttyS4.
 * Blue / Brown / Red are derived spectral maps from UV / Wood / White captures
 * (same presentation as the original 8-light report).
 */
enum class LightMode(
    val id: Int,
    val displayName: String,
    val shortName: String,
    val hardwareChannel: HardwareChannel?,
    val centerPercent: Int,
) {
    WHITE(1, "Luz blanca", "White", HardwareChannel.WHITE, 49),
    XPL(2, "Polarización negativa (XPL)", "XPL", HardwareChannel.NEGATIVE, 83),
    PPL(3, "Polarización positiva (PPL)", "PPL", HardwareChannel.POSITIVE, 90),
    WOODS(4, "Luz de Wood", "Wood's", HardwareChannel.WOODS, 100),
    UV(5, "Luz UV", "UV", HardwareChannel.UV, 65),
    BLUE(6, "Luz azul", "Blue", null, 0),
    BROWN(7, "Luz marrón", "Brown", null, 0),
    RED(8, "Luz roja", "Red", null, 0);

    companion object {
        val captureOrder = listOf(WHITE, XPL, PPL, WOODS, UV, BLUE, BROWN, RED)
        val hardwareOrder = listOf(WHITE, XPL, PPL, WOODS, UV)
    }
}

enum class HardwareChannel(val code: Int) {
    WHITE(1),
    NEGATIVE(2),
    POSITIVE(3),
    WOODS(4),
    UV(5),
}

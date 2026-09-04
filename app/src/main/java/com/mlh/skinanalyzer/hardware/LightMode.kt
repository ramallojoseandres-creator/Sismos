package com.mlh.skinanalyzer.hardware

/**
 * Spectral capture modes for **MJ-008 Maokin Miaojin** (Miaojing OEM map).
 *
 * All 8 lights are physical USB-XU channels (`00 78 cmd FF`). Capture order
 * matches `CameraSamplingActPresenter` / `TypeUtils.getLightTypeNew`:
 * white → negative → positive → Wood's → UV → blue → orange → red.
 *
 * Timing mirrors OEM `CameraSamplingActPresenter`:
 * white light ~1s after preview; ~2s settle between shots.
 */
enum class LightMode(
    val id: Int,
    val displayName: String,
    val shortName: String,
    /** OEM light code (1001…1014). */
    val oemCode: Int,
    /** USB XU command byte (payload[2]). */
    val usbCmd: Int?,
    /** Legacy UART channel when falling back to ttyS4. */
    val hardwareChannel: HardwareChannel?,
) {
    WHITE(1, "Luz blanca", "White", 1001, UsbXuLightController.CMD_WHITE, HardwareChannel.WHITE),
    XPL(2, "Polarización negativa (XPL)", "XPL", 1004, UsbXuLightController.CMD_NEGATIVE, HardwareChannel.NEGATIVE),
    PPL(3, "Polarización positiva (PPL)", "PPL", 1003, UsbXuLightController.CMD_POSITIVE, HardwareChannel.POSITIVE),
    WOODS(4, "Luz de Wood (WSG)", "Wood's", 1012, UsbXuLightController.CMD_WOODS, HardwareChannel.WOODS),
    UV(5, "Luz UV", "UV", 1002, UsbXuLightController.CMD_UV, HardwareChannel.UV),
    BLUE(6, "Luz azul", "Blue", 1005, UsbXuLightController.CMD_BLUE, null),
    ORANGE(7, "Luz naranja", "Orange", 1013, UsbXuLightController.CMD_ORANGE, null),
    RED(8, "Luz roja", "Red", 1014, UsbXuLightController.CMD_RED, null);

    fun uiColor(): androidx.compose.ui.graphics.Color = when (this) {
        WHITE -> com.mlh.skinanalyzer.ui.theme.LightColors.White
        XPL -> com.mlh.skinanalyzer.ui.theme.LightColors.Xpl
        PPL -> com.mlh.skinanalyzer.ui.theme.LightColors.Ppl
        WOODS -> com.mlh.skinanalyzer.ui.theme.LightColors.Woods
        UV -> com.mlh.skinanalyzer.ui.theme.LightColors.Uv
        BLUE -> com.mlh.skinanalyzer.ui.theme.LightColors.Blue
        ORANGE -> com.mlh.skinanalyzer.ui.theme.LightColors.Orange
        RED -> com.mlh.skinanalyzer.ui.theme.LightColors.Red
    }

    companion object {
        val captureOrder = listOf(WHITE, XPL, PPL, WOODS, UV, BLUE, ORANGE, RED)
        val hardwareOrder = captureOrder

        /** Defaults — override via [CapturePrefs] in Ajustes. */
        const val WHITE_LIGHT_DELAY_MS = 1_000L
        const val SETTLE_FIRST_MS = CapturePrefs.DEFAULT_SETTLE_FIRST_MS
        const val SETTLE_BETWEEN_MS = CapturePrefs.DEFAULT_SETTLE_BETWEEN_MS
        const val SETTLE_AFTER_SHOT_MS = CapturePrefs.DEFAULT_SETTLE_AFTER_MS
        const val PRE_FIRST_MS = CapturePrefs.DEFAULT_PRE_FIRST_MS
    }
}

enum class HardwareChannel(val code: Int) {
    WHITE(1),
    NEGATIVE(2),
    POSITIVE(3),
    WOODS(4),
    UV(5),
}

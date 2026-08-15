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

    companion object {
        val captureOrder = listOf(WHITE, XPL, PPL, WOODS, UV, BLUE, ORANGE, RED)
        val hardwareOrder = captureOrder

        /** OEM: white light message 1008 delayed 1000ms after startPreview. */
        const val WHITE_LIGHT_DELAY_MS = 1_000L
        /** [MaokinLightController.DELAY_FIRST_SHOT] — light on → first still. */
        const val SETTLE_FIRST_MS = MaokinLightController.DELAY_FIRST_SHOT
        /** [MaokinLightController.DELAY_BETWEEN] — light on → still (after first). */
        const val SETTLE_BETWEEN_MS = MaokinLightController.DELAY_BETWEEN
        /** [MaokinLightController.DELAY_AFTER_SHOT] — after still → next light. */
        const val SETTLE_AFTER_SHOT_MS = MaokinLightController.DELAY_AFTER_SHOT
    }
}

enum class HardwareChannel(val code: Int) {
    WHITE(1),
    NEGATIVE(2),
    POSITIVE(3),
    WOODS(4),
    UV(5),
}

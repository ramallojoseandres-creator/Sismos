package com.mlh.skinanalyzer.hardware

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice

/**
 * Constantes y utilidades del protocolo LED MJ-008 (XU UVC).
 *
 * **No abre USB.** La única puerta al dispositivo es [USBMonitor] →
 * [Mj008UvcSession] → [MaokinLightController] sobre la misma
 * [android.hardware.usb.UsbDeviceConnection].
 *
 * Abrir aquí un segundo `openDevice` / `claimInterface` mientras UVC
 * tiene la cámara cuelga el MJ-008.
 */
object UsbXuLightController {
    const val CMD_WHITE = 0x10
    const val CMD_NEGATIVE = 0x11
    const val CMD_POSITIVE = 0x12
    const val CMD_WOODS = 0x13
    const val CMD_UV = 0x14
    const val CMD_BLUE = 0x15
    const val CMD_ORANGE = 0x16
    const val CMD_RED = 0x17
    const val ARG_ON = 0xFF
    const val ARG_OFF = 0x00

    const val UNIT_ID = 130 // 0x82
    const val LIGHT_ADDR = 55318 // 0xD816

    fun isMj008Camera(device: UsbDevice): Boolean =
        Mj008UsbDevices.isAnalyzerCamera(device)

    fun hasVideoInterface(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_VIDEO) {
                return true
            }
        }
        return false
    }

    fun describe(device: UsbDevice): String {
        val name = runCatching { device.productName }.getOrNull() ?: "USB"
        return "$name vid=${device.vendorId} pid=${device.productId}"
    }

    /** Payload OEM de 4 bytes: `00 78 cmd arg`. */
    fun lightPayload(cmd: Int, arg: Int = ARG_ON): ByteArray =
        byteArrayOf(0x00, 0x78, cmd.toByte(), arg.toByte())

    fun setupHeader(unit: Int = UNIT_ID, address: Int = LIGHT_ADDR, length: Int = 4): ByteArray =
        byteArrayOf(
            ((unit ushr 8) and 0xFF).toByte(),
            (unit and 0xFF).toByte(),
            (address and 0xFF).toByte(),
            ((address ushr 8) and 0xFF).toByte(),
            (length and 0xFF).toByte(),
            ((length ushr 8) and 0xFF).toByte(),
            ((address ushr 16) and 0xFF).toByte(),
            0,
        )
}

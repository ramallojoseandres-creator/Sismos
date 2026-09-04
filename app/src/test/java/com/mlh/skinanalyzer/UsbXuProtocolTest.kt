package com.mlh.skinanalyzer

import com.mlh.skinanalyzer.hardware.UsbXuLightController
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class UsbXuProtocolTest {
    @Test
    fun setupHeaderMatchesOemNativeLayout() {
        // unit=130 (0x82), addr=55318 (0xD816), len=4
        // bytes: 00 82 16 D8 04 00 00 00
        val header = UsbXuLightController.setupHeader()
        assertArrayEquals(
            byteArrayOf(0x00, 0x82.toByte(), 0x16, 0xD8.toByte(), 0x04, 0x00, 0x00, 0x00),
            header,
        )
    }

    @Test
    fun lightPayloadMatchesTypeUtilsGetLightTypeNew() {
        assertArrayEquals(
            byteArrayOf(0x00, 0x78, 0x10, 0xFF.toByte()),
            UsbXuLightController.lightPayload(UsbXuLightController.CMD_WHITE),
        )
        assertArrayEquals(
            byteArrayOf(0x00, 0x78, 0x11, 0xFF.toByte()),
            UsbXuLightController.lightPayload(UsbXuLightController.CMD_NEGATIVE),
        )
        assertArrayEquals(
            byteArrayOf(0x00, 0x78, 0x12, 0xFF.toByte()),
            UsbXuLightController.lightPayload(UsbXuLightController.CMD_POSITIVE),
        )
        assertArrayEquals(
            byteArrayOf(0x00, 0x78, 0x13, 0xFF.toByte()),
            UsbXuLightController.lightPayload(UsbXuLightController.CMD_WOODS),
        )
        assertArrayEquals(
            byteArrayOf(0x00, 0x78, 0x14, 0xFF.toByte()),
            UsbXuLightController.lightPayload(UsbXuLightController.CMD_UV),
        )
        assertArrayEquals(
            byteArrayOf(0x00, 0x78, 0x15, 0xFF.toByte()),
            UsbXuLightController.lightPayload(UsbXuLightController.CMD_BLUE),
        )
        assertArrayEquals(
            byteArrayOf(0x00, 0x78, 0x16, 0xFF.toByte()),
            UsbXuLightController.lightPayload(UsbXuLightController.CMD_ORANGE),
        )
        assertArrayEquals(
            byteArrayOf(0x00, 0x78, 0x17, 0xFF.toByte()),
            UsbXuLightController.lightPayload(UsbXuLightController.CMD_RED),
        )
        assertArrayEquals(
            byteArrayOf(0x00, 0x78, 0x13, 0x00),
            UsbXuLightController.lightPayload(UsbXuLightController.CMD_WOODS, UsbXuLightController.ARG_OFF),
        )
    }

    @Test
    fun captureOrderMatchesOemEightLights() {
        val cmds = com.mlh.skinanalyzer.hardware.LightMode.captureOrder.map { it.usbCmd }
        assertEquals(
            listOf(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17),
            cmds,
        )
    }
}

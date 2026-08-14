package com.mlh.skinanalyzer.hardware

import android.content.Context
import android.util.Log

/**
 * Prefers the real MJ-008 USB-XU LED path (Miaojing OEM). Falls back to UART
 * `/dev/ttyS4` only when no USB camera is available (lab / emulator).
 */
class Mj008LightController(
    context: Context,
) : LightController {
    private val usb = UsbXuLightController(context)
    private val serial = SerialLightController()
    private var active: LightController? = null

    override val isOpen: Boolean
        get() = active?.isOpen == true
    override var lastError: String? = null
        private set
    override val backendLabel: String
        get() = active?.backendLabel ?: "none"

    val usingUsb: Boolean
        get() = active === usb
    val usingSerial: Boolean
        get() = active === serial

    override fun setCameraVariant(variant: Mj008Hardware.CameraVariant) {
        serial.setCameraVariant(variant)
    }

    override fun open(): Boolean {
        close()
        if (usb.open()) {
            active = usb
            lastError = null
            Log.i(TAG, "Using USB-XU LED backend")
            return true
        }
        val usbErr = usb.lastError
        if (serial.open()) {
            active = serial
            lastError = "USB-XU no disponible ($usbErr); usando UART de respaldo"
            Log.w(TAG, lastError!!)
            return true
        }
        lastError = listOfNotNull(usbErr, serial.lastError).joinToString(" · ")
        active = null
        return false
    }

    override fun close() {
        runCatching { usb.close() }
        runCatching { serial.close() }
        active = null
    }

    /** USB-XU and UVC cannot hold the same device — release before [Mj008UvcSession]. */
    fun releaseUsbForUvc() {
        runCatching { usb.close() }
        if (active === usb) active = null
    }

    /** LED via UART while UVC owns USB (MJ-008 ttyS1 @ 9600). */
    fun openSerialOnly(): Boolean {
        releaseUsbForUvc()
        return if (serial.open()) {
            active = serial
            lastError = null
            true
        } else {
            lastError = serial.lastError
            false
        }
    }

    override fun turnOff() {
        active?.turnOff()
    }

    override fun setMultiMode() {
        active?.setMultiMode()
    }

    override fun applyLightMode(mode: LightMode) {
        val ctrl = active
        if (ctrl == null) {
            lastError = "LED no conectado"
            return
        }
        // Serial Moji boards only drive W/N/P/WS/UV; skip spectral-only modes there.
        if (ctrl === serial && mode.usbCmd != null && mode.hardwareChannel == null) {
            Log.w(TAG, "Modo ${mode.shortName} requiere USB-XU; omitido en UART")
            return
        }
        ctrl.applyLightMode(mode)
        lastError = ctrl.lastError
    }

    companion object {
        private const val TAG = "Mj008Lights"
    }
}

package com.mlh.skinanalyzer.hardware

import android.content.Context
import android.util.Log

/**
 * Respaldo **solo UART** para lab/emulador sin cámara USB.
 *
 * En la tablet MJ-008 las luces van por [MaokinLightController] dentro de
 * [Mj008UvcSession] (misma UsbDeviceConnection que UVC). Este controlador
 * **nunca** abre USB.
 */
class Mj008LightController(
    @Suppress("UNUSED_PARAMETER") context: Context,
) : LightController {
    private val serial = SerialLightController()
    private var active: LightController? = null

    override val isOpen: Boolean
        get() = active?.isOpen == true
    override var lastError: String? = null
        private set
    override val backendLabel: String
        get() = active?.backendLabel ?: "none"

    val usingUsb: Boolean
        get() = false
    val usingSerial: Boolean
        get() = active === serial

    override fun setCameraVariant(variant: Mj008Hardware.CameraVariant) {
        serial.setCameraVariant(variant)
    }

    /**
     * Solo UART de laboratorio. No toca USB (evita pelear con USBMonitor).
     */
    override fun open(): Boolean {
        close()
        return if (serial.open()) {
            active = serial
            lastError = null
            Log.i(TAG, "Using UART LED backend (lab only — no USB)")
            true
        } else {
            lastError = serial.lastError ?: "UART no disponible"
            active = null
            false
        }
    }

    override fun close() {
        runCatching { serial.close() }
        active = null
    }

    /** No-op: ya no hay handle USB-XU que soltar. */
    fun releaseUsbForUvc() {
        // USB lights live only inside Mj008UvcSession / MaokinLightController.
    }

    fun openSerialOnly(): Boolean = open()

    override fun turnOff() {
        active?.turnOff()
    }

    override fun setMultiMode() {
        active?.setMultiMode()
    }

    override fun applyLightMode(mode: LightMode) {
        val ctrl = active
        if (ctrl == null) {
            lastError = "LED UART no conectado (en MJ-008 use Captura UVC)"
            return
        }
        if (mode.usbCmd != null && mode.hardwareChannel == null) {
            Log.w(TAG, "Modo ${mode.shortName} requiere Maokin/UVC; omitido en UART")
            return
        }
        ctrl.applyLightMode(mode)
        lastError = ctrl.lastError
    }

    companion object {
        private const val TAG = "Mj008Lights"
    }
}

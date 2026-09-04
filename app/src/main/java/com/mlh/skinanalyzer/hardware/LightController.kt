package com.mlh.skinanalyzer.hardware

/**
 * Unified LED controller for MJ-008.
 * Primary path is USB XU (OEM Miaojing); UART serial is an optional fallback.
 */
interface LightController {
    val isOpen: Boolean
    val lastError: String?
    /** Human-readable backend, e.g. "USB-XU" or "UART-ttyS4". */
    val backendLabel: String

    fun open(): Boolean
    fun close()
    fun turnOff()
    fun applyLightMode(mode: LightMode)
    fun setMultiMode() {}
    fun setCameraVariant(variant: Mj008Hardware.CameraVariant) {}
}

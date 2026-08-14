package com.mlh.skinanalyzer.hardware

import android.hardware.usb.UsbDevice

/**
 * MJ-008 tablets ship with **Dual USB camera** + **Force USB front camera** enabled
 * (Auxiliary Function menu). We must pick the skin-analyzer UVC cam, not a secondary USB cam.
 */
object Mj008UsbDevices {
    data class RankedDevice(val device: UsbDevice, val score: Int, val reason: String)

    fun rankAnalyzerCamera(device: UsbDevice): RankedDevice {
        var score = 0
        val reasons = mutableListOf<String>()
        val serial = runCatching { device.serialNumber }.getOrNull().orEmpty()
        val product = runCatching { device.productName }.getOrNull().orEmpty()
        val productLower = product.lowercase()

        if (serial.equals("USB3.0", ignoreCase = true)) {
            score += 120
            reasons += "serial=USB3.0"
        }
        if (product.equals("USB3.0", ignoreCase = true)) {
            score += 110
            reasons += "product=USB3.0"
        }
        if (product.equals("USB Camera", ignoreCase = true)) {
            score += 100
            reasons += "product=USB Camera"
        }
        if (device.vendorId == Mj008Hardware.ANALYZER_USB_VENDOR_ID &&
            device.productId == Mj008Hardware.ANALYZER_USB_PRODUCT_ID
        ) {
            score += 130
            reasons += "MJ-008 USB3.0 vid/pid"
        }
        if (device.productId in Mj008Hardware.knownCameraProductIds) {
            score += 90
            reasons += "knownPid=${device.productId}"
        }
        if (UsbXuLightController.hasVideoInterface(device)) {
            score += 60
            reasons += "uvc"
        }
        if (productLower.contains("usb") && productLower.contains("camera")) {
            score += 40
            reasons += "usb+camera name"
        }
        // Dual-USB firmware: secondary cams often have generic names without USB3.0.
        if (product.isBlank() || productLower == "camera") {
            score -= 30
            reasons += "generic-name-penalty"
        }
        if (device.interfaceCount >= 2 && UsbXuLightController.hasVideoInterface(device)) {
            score += 15
            reasons += "multi-if"
        }
        return RankedDevice(
            device = device,
            score = score,
            reason = reasons.joinToString(", ").ifBlank { "score=$score" },
        )
    }

    fun pickAnalyzerCamera(devices: Collection<UsbDevice>): RankedDevice? {
        if (devices.isEmpty()) return null
        val ranked = devices.map { rankAnalyzerCamera(it) }.sortedByDescending { it.score }
        val best = ranked.first()
        // Require at least video class or OEM naming — avoid random USB hubs.
        if (best.score < 40 && !UsbXuLightController.hasVideoInterface(best.device)) {
            return ranked.firstOrNull { UsbXuLightController.hasVideoInterface(it.device) }
        }
        return best
    }

    fun pickAnalyzerCameraOrNull(devices: Collection<UsbDevice>): UsbDevice? =
        pickAnalyzerCamera(devices)?.device

    fun isLikelyAnalyzerCamera(device: UsbDevice): Boolean {
        val ranked = rankAnalyzerCamera(device)
        return ranked.score >= 60 ||
            UsbXuLightController.isMj008Camera(device) ||
            (UsbXuLightController.hasVideoInterface(device) && ranked.score >= 40)
    }
}

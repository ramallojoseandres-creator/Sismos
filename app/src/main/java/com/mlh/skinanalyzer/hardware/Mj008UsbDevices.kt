package com.mlh.skinanalyzer.hardware

import android.hardware.usb.UsbDevice

/**
 * MJ-008 tablets ship with **Dual USB camera** + **Force USB front camera** enabled
 * (Auxiliary Function menu). We must pick the skin-analyzer UVC cam (USB3.0 /
 * vid=3804 pid=12416), never the secondary USB cam or ILITEK touch.
 */
object Mj008UsbDevices {
    data class RankedDevice(val device: UsbDevice, val score: Int, val reason: String)

    /** Hard reject: touch panels and non-video peripherals. */
    fun isExcluded(device: UsbDevice): Boolean {
        val product = runCatching { device.productName }.getOrNull().orEmpty().lowercase()
        val manufacturer = runCatching { device.manufacturerName }.getOrNull().orEmpty().lowercase()
        if (product.contains("ilitek") || manufacturer.contains("ilitek")) return true
        if (product.contains("touch") || product.contains("digitizer")) return true
        if (product.contains("hub") && !UsbXuLightController.hasVideoInterface(device)) return true
        return false
    }

    /**
     * True for the chin-rest analyzer camera. Soft threshold — Dual USB secondary
     * is rejected via negative scores / ILITEK exclude, not a hard 100 cutoff.
     */
    fun isAnalyzerCamera(device: UsbDevice): Boolean = isLikelyAnalyzerCamera(device)

    fun rankAnalyzerCamera(device: UsbDevice): RankedDevice {
        if (isExcluded(device)) {
            return RankedDevice(device, -1000, "excluded")
        }

        var score = 0
        val reasons = mutableListOf<String>()
        val serial = runCatching { device.serialNumber }.getOrNull().orEmpty()
        val product = runCatching { device.productName }.getOrNull().orEmpty()
        val productLower = product.lowercase()

        // Field unit: USB3.0 vid=3804 pid=12416 sn=20220805 (or serial name USB3.0)
        if (device.vendorId == Mj008Hardware.ANALYZER_USB_VENDOR_ID &&
            device.productId == Mj008Hardware.ANALYZER_USB_PRODUCT_ID
        ) {
            score += 200
            reasons += "MJ-008 vid/pid ${device.vendorId}/${device.productId}"
        }
        if (serial.equals("USB3.0", ignoreCase = true)) {
            score += 150
            reasons += "serial=USB3.0"
        }
        if (product.equals("USB3.0", ignoreCase = true)) {
            score += 140
            reasons += "product=USB3.0"
        }
        if (product.equals("USB Camera", ignoreCase = true)) {
            score += 120
            reasons += "product=USB Camera"
        }
        if (device.productId in Mj008Hardware.knownCameraProductIds) {
            score += 90
            reasons += "knownPid=${device.productId}"
        }
        if (UsbXuLightController.hasVideoInterface(device)) {
            score += 25
            reasons += "uvc"
        } else {
            score -= 80
            reasons += "no-video"
        }
        if (productLower.contains("usb") && productLower.contains("camera")) {
            score += 40
            reasons += "usb+camera name"
        }

        // Dual-USB secondary: generic "Camera" / blank — push down hard.
        if (product.isBlank() || productLower == "camera" || productLower == "usb camera2") {
            score -= 80
            reasons += "secondary-generic-penalty"
        }
        if (productLower.contains("secondary") || productLower.contains("rear")) {
            score -= 100
            reasons += "secondary-name"
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
        val ranked = devices
            .filterNot { isExcluded(it) }
            .map { rankAnalyzerCamera(it) }
            .sortedByDescending { it.score }
        val best = ranked.firstOrNull() ?: return null
        if (best.score >= 100) return best
        // Single UVC device on the bus → that's the analyzer (even if name strings empty).
        val videoOnly = ranked.filter { UsbXuLightController.hasVideoInterface(it.device) }
        if (videoOnly.size == 1 && videoOnly.first().score >= 40) {
            return videoOnly.first()
        }
        // Prefer highest UVC with score ≥ 80 (USB Camera / known PID without exact USB3.0 string).
        return ranked.firstOrNull { it.score >= 80 && UsbXuLightController.hasVideoInterface(it.device) }
            ?: ranked.firstOrNull { it.score >= 100 }
    }

    fun pickAnalyzerCameraOrNull(devices: Collection<UsbDevice>): UsbDevice? =
        pickAnalyzerCamera(devices)?.device

    fun isLikelyAnalyzerCamera(device: UsbDevice): Boolean {
        if (isExcluded(device)) return false
        val ranked = rankAnalyzerCamera(device)
        return ranked.score >= 80 ||
            (device.vendorId == Mj008Hardware.ANALYZER_USB_VENDOR_ID &&
                device.productId == Mj008Hardware.ANALYZER_USB_PRODUCT_ID)
    }
}

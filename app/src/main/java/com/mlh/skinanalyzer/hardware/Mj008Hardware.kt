package com.mlh.skinanalyzer.hardware

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import java.io.File

/**
 * Hardware profile locked to **Maokin Miaojin MJ-008**.
 *
 * Real OEM (Miaojing / 妙境 `com.ym.smart.skins`) drives LEDs over USB XU on the
 * UVC camera (`nativeXuWrite(130, 55318, …)`). Engineering menus on some MJ-008
 * builds expose UART at `/dev/ttyS1` @ 9600 as a lab/debug path (not ttyS4).
 */
object Mj008Hardware {
    const val MODEL_NAME = "MJ-008"
    const val BRAND_NAME = "Maokin Miaojin"
    const val FULL_LABEL = "MJ-008 Maokin Miaojin Skin Analyzer"

    /** Preferred UART candidates — tablet eng menu shows ttyS1 @ 9600. */
    val SERIAL_CANDIDATES = listOf(
        SerialCandidate("/dev/ttyS1", 9600),
        SerialCandidate("/dev/ttyS4", 115200),
        SerialCandidate("/dev/ttyS4", 9600),
        SerialCandidate("/dev/ttyS3", 9600),
        SerialCandidate("/dev/ttyS0", 9600),
    )

    /** @deprecated Prefer [SERIAL_CANDIDATES]; kept for callers. */
    const val SERIAL_DEVICE = "/dev/ttyS1"
    const val SERIAL_BAUD_PRIMARY = 9600
    const val SERIAL_BAUD_LEGACY = 9600

    /** Preview size used by OEM UVC handler. */
    const val PREVIEW_WIDTH = 1600
    const val PREVIEW_HEIGHT = 1200
    /**
     * Preview orientation for [UVCCameraHandler.createHandler].
     * Must match on-screen camera: **90°** (user-confirmed on tablet).
     */
    const val PREVIEW_ORIENTATION = 90

    /**
     * USB camera product IDs seen on Moji / MJ-family analyzers.
     * 25441 = ZX FCA56, 25443 / 25456 = Moji L12345, 52243 = SXW,
     * 12416 = MJ-008 USB3.0 analyzer (vid 3804, ZK-R36A boards).
     */
    const val ANALYZER_USB_VENDOR_ID = 3804
    const val ANALYZER_USB_PRODUCT_ID = 12416
    val knownCameraProductIds = intArrayOf(25441, 25443, 25456, 52243, 12416)

    data class SerialCandidate(val path: String, val baud: Int)

    enum class CameraVariant {
        SXW,
        ZX_FCA56,
        MOJI_25443,
        MOJI_25456,
        MJ008_UVC,
        UNKNOWN,
    }

    data class Detection(
        val serialPresent: Boolean,
        val serialWritable: Boolean,
        val serialPath: String?,
        val serialBaud: Int?,
        val usbXuCameraPresent: Boolean,
        val cameraVariant: CameraVariant,
        val usbCameras: List<String>,
        val usbAllDevices: List<String>,
        val deviceModel: String,
        val summary: String,
        val diagnostics: String,
    )

    data class LightPreset(
        val whiteCenter: Int,
        val xplWhiteFill: Int,
        val xplCenter: Int,
        val pplNegFill: Int,
        val pplCenter: Int,
        val woodsCenter: Int,
        val uvCenter: Int,
    )

    /** LED center-channel intensities for UART fallback only. */
    fun presetFor(variant: CameraVariant): LightPreset = when (variant) {
        CameraVariant.MOJI_25443, CameraVariant.MOJI_25456 -> LightPreset(
            whiteCenter = 49,
            xplWhiteFill = 3,
            xplCenter = 83,
            pplNegFill = 10,
            pplCenter = 90,
            woodsCenter = 100,
            uvCenter = 70,
        )
        CameraVariant.ZX_FCA56 -> LightPreset(
            whiteCenter = 49,
            xplWhiteFill = 4,
            xplCenter = 75,
            pplNegFill = 5,
            pplCenter = 90,
            woodsCenter = 100,
            uvCenter = 65,
        )
        else -> LightPreset(
            whiteCenter = 49,
            xplWhiteFill = 3,
            xplCenter = 83,
            pplNegFill = 10,
            pplCenter = 90,
            woodsCenter = 85,
            uvCenter = 65,
        )
    }

    fun detect(context: Context): Detection {
        val serialHit = findSerialPort()
        val usb = listUsbDevices(context)
        val videoUsb = usb.filter { UsbXuLightController.hasVideoInterface(it.first) }
        val namedCam = usb.filter { UsbXuLightController.isMj008Camera(it.first) }
        val xuPresent = namedCam.isNotEmpty() || videoUsb.isNotEmpty()
        val variant = when {
            namedCam.isNotEmpty() || videoUsb.isNotEmpty() -> CameraVariant.MJ008_UVC
            else -> resolveVariant(usb.map { it.second })
        }
        val model = Build.MODEL.orEmpty()
        val camLabel = when {
            namedCam.isNotEmpty() -> namedCam.joinToString { describeUsb(it.first) }
            videoUsb.isNotEmpty() -> videoUsb.joinToString { describeUsb(it.first) }
            usb.isEmpty() -> "ningún USB (¿permiso/host?)"
            else -> "USB sin clase vídeo (${usb.size})"
        }
        val summary = buildString {
            append(FULL_LABEL)
            append(" · LED ")
            append(
                when {
                    xuPresent -> "USB-XU / UVC"
                    serialHit != null -> "UART ${serialHit.path}@${serialHit.baud}"
                    else -> "no detectado"
                },
            )
            append(" · cámara ")
            append(camLabel)
        }
        val diagnostics = buildString {
            appendLine("Firmware MJ-008 (menú Auxiliary Function):")
            appendLine("  Screen Rotation=270 · Camera Rotation=0")
            appendLine("  Force USB front camera=ON · Dual USB camera=ON")
            appendLine("  → la app elige la cámara analizador por nombre/PID (no la USB secundaria)")
            appendLine("Modelo Android: $model")
            appendLine("USB total: ${usb.size}")
            if (usb.isEmpty()) {
                appendLine("  (vacío — revise cable interno / permiso USB al capturar)")
            } else {
                usb.map { Mj008UsbDevices.rankAnalyzerCamera(it.first) }
                    .sortedByDescending { it.score }
                    .forEach { r ->
                        val dev = r.device
                        appendLine(
                            "  [score=${r.score}] ${describeUsb(dev)} · ${r.reason}",
                        )
                    }
            }
            appendLine("Puertos serie:")
            SERIAL_CANDIDATES.map { it.path }.distinct().forEach { path ->
                val f = File(path)
                val exists = runCatching { f.exists() }.getOrDefault(false)
                val canWrite = runCatching { exists && f.canWrite() }.getOrDefault(false)
                appendLine("  $path exists=$exists writable=$canWrite")
            }
            if (serialHit != null) {
                appendLine("Serie elegida: ${serialHit.path} @ ${serialHit.baud}")
            }
        }
        return Detection(
            serialPresent = serialHit != null,
            serialWritable = serialHit != null,
            serialPath = serialHit?.path,
            serialBaud = serialHit?.baud,
            usbXuCameraPresent = xuPresent,
            cameraVariant = variant,
            usbCameras = (namedCam.ifEmpty { videoUsb }).map { describeUsb(it.first) },
            usbAllDevices = usb.map { describeUsb(it.first) },
            deviceModel = model,
            summary = summary,
            diagnostics = diagnostics,
        )
    }

    fun findSerialPort(): SerialCandidate? {
        var firstExisting: SerialCandidate? = null
        for (c in SERIAL_CANDIDATES) {
            val f = File(c.path)
            val exists = try {
                f.exists()
            } catch (_: Exception) {
                false
            }
            if (!exists) continue
            if (firstExisting == null) firstExisting = c
            val writable = try {
                f.canWrite()
            } catch (_: Exception) {
                false
            }
            if (writable) return c
        }
        return firstExisting
    }

    fun resolveVariant(productIds: List<Int>): CameraVariant {
        for (pid in productIds) {
            when (pid) {
                25443 -> return CameraVariant.MOJI_25443
                25456 -> return CameraVariant.MOJI_25456
                25441 -> return CameraVariant.ZX_FCA56
                52243 -> return CameraVariant.SXW
            }
        }
        return CameraVariant.UNKNOWN
    }

    private fun describeUsb(device: UsbDevice): String {
        val name = runCatching { device.productName }.getOrNull() ?: "USB"
        val serial = runCatching { device.serialNumber }.getOrNull().orEmpty()
        return "$name vid=${device.vendorId} pid=${device.productId}" +
            if (serial.isNotBlank()) " sn=$serial" else ""
    }

    private fun listUsbDevices(context: Context): List<Pair<UsbDevice, Int>> {
        return try {
            val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
                ?: return emptyList()
            manager.deviceList.values.map { it to it.productId }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

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
 * UVC camera (`nativeXuWrite(130, 55318, …)`), not UART. Serial `/dev/ttyS4` is
 * kept only as a lab fallback for older Moji boards.
 */
object Mj008Hardware {
    const val MODEL_NAME = "MJ-008"
    const val BRAND_NAME = "Maokin Miaojin"
    const val FULL_LABEL = "MJ-008 Maokin Miaojin Skin Analyzer"

    /** Optional UART fallback (Bitmoji/Moji A6 family). */
    const val SERIAL_DEVICE = "/dev/ttyS4"
    const val SERIAL_BAUD_PRIMARY = 115200
    const val SERIAL_BAUD_LEGACY = 9600

    /** Preview size used by OEM UVC handler. */
    const val PREVIEW_WIDTH = 1600
    const val PREVIEW_HEIGHT = 1200
    const val PREVIEW_ORIENTATION = 90

    /**
     * USB camera product IDs seen on Moji / MJ-family analyzers.
     * 25441 = ZX FCA56, 25443 / 25456 = Moji L12345, 52243 = SXW.
     */
    val knownCameraProductIds = intArrayOf(25441, 25443, 25456, 52243)

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
        val usbXuCameraPresent: Boolean,
        val cameraVariant: CameraVariant,
        val usbCameras: List<String>,
        val deviceModel: String,
        val summary: String,
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
        val serial = File(SERIAL_DEVICE)
        val serialPresent = try {
            serial.exists()
        } catch (_: Exception) {
            false
        }
        // Never open ttyS4 just to probe — that can native-crash on some MJ firmwares.
        val serialWritable = try {
            serialPresent && serial.canWrite()
        } catch (_: Exception) {
            false
        }
        val usb = listUsbCameras(context)
        val xuPresent = usb.any { UsbXuLightController.isMj008Camera(it.first) } ||
            usb.any { UsbXuLightController.hasVideoInterface(it.first) }
        val variant = when {
            usb.any { UsbXuLightController.isMj008Camera(it.first) } -> CameraVariant.MJ008_UVC
            else -> resolveVariant(usb.map { it.second })
        }
        val model = Build.MODEL.orEmpty()
        val summary = buildString {
            append(FULL_LABEL)
            append(" · LED ")
            append(
                when {
                    xuPresent -> "USB-XU"
                    serialWritable -> "UART ($SERIAL_DEVICE)"
                    serialPresent -> "UART presente"
                    else -> "no detectado"
                },
            )
            append(" · cámara ")
            append(
                when (variant) {
                    CameraVariant.UNKNOWN -> if (usb.isEmpty()) "no detectada (USB)" else "USB genérica"
                    CameraVariant.MJ008_UVC -> "USB3.0 UVC"
                    else -> variant.name
                },
            )
        }
        return Detection(
            serialPresent = serialPresent,
            serialWritable = serialWritable,
            usbXuCameraPresent = xuPresent,
            cameraVariant = variant,
            usbCameras = usb.map { (dev, pid) ->
                val name = runCatching { dev.productName }.getOrNull() ?: dev.deviceName
                "$name PID=$pid"
            },
            deviceModel = model,
            summary = summary,
        )
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

    private fun listUsbCameras(context: Context): List<Pair<UsbDevice, Int>> {
        return try {
            val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
                ?: return emptyList()
            manager.deviceList.values.map { it to it.productId }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

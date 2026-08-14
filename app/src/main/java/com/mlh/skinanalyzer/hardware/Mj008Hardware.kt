package com.mlh.skinanalyzer.hardware

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import java.io.File

/**
 * Hardware profile locked to **Maokin Miaojin MJ-008**.
 *
 * The Bitmoji/Moji A6 reference APK shares the same LED UART and USB camera
 * family used on MJ-008 tablets. This profile pins product IDs, serial path,
 * baud rate and LED intensity presets to that machine — not generic Android tablets.
 */
object Mj008Hardware {
    const val MODEL_NAME = "MJ-008"
    const val BRAND_NAME = "Maokin Miaojin"
    const val FULL_LABEL = "MJ-008 Maokin Miaojin Skin Analyzer"

    /** UART used by MJ-008 light board (same as Moji A6 tablet integration). */
    const val SERIAL_DEVICE = "/dev/ttyS4"
    const val SERIAL_BAUD_PRIMARY = 115200
    const val SERIAL_BAUD_LEGACY = 9600

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
        UNKNOWN,
    }

    data class Detection(
        val serialPresent: Boolean,
        val serialWritable: Boolean,
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

    /** LED center-channel intensities matching OEM capture groups for MJ-008 / Moji. */
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
        val variant = resolveVariant(usb.map { it.second })
        val model = Build.MODEL.orEmpty()
        val summary = buildString {
            append(FULL_LABEL)
            append(" · serial ")
            append(
                when {
                    serialWritable -> "OK ($SERIAL_DEVICE)"
                    serialPresent -> "presente ($SERIAL_DEVICE)"
                    else -> "no encontrado"
                },
            )
            append(" · cámara ")
            append(
                when (variant) {
                    CameraVariant.UNKNOWN -> if (usb.isEmpty()) "no detectada (USB)" else "USB genérica"
                    else -> variant.name
                },
            )
        }
        return Detection(
            serialPresent = serialPresent,
            serialWritable = serialWritable,
            cameraVariant = variant,
            usbCameras = usb.map { (dev, pid) -> "${dev.deviceName} PID=$pid" },
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
        // MJ-008 default: Moji LED curve (most common on Miaojin tablets)
        return CameraVariant.MOJI_25443
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

package com.mlh.skinanalyzer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mlh.skinanalyzer.BuildConfig
import com.mlh.skinanalyzer.hardware.Mj008Hardware
import com.mlh.skinanalyzer.hardware.Mj008UsbDevices
import com.mlh.skinanalyzer.hardware.UsbXuLightController
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.Cream
import com.mlh.skinanalyzer.ui.theme.Ink
import com.mlh.skinanalyzer.ui.theme.Paper

/**
 * In-app USB / camera dump so the clinic can screenshot or copy and send
 * without ADB or third-party process monitors.
 */
@Composable
fun DiagnosticScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var report by remember { mutableStateOf(buildDiagnosticReport(context)) }
    var copied by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
            }
            Text(
                "Diagnóstico USB / cámara",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Abra la app china con la cámara encendida, luego vuelva aquí, pulse Actualizar y copie el texto. " +
                "Así vemos qué USB/cámara está activa sin PC.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.copy(alpha = 0.65f),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    report = buildDiagnosticReport(context)
                    copied = false
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(4.dp),
            ) { Text("Actualizar") }
            OutlinedButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("MLH diagnóstico", report))
                    copied = true
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(4.dp),
            ) { Text(if (copied) "Copiado" else "Copiar todo") }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Cream, RoundedCornerShape(4.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                report,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
        }
    }
}

private fun buildDiagnosticReport(context: Context): String {
    val sb = StringBuilder()
    sb.appendLine("=== MLH Skin Analyzer Pro · diagnóstico ===")
    sb.appendLine("app v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    sb.appendLine("time=${System.currentTimeMillis()}")
    sb.appendLine()
    sb.appendLine("-- Dispositivo --")
    sb.appendLine("manufacturer=${Build.MANUFACTURER}")
    sb.appendLine("model=${Build.MODEL}")
    sb.appendLine("device=${Build.DEVICE}")
    sb.appendLine("hardware=${Build.HARDWARE}")
    sb.appendLine("sdk=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE}")
    sb.appendLine("cpuAbi=${Build.CPU_ABI} cpuAbi2=${Build.CPU_ABI2}")
    sb.appendLine("supportedAbis=${Build.SUPPORTED_ABIS.joinToString()}")
    sb.appendLine("apkAbis=arm64-v8a,armeabi-v7a (UVC jniLibs)")
    sb.appendLine()

    val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    val devices = usb?.deviceList?.values?.toList().orEmpty()
    sb.appendLine("-- USB (${devices.size}) --")
    if (devices.isEmpty()) {
        sb.appendLine("(ninguno)")
    } else {
        val ranked = devices.map { Mj008UsbDevices.rankAnalyzerCamera(it) }
            .sortedByDescending { it.score }
        ranked.forEachIndexed { i, r ->
            val d = r.device
            val granted = runCatching { usb?.hasPermission(d) }.getOrNull()
            sb.appendLine("[$i] score=${r.score} reason=${r.reason}")
            sb.appendLine("  name=${UsbXuLightController.describe(d)}")
            sb.appendLine("  product=${runCatching { d.productName }.getOrNull()}")
            sb.appendLine("  manufacturer=${runCatching { d.manufacturerName }.getOrNull()}")
            sb.appendLine("  serial=${runCatching { d.serialNumber }.getOrNull()}")
            sb.appendLine("  vid=${d.vendorId} pid=${d.productId} class=${d.deviceClass}")
            sb.appendLine("  deviceName=${d.deviceName}")
            sb.appendLine("  hasPermission=$granted excluded=${Mj008UsbDevices.isExcluded(d)}")
            sb.appendLine("  analyzer=${Mj008UsbDevices.isAnalyzerCamera(d)} video=${UsbXuLightController.hasVideoInterface(d)}")
            for (ii in 0 until d.interfaceCount) {
                val intf = d.getInterface(ii)
                val cls = when (intf.interfaceClass) {
                    UsbConstants.USB_CLASS_VIDEO -> "VIDEO"
                    UsbConstants.USB_CLASS_AUDIO -> "AUDIO"
                    UsbConstants.USB_CLASS_HID -> "HID"
                    UsbConstants.USB_CLASS_VENDOR_SPEC -> "VENDOR"
                    else -> "class=${intf.interfaceClass}"
                }
                sb.appendLine("  iface$ii $cls subclass=${intf.interfaceSubclass} proto=${intf.interfaceProtocol}")
            }
            sb.appendLine()
        }
        val pick = Mj008UsbDevices.pickAnalyzerCamera(devices)
        sb.appendLine("PICK=${pick?.let { UsbXuLightController.describe(it.device) + " s=" + it.score } ?: "ninguno"}")
    }
    sb.appendLine()

    sb.appendLine("-- Camera2 (Android) --")
    runCatching {
        val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = cam.cameraIdList
        sb.appendLine("count=${ids.size}")
        ids.forEach { id ->
            val chars = cam.getCameraCharacteristics(id)
            val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                else -> "?"
            }
            val size = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            sb.appendLine("  id=$id facing=$facing sensor=$size")
        }
    }.onFailure {
        sb.appendLine("error=${it.message}")
    }
    sb.appendLine()

    runCatching {
        val det = Mj008Hardware.detect(context)
        sb.appendLine("-- Mj008Hardware.detect --")
        sb.appendLine(det.diagnostics)
    }.onFailure {
        sb.appendLine("detect error=${it.message}")
    }

    sb.appendLine()
    sb.appendLine("-- Gushang SkinDetect --")
    sb.appendLine(com.mlh.skinanalyzer.analysis.gushang.GushangLicense.diagnose())
    sb.appendLine()
    sb.appendLine("=== fin ===")
    return sb.toString()
}

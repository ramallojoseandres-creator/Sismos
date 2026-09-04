package com.mlh.skinanalyzer.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.usb.UsbManager
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mlh.skinanalyzer.BuildConfig
import com.mlh.skinanalyzer.hardware.LightMode
import com.mlh.skinanalyzer.hardware.Mj008UsbDevices
import com.mlh.skinanalyzer.hardware.Mj008UvcSession
import com.mlh.skinanalyzer.hardware.UsbXuLightController
import com.mlh.skinanalyzer.ui.AppViewModel
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.Ink
import com.mlh.skinanalyzer.ui.theme.Paper
import com.serenegiant.widget.UVCCameraTextureView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Brief §10 etapa 3: preview vivo + 8 botones de luz.
 * Si el preview muere al encender una luz → hay dos conexiones USB.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LightTestScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var textureView by remember { mutableStateOf<UVCCameraTextureView?>(null) }
    var session by remember { mutableStateOf<Mj008UvcSession?>(null) }
    var status by remember { mutableStateOf("Etapa 3 · abriendo UVC…") }
    var ready by remember { mutableStateOf(false) }
    var activeLight by remember { mutableStateOf<LightMode?>(null) }
    var retryToken by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { vm.markCaptureActive(true) }

    LaunchedEffect(textureView, retryToken) {
        val act = activity
        val view = textureView
        if (act == null || view == null) return@LaunchedEffect
        ready = false
        activeLight = null
        val usbSummary = runCatching {
            val mgr = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val list = mgr.deviceList.values.toList()
            val pick = Mj008UsbDevices.pickAnalyzerCamera(list)
            "pick=${pick?.let { UsbXuLightController.describe(it.device) } ?: "ninguno"}"
        }.getOrDefault("USB=?")
        status = "$usbSummary · preparando…"
        yield()
        try {
            val s = withContext(Dispatchers.Default) { vm.prepareUvcSession(act) }
            delay(300)
            val bound = withContext(Dispatchers.Default) { s.bindPreview(view) }
            if (!bound) {
                status = "${s.statusLabel} · falló handler"
                return@LaunchedEffect
            }
            s.start()
            session = s
            repeat(30) {
                delay(400)
                status = s.statusLabel
                if (s.isReady) {
                    ready = true
                    status = "Preview OK · pulse una luz. Si el video muere = doble USB."
                    return@repeat
                }
            }
            if (!s.isReady) {
                status = "${s.statusLabel} · sin preview. Reintentar."
            }
        } catch (e: Exception) {
            Log.e("LightTest", "open failed", e)
            status = "Error: ${e.message}"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.markCaptureActive(false)
            vm.releaseUvcSession()
            session = null
            textureView = null
        }
    }

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
            Column(Modifier.weight(1f)) {
                Text("Prueba luces · etapa 3", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · controlLed only",
                    style = MaterialTheme.typography.labelLarge,
                    color = Ink.copy(alpha = 0.5f),
                )
            }
        }
        Text(
            status,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.copy(alpha = 0.75f),
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Ink.copy(alpha = 0.2f))
                .background(Ink),
        ) {
            AndroidView(
                factory = { ctx ->
                    UVCCameraTextureView(ctx).also { v ->
                        v.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        textureView = v
                    }
                },
                update = { v -> if (textureView !== v) textureView = v },
                modifier = Modifier.fillMaxSize(),
            )
            if (!ready) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Ink.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Accent)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            status,
                            color = Paper,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Una luz a la vez. Preview debe seguir vivo.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            LightMode.captureOrder.forEach { mode ->
                val selected = activeLight == mode
                Button(
                    onClick = {
                        val s = session
                        if (s == null || !s.isReady) {
                            status = "Cámara no lista"
                            return@Button
                        }
                        activeLight = mode
                        status = "Encendiendo ${mode.displayName}…"
                        s.applyLightMode(mode)
                        status = s.statusLabel
                    },
                    enabled = ready,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) Accent else Ink.copy(alpha = 0.15f),
                        contentColor = if (selected) Paper else Ink,
                    ),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text("${mode.shortName}\n0x${"%02X".format(mode.usbCmd ?: 0)}")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    session?.turnOff()
                    activeLight = null
                    status = session?.statusLabel ?: "OFF"
                },
                enabled = ready,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.weight(1f),
            ) { Text("Apagar luces") }
            OutlinedButton(
                onClick = {
                    vm.releaseUvcSession()
                    session = null
                    ready = false
                    retryToken++
                },
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.weight(1f),
            ) { Text("Reintentar cámara") }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

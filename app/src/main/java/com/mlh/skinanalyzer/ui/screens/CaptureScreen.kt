package com.mlh.skinanalyzer.ui.screens

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.usb.UsbManager
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mlh.skinanalyzer.BuildConfig
import com.mlh.skinanalyzer.analysis.DemoFrameGenerator
import com.mlh.skinanalyzer.analysis.oem.OemCaptureFiles
import com.mlh.skinanalyzer.data.Patient
import com.mlh.skinanalyzer.hardware.CapturePrefs
import com.mlh.skinanalyzer.hardware.Mj008LightController
import com.mlh.skinanalyzer.hardware.Mj008UvcSession
import com.mlh.skinanalyzer.hardware.Mj008UsbDevices
import com.mlh.skinanalyzer.hardware.LightMode
import com.mlh.skinanalyzer.hardware.Mj008Hardware
import com.mlh.skinanalyzer.hardware.UsbXuLightController
import com.mlh.skinanalyzer.ui.AppViewModel
import com.serenegiant.widget.UVCCameraTextureView
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.Cream
import com.mlh.skinanalyzer.ui.theme.Ink
import com.mlh.skinanalyzer.ui.theme.Paper
import com.mlh.skinanalyzer.ui.theme.Teal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun CaptureScreen(
    patientId: Long,
    vm: AppViewModel,
    controller: Mj008LightController,
    onBack: () -> Unit,
    onFinished: (Map<String, String>, Float?, String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val demoMode = vm.demoMode
    val detection = remember { runCatching { Mj008Hardware.detect(context) }.getOrNull() }
    val activity = remember(context) { context.findActivity() }

    var patient by remember(patientId) {
        mutableStateOf(
            vm.capturePatient?.takeIf { it.id == patientId }
                ?: vm.findPatientById(patientId),
        )
    }
    LaunchedEffect(patientId) {
        if (patient == null) {
            patient = vm.getPatient(patientId)
        }
    }

    var hasCamPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // Demo never uses USB UVC; real mode defaults to UVC.
    var useUvc by remember(demoMode) { mutableStateOf(!demoMode) }
    var uvcSession by remember { mutableStateOf<Mj008UvcSession?>(null) }
    var textureView by remember { mutableStateOf<UVCCameraTextureView?>(null) }
    var uvcStartToken by remember { mutableIntStateOf(0) }
    var uvcLabel by remember { mutableStateOf(if (demoMode) "Modo de prueba" else "Preparando equipo…") }
    var uvcReady by remember { mutableStateOf(false) }
    var lightsOn by remember { mutableStateOf(false) }
    var uvcGiveUp by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCamPermission = granted }

    LaunchedEffect(demoMode) {
        if (demoMode && !hasCamPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var moistureText by remember { mutableStateOf("") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember {
        mutableStateOf(
            if (demoMode) {
                "Modo de prueba: pulse Iniciar para simular las 8 luces."
            } else {
                "Coloque el mentón y cierre los ojos. El equipo se prepara solo."
            },
        )
    }

    LaunchedEffect(Unit) { vm.markCaptureActive(true) }

    // TextureView ready → prepare USB off-main → bind+start (openDevice on USB I/O thread).
    LaunchedEffect(useUvc, textureView, uvcStartToken, demoMode) {
        if (demoMode || !useUvc) return@LaunchedEffect
        val act = activity
        if (act == null) {
            uvcLabel = "Error: Activity no encontrada (v${BuildConfig.VERSION_NAME})"
            return@LaunchedEffect
        }
        val view = textureView
        if (view == null) {
            uvcLabel = "Creando vista UVC… (v${BuildConfig.VERSION_NAME})"
            return@LaunchedEffect
        }

        uvcReady = false
        uvcGiveUp = false
        lightsOn = false
        val usbSummary = runCatching {
            val mgr = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val list = mgr.deviceList.values.toList()
            val pick = Mj008UsbDevices.pickAnalyzerCamera(list)
            "USB=${list.size} pick=${pick?.let { UsbXuLightController.describe(it.device) + " s=" + it.score } ?: "ninguno"}"
        }.getOrDefault("USB=?")
        Log.i("Capture", usbSummary)

        // Fail-fast watchdog: never leave the clinic staring at a spinner.
        val watchdog = launch {
            delay(8_000)
            if (!uvcReady) {
                uvcGiveUp = true
                uvcLabel = "No se detecta el equipo. Revise la conexión."
                status = "No se detecta el equipo. Revise la conexión y reintente."
            }
        }

        try {
            // Never await USB close / handler locks on Main — ANR on MJ-008.
            uvcLabel = "Abriendo sesión…"
            yield()
            val session = withContext(Dispatchers.Default) {
                vm.prepareUvcSession(act)
            }
            detection?.let { controller.setCameraVariant(it.cameraVariant) }
            delay(300) // settle while bg release runs; do not join it
            uvcLabel = "Preparando cámara…"
            yield()
            val bound = withContext(Dispatchers.Default) {
                session.bindPreview(view)
            }
            if (!bound) {
                uvcGiveUp = true
                uvcLabel = "No se detecta el equipo. Revise la conexión."
                return@LaunchedEffect
            }
            uvcLabel = "Conectando equipo…"
            yield()
            session.start() // register/probe posted to USB I/O thread
            uvcSession = session
            uvcLabel = session.statusLabel
            Log.i("Capture", "UVC bind+start done: ${session.statusLabel}")
            repeat(20) {
                delay(400)
                uvcLabel = session.statusLabel
                if (session.isReady) {
                    uvcGiveUp = false
                    return@repeat
                }
            }
            if (!session.isReady) {
                uvcGiveUp = true
                uvcLabel = "No se detecta el equipo. Revise la conexión."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Capture", "UVC prepare/bind/start failed", e)
            uvcGiveUp = true
            uvcLabel = "No se detecta el equipo. Revise la conexión."
        } finally {
            watchdog.cancel()
        }
    }

    // Camera ready → turn lights ON; analysis only starts when user taps the button.
    LaunchedEffect(useUvc, uvcSession, demoMode) {
        if (demoMode || !useUvc) return@LaunchedEffect
        var lit = false
        while (true) {
            val session = uvcSession
            if (session != null) {
                if (!uvcGiveUp || session.isReady) {
                    uvcLabel = session.statusLabel
                }
                val readyNow = session.isReady
                uvcReady = readyNow
                if (readyNow) uvcGiveUp = false
                lightsOn = session.lightsOn
                if (readyNow && !lit) {
                    lit = true
                    status = "Cámara lista · encendiendo luces blancas…"
                    // Wait OEM 1s white-light window, then force ON again for reliability.
                    delay(LightMode.WHITE_LIGHT_DELAY_MS + 200)
                    withContext(Dispatchers.IO) {
                        runCatching { session.applyWhiteLight() }
                        delay(300)
                        runCatching { session.applyWhiteLight() }
                    }
                    lightsOn = true
                    status = "Luces encendidas. Coloque el mentón, cierre los ojos y pulse Iniciar análisis."
                }
            }
            delay(400)
        }
    }

    var captureJob by remember { mutableStateOf<Job?>(null) }

    fun forceLightsOff() {
        runCatching { uvcSession?.turnOff() }
        runCatching { controller.turnOff() }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                captureJob?.cancel()
                forceLightsOff()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            captureJob?.cancel()
            captureJob = null
            forceLightsOff()
            vm.markCaptureActive(false)
            vm.releaseUvcSession()
            uvcSession = null
            textureView = null
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetResolution(
                Size(Mj008Hardware.PREVIEW_WIDTH, Mj008Hardware.PREVIEW_HEIGHT),
            )
            .build()
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    val captured = remember { mutableStateMapOf<String, Pair<String, Bitmap?>>() }
    var currentIndex by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    var captureBanner by remember { mutableStateOf("") }
    var cameraXBound by remember { mutableStateOf(false) }
    val busy = capturing || vm.analyzing

    val progress by animateFloatAsState(
        targetValue = captured.size / 8f,
        label = "progress",
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                enabled = !busy,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (demoMode) "Captura Demo" else "Captura MJ-008",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    patient?.let { "${it.displayName} · ${it.currentAge()} años" } ?: "Paciente",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.6f),
                )
                Text(
                    "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" +
                        if (demoMode) " · DEMO" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = Ink.copy(alpha = 0.45f),
                )
            }
            Text("${captured.size}/8", style = MaterialTheme.typography.titleLarge, color = Accent)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            color = Accent,
        )

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Ink.copy(alpha = 0.2f))
                .background(Ink),
        ) {
            when {
                useUvc && !demoMode -> {
                    AndroidView(
                        factory = { ctx ->
                            UVCCameraTextureView(ctx).also { view ->
                                textureView = view
                                Log.i("Capture", "TextureView created")
                            }
                        },
                        update = { view ->
                            if (textureView !== view) textureView = view
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (!uvcReady) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Ink.copy(alpha = 0.72f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp),
                            ) {
                                CircularProgressIndicator(color = Accent)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    uvcLabel.ifBlank { "Conectando equipo…" },
                                    color = Paper,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        uvcGiveUp = false
                                        val existing = uvcSession
                                        if (existing != null) {
                                            existing.retryConnect()
                                            uvcLabel = existing.statusLabel
                                        } else {
                                            uvcStartToken++
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                    shape = RoundedCornerShape(14.dp),
                                ) { Text("Reintentar conexión") }
                                if (uvcGiveUp) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Sin el equipo no hay análisis. La simulación solo se activa en Admin.",
                                        color = Paper.copy(alpha = 0.9f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
                hasCamPermission -> {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            val providerFuture = ProcessCameraProvider.getInstance(ctx)
                            providerFuture.addListener({
                                try {
                                    val provider = providerFuture.get()
                                    val preview = Preview.Builder().build()
                                    preview.setSurfaceProvider(previewView.surfaceProvider)
                                    provider.unbindAll()
                                    val selectors = listOf(
                                        CameraSelector.DEFAULT_FRONT_CAMERA,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                    )
                                    var bound = false
                                    for (selector in selectors) {
                                        try {
                                            provider.bindToLifecycle(
                                                lifecycleOwner,
                                                selector,
                                                preview,
                                                imageCapture,
                                            )
                                            bound = true
                                            break
                                        } catch (e: Exception) {
                                            Log.w("Capture", "bind failed for selector", e)
                                        }
                                    }
                                    cameraXBound = bound
                                    if (!bound) Log.e("Capture", "No camera could be bound")
                                } catch (e: Exception) {
                                    Log.e("Capture", "camera provider error", e)
                                    cameraXBound = false
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                demoMode -> {
                    Text(
                        "Demo sin cámara: se generarán 8 fotogramas sintéticos al iniciar.",
                        color = Paper,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    Text(
                        "Preparando vista de cámara…",
                        color = Paper,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxSize()
                    .align(Alignment.Center)
                    .background(Paper.copy(alpha = 0.4f)),
            )
            previewBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .size(72.dp),
                )
            }

            if (busy && captureBanner.isNotBlank()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Ink.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(24.dp)
                            .background(Paper.copy(alpha = 0.95f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 28.dp, vertical = 22.dp),
                    ) {
                        if (!captureBanner.contains("finalizado", ignoreCase = true)) {
                            CircularProgressIndicator(color = Accent, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(16.dp))
                        }
                        Text(
                            captureBanner,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Ink,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(status, style = MaterialTheme.typography.bodyLarge)
            Text(
                "Modo: ${LightMode.captureOrder.getOrNull(currentIndex)?.displayName ?: "—"}",
                style = MaterialTheme.typography.titleLarge,
                color = Accent,
            )
            Text(
                when {
                    demoMode && hasCamPermission -> "Modo de prueba · cámara del dispositivo"
                    demoMode -> "Modo de prueba · simulación"
                    useUvc && uvcReady && lightsOn -> "Listo para capturar"
                    useUvc && uvcReady -> "Preparando luces…"
                    useUvc -> uvcLabel.ifBlank { "Buscando el equipo…" }
                    controller.isOpen -> "Equipo conectado"
                    else -> "Esperando el equipo"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.55f),
            )
            OutlinedTextField(
                value = moistureText,
                onValueChange = { moistureText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = { Text("Humedad % (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(4.dp),
            )
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Accent)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            vm.analyzing -> vm.analyzingPhase.ifBlank { "Analizando indicadores…" }
                            else -> captureBanner.ifBlank { "Capturando, por favor espere…" }
                        },
                    )
                }
            } else {
                Button(
                    onClick = {
                        captureJob?.cancel()
                        captureJob = scope.launch {
                            capturing = true
                            captureBanner = "Capturando, por favor espere…"
                            try {
                                captured.clear()
                                val sessionDir = File(
                                    context.filesDir,
                                    "sessions/capture_${System.currentTimeMillis()}",
                                ).apply { mkdirs() }
                                if (!demoMode && useUvc && uvcSession != null) {
                                    if (!uvcSession!!.awaitReady()) {
                                        status = "Cámara del analizador no lista: ${uvcSession!!.statusLabel}. " +
                                            "Pulse Reintentar."
                                        captureBanner = ""
                                        return@launch
                                    }
                                }
                                val total = LightMode.captureOrder.size
                                val preferLiveCam = demoMode && hasCamPermission && cameraXBound
                                val settleFirst = CapturePrefs.settleFirstMs(context)
                                val settleBetween = CapturePrefs.settleBetweenMs(context)
                                val settleAfter = CapturePrefs.settleAfterMs(context)
                                val preFirst = CapturePrefs.preFirstMs(context)
                                val seqStart = SystemClock.elapsedRealtime()
                                if (!demoMode && useUvc && uvcSession != null && preFirst > 0) {
                                    status = "Acomode el rostro en el mentonera…"
                                    captureBanner = "Mantén los ojos cerrados y la cara apoyada"
                                    delay(preFirst)
                                }
                                for ((index, mode) in LightMode.captureOrder.withIndex()) {
                                    if (!isActive) return@launch
                                    currentIndex = index
                                    val n = index + 1
                                    captureBanner =
                                        "${mode.displayName} — $n de $total · Mantén los ojos cerrados y la cara apoyada"
                                    status = if (demoMode) {
                                        "Demo $n/$total · ${mode.displayName}"
                                    } else {
                                        "Foto $n/$total · ${mode.displayName}"
                                    }
                                    val oemFile = File(sessionDir, OemCaptureFiles.filenameFor(mode))
                                    val bmp = when {
                                        demoMode && !preferLiveCam -> {
                                            delay(280)
                                            withContext(Dispatchers.Default) {
                                                DemoFrameGenerator.createFrame(mode, oemFile)
                                            }
                                        }
                                        demoMode && preferLiveCam -> {
                                            delay(220)
                                            takePicture(imageCapture, cameraExecutor, oemFile)
                                                ?: withContext(Dispatchers.Default) {
                                                    DemoFrameGenerator.createFrame(mode, oemFile)
                                                }
                                        }
                                        useUvc && uvcSession != null -> {
                                            val session = uvcSession!!
                                            if (!session.isCameraAlive()) {
                                                status =
                                                    "Se perdió la conexión del equipo. Reintente."
                                                captureBanner = ""
                                                Log.e("Capture", "camera dead before ${mode.shortName}")
                                                return@launch
                                            }
                                            withContext(Dispatchers.IO) {
                                                session.applyLightMode(mode)
                                            }
                                            delay(if (index == 0) settleFirst else settleBetween)
                                            val still = withTimeoutOrNull(5_000) {
                                                session.captureStill(oemFile)
                                            }
                                            if (still == null) {
                                                Log.e("Capture", "Timeout/null capturando ${mode.shortName}")
                                                status =
                                                    "No se pudo capturar en modo ${mode.displayName}. " +
                                                        "Revise la conexión del equipo y reintente."
                                                captureBanner = ""
                                                return@launch
                                            }
                                            if (index < total - 1) {
                                                delay(settleAfter)
                                            }
                                            still
                                        }
                                        else -> {
                                            withContext(Dispatchers.IO) {
                                                controller.applyLightMode(mode)
                                            }
                                            delay(if (index == 0) settleFirst else settleBetween)
                                            takePicture(imageCapture, cameraExecutor, oemFile)
                                        }
                                    }
                                    if (bmp != null || oemFile.exists()) {
                                        captured[mode.shortName] = oemFile.absolutePath to bmp
                                        previewBitmap = bmp
                                    } else {
                                        status = "No se pudo capturar ${mode.displayName}"
                                        captureBanner = ""
                                        return@launch
                                    }
                                }
                                val seqMs = SystemClock.elapsedRealtime() - seqStart
                                Log.i("Capture", "sequence total ${seqMs}ms for $total lights")
                                if (!isActive) return@launch
                                when {
                                    captured.isEmpty() -> {
                                        captureBanner = ""
                                        status = "Sin imágenes. Revise cámara y permisos."
                                    }
                                    captured.size < total -> {
                                        captureBanner = ""
                                        val missing = LightMode.captureOrder
                                            .filter { it.shortName !in captured }
                                            .joinToString { it.displayName }
                                        status =
                                            "Captura incompleta (${captured.size}/$total · ${seqMs}ms). " +
                                                "Faltan: $missing. Reintente."
                                    }
                                    else -> {
                                        captureBanner = "Escaneo finalizado"
                                        status = if (demoMode) {
                                            "Demo: 8 espectros listos (${seqMs}ms)"
                                        } else {
                                            "8 espectros listos (${seqMs}ms)"
                                        }
                                        delay(900)
                                        captureBanner = "Analizando, por favor espere…"
                                        status = "Generando mapas e informe…"
                                        onFinished(
                                            captured.mapValues { it.value.first },
                                            moistureText.toFloatOrNull(),
                                            sessionDir.absolutePath,
                                        )
                                        // Stay busy until ViewModel finishes navigating away.
                                        while (isActive && vm.analyzing) delay(200)
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                captureBanner = ""
                                status = "Error: ${e.message}"
                                Log.e("Capture", "sequence failed", e)
                            } finally {
                                if (!demoMode) {
                                    withContext(NonCancellable) {
                                        when {
                                            useUvc -> runCatching { uvcSession?.turnOff() }
                                            else -> runCatching { controller.turnOff() }
                                        }
                                    }
                                }
                                capturing = false
                                if (!vm.analyzing) captureBanner = ""
                            }
                        }
                    },
                    enabled = patient != null && (
                        demoMode ||
                            (useUvc && uvcReady && lightsOn) ||
                            (!useUvc && hasCamPermission)
                        ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Icon(Icons.Outlined.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            demoMode -> "Iniciar análisis (Demo)"
                            useUvc && !uvcReady -> "Espere cámara…"
                            useUvc && !lightsOn -> "Espere luces…"
                            else -> "Iniciar análisis"
                        },
                    )
                }
            }
            val lightPulse = rememberInfiniteTransition(label = "lights")
            val lightGlow by lightPulse.animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "lightGlow",
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(LightMode.captureOrder) { mode ->
                    val shot = captured[mode.shortName]
                    val active = capturing && LightMode.captureOrder.getOrNull(currentIndex) == mode
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(56.dp),
                    ) {
                        Box(
                            Modifier
                                .size(if (active) 44.dp else 36.dp)
                                .background(
                                    mode.uiColor().copy(
                                        alpha = when {
                                            active -> lightGlow
                                            shot != null -> 1f
                                            else -> 0.35f
                                        },
                                    ),
                                    CircleShape,
                                )
                                .border(
                                    width = if (active) 2.dp else 1.dp,
                                    color = if (shot != null) Teal else Ink.copy(alpha = 0.2f),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (shot?.second != null) {
                                Image(
                                    bitmap = shot.second!!.asImageBitmap(),
                                    contentDescription = mode.shortName,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Ink, CircleShape),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            mode.shortName.take(5),
                            style = MaterialTheme.typography.labelLarge,
                            color = Ink.copy(alpha = 0.6f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private suspend fun takePicture(
    imageCapture: ImageCapture,
    executor: ExecutorService,
    file: File,
): Bitmap? = suspendCoroutine { cont ->
    try {
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            options,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    cont.resume(android.graphics.BitmapFactory.decodeFile(file.absolutePath))
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("Capture", "takePicture", exception)
                    cont.resume(null)
                }
            },
        )
    } catch (e: Exception) {
        Log.e("Capture", "takePicture setup", e)
        cont.resume(null)
    }
}

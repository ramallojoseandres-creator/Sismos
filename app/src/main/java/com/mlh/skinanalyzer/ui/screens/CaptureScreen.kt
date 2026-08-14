package com.mlh.skinanalyzer.ui.screens

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.usb.UsbManager
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
import androidx.compose.animation.core.animateFloatAsState
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
import com.mlh.skinanalyzer.BuildConfig
import com.mlh.skinanalyzer.analysis.DemoFrameGenerator
import com.mlh.skinanalyzer.analysis.oem.OemCaptureFiles
import com.mlh.skinanalyzer.data.Patient
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
    var uvcLabel by remember { mutableStateOf(if (demoMode) "Modo Demo" else "Iniciando UVC…") }
    var uvcReady by remember { mutableStateOf(false) }

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
                "Modo Demo: no hace falta la tablet. Pulse Iniciar para simular las 8 luces."
            } else {
                "Coloque el mentón y cierre los ojos. La cámara USB se abre sola (el diálogo USB a menudo no aparece)."
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
        val usbSummary = runCatching {
            val mgr = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val list = mgr.deviceList.values.toList()
            val pick = Mj008UsbDevices.pickAnalyzerCamera(list)
            "USB=${list.size} pick=${pick?.let { UsbXuLightController.describe(it.device) + " s=" + it.score } ?: "ninguno"}"
        }.getOrDefault("USB=?")
        Log.i("Capture", usbSummary)

        runCatching {
            uvcLabel = "$usbSummary — liberando USB…"
            val session = vm.prepareUvcSession(act)
            detection?.let { controller.setCameraVariant(it.cameraVariant) }
            uvcLabel = "$usbSummary — creando handler…"
            withContext(Dispatchers.Main.immediate) {
                session.bindPreview(view)
            }
            uvcLabel = "$usbSummary — conectando (no cierre la app)…"
            withContext(Dispatchers.Main.immediate) {
                session.start()
            }
            uvcSession = session
            uvcLabel = session.statusLabel
            Log.i("Capture", "UVC bind+start done: ${session.statusLabel}")
            // Poll while native open runs on USB I/O thread (can take several seconds).
            repeat(40) {
                delay(500)
                uvcLabel = session.statusLabel
                if (session.isReady) return@repeat
            }
            if (!session.isReady) {
                uvcLabel = "${session.statusLabel} — pulse Reintentar si no hay imagen"
            }
        }.onFailure {
            Log.e("Capture", "UVC prepare/bind/start failed", it)
            val detail = it.message ?: it.javaClass.simpleName
            uvcLabel = if (detail.contains("0x7f0e0000") || detail.contains("Resource ID")) {
                "Falta recurso cámara (click). Reinstalá v${BuildConfig.VERSION_NAME}+"
            } else {
                "Error UVC: $detail"
            }
        }
    }

    LaunchedEffect(useUvc, uvcSession, demoMode) {
        if (demoMode || !useUvc) return@LaunchedEffect
        var lit = false
        while (true) {
            val session = uvcSession
            if (session != null) {
                uvcLabel = session.statusLabel
                val readyNow = session.isReady
                uvcReady = readyNow
                if (readyNow && !lit) {
                    lit = true
                    runCatching { session.applyWhiteLight() }
                    status = "Cámara frontal lista. Coloque el mentón y pulse Iniciar."
                }
            }
            delay(400)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
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
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (demoMode) "Captura Demo" else "Captura MJ-008",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    patient?.let { "${it.name} · ${it.age} años" } ?: "Paciente",
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
                                    uvcLabel.ifBlank { "Conectando USB3.0…" },
                                    color = Paper,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { uvcStartToken++ },
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                ) { Text("Reintentar cámara frontal USB3.0") }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "No hace falta permiso de cámara de Android. " +
                                        "Si no sale diálogo USB, es normal: el permiso ya está concedido y la app abre sola. " +
                                        "v${BuildConfig.VERSION_NAME}",
                                    color = Paper.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                )
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

            if (capturing && captureBanner.isNotBlank()) {
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
                    demoMode && hasCamPermission -> "Demo · cámara del dispositivo (sin luces USB)"
                    demoMode -> "Demo · fotogramas sintéticos (sin cámara / sin USB)"
                    useUvc && uvcReady -> "Luces vía USB-XU en cámara frontal · $uvcLabel"
                    useUvc -> uvcLabel.ifBlank { "Buscando USB3.0 frontal (vid 3804 / pid 12416)…" }
                    controller.isOpen -> "MJ-008 LED: ${controller.backendLabel} (${detection?.cameraVariant?.name ?: "—"})"
                    else -> "MJ-008 LED: esperando cámara del analizador"
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
            if (capturing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Accent)
                    Spacer(Modifier.width(10.dp))
                    Text(captureBanner.ifBlank { "Capturando, por favor espere…" })
                }
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            capturing = true
                            captureBanner = "Capturando, por favor espere…"
                            try {
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
                                for ((index, mode) in LightMode.captureOrder.withIndex()) {
                                    currentIndex = index
                                    val n = index + 1
                                    captureBanner = if (n < total) {
                                        "Capturando, por favor espere…"
                                    } else {
                                        "Capturando última imagen…"
                                    }
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
                                            withContext(Dispatchers.IO) {
                                                session.applyLightMode(mode)
                                                if (controller.usingSerial) {
                                                    runCatching { controller.applyLightMode(mode) }
                                                }
                                            }
                                            delay(350)
                                            withContext(Dispatchers.IO) {
                                                session.captureStill(oemFile)
                                            }
                                        }
                                        else -> {
                                            withContext(Dispatchers.IO) {
                                                controller.applyLightMode(mode)
                                            }
                                            delay(350)
                                            takePicture(imageCapture, cameraExecutor, oemFile)
                                        }
                                    }
                                    if (bmp != null || oemFile.exists()) {
                                        captured[mode.shortName] = oemFile.absolutePath to bmp
                                        previewBitmap = bmp
                                    } else {
                                        status = "No se pudo capturar ${mode.shortName}"
                                    }
                                }
                                withContext(Dispatchers.IO) {
                                    when {
                                        demoMode -> Unit
                                        useUvc -> runCatching { uvcSession?.turnOff() }
                                        else -> runCatching { controller.turnOff() }
                                    }
                                }
                                if (captured.isEmpty()) {
                                    captureBanner = ""
                                    status = "Sin imágenes. Revisar cámara y permisos."
                                } else {
                                    captureBanner = "Escaneo finalizado"
                                    status = if (demoMode) "Demo: 8 espectros listos" else "8 espectros listos"
                                    delay(1200)
                                    captureBanner = "Analizando, por favor espere…"
                                    status = "Generando mapas e informe…"
                                    onFinished(
                                        captured.mapValues { it.value.first },
                                        moistureText.toFloatOrNull(),
                                        sessionDir.absolutePath,
                                    )
                                }
                            } catch (e: Exception) {
                                captureBanner = ""
                                status = "Error: ${e.message}"
                                Log.e("Capture", "sequence failed", e)
                            } finally {
                                capturing = false
                                captureBanner = ""
                            }
                        }
                    },
                    enabled = patient != null && (demoMode || useUvc || hasCamPermission),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Icon(Icons.Outlined.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (demoMode) "Iniciar análisis (Demo)" else "Iniciar análisis")
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LightMode.captureOrder) { mode ->
                    val shot = captured[mode.shortName]
                    Column(
                        Modifier
                            .width(76.dp)
                            .background(Cream, RoundedCornerShape(4.dp))
                            .border(
                                1.dp,
                                if (shot != null) Accent else Ink.copy(alpha = 0.15f),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (shot?.second != null) {
                            Image(
                                bitmap = shot.second!!.asImageBitmap(),
                                contentDescription = mode.shortName,
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(Ink),
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(60.dp)
                                    .background(Ink.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(mode.shortName.take(3), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Text(mode.shortName, style = MaterialTheme.typography.labelLarge)
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

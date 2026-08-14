package com.mlh.skinanalyzer.ui.screens

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.mlh.skinanalyzer.analysis.oem.OemCaptureFiles
import com.mlh.skinanalyzer.data.Patient
import com.mlh.skinanalyzer.hardware.Mj008LightController
import com.mlh.skinanalyzer.hardware.Mj008UvcSession
import com.mlh.skinanalyzer.hardware.LightMode
import com.mlh.skinanalyzer.hardware.Mj008Hardware
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
    val detection = remember { runCatching { Mj008Hardware.detect(context) }.getOrNull() }

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
    val activity = context as? Activity
    var useUvc by remember { mutableStateOf(activity != null) }
    var uvcSession by remember { mutableStateOf<Mj008UvcSession?>(null) }
    var uvcStartToken by remember { mutableIntStateOf(0) }

    var uvcLabel by remember { mutableStateOf("") }
    var uvcReady by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCamPermission = granted
    }

    // CAMERA permission is for CameraX fallback only — UVC uses USB permission.
    LaunchedEffect(Unit) {
        if (!hasCamPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var moistureText by remember { mutableStateOf("") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember {
        mutableStateOf(
            "Coloque el mentón, cierre los ojos. Acepte el permiso USB si aparece.",
        )
    }

    LaunchedEffect(Unit) {
        vm.markCaptureActive(true)
    }

    // Start UVC as soon as Captura opens — do not wait for CAMERA permission.
    LaunchedEffect(useUvc, activity, uvcStartToken) {
        if (!useUvc || activity == null) {
            uvcSession = null
            return@LaunchedEffect
        }
        uvcReady = false
        uvcLabel = "Preparando cámara USB3.0 del analizador…"
        runCatching {
            val session = vm.prepareUvcSession(activity)
            detection?.let { controller.setCameraVariant(it.cameraVariant) }
            uvcSession = session
            uvcLabel = session.statusLabel
            Log.i("Capture", "UVC session prepared — waiting for TextureView bind")
        }.onFailure {
            Log.e("Capture", "prepareUvcSession failed", it)
            uvcLabel = "Error cámara: ${it.message}"
        }
    }

    LaunchedEffect(useUvc, uvcSession) {
        if (!useUvc) return@LaunchedEffect
        var lit = false
        while (true) {
            uvcLabel = uvcSession?.statusLabel.orEmpty().ifBlank { "UVC…" }
            val readyNow = uvcSession?.isReady == true
            uvcReady = readyNow
            if (readyNow && !lit) {
                lit = true
                runCatching { uvcSession?.applyWhiteLight() }
                status = "Cámara frontal del analizador lista. Coloque el mentón y pulse Iniciar."
            }
            delay(400)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.markCaptureActive(false)
            vm.releaseUvcSession()
            uvcSession = null
            scope.launch(Dispatchers.IO) {
                if (!useUvc) {
                    runCatching { controller.turnOff() }
                }
            }
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
                Text("Captura MJ-008", style = MaterialTheme.typography.headlineMedium)
                Text(
                    patient?.let { "${it.name} · ${it.age} años" } ?: "Paciente",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.6f),
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
            if (useUvc && activity != null) {
                    val session = uvcSession
                    if (session != null) {
                        key(session) {
                            AndroidView(
                                factory = { ctx ->
                                    UVCCameraTextureView(ctx).also { view ->
                                        try {
                                            session.bindPreview(view)
                                            session.start()
                                            Log.i("Capture", "UVC bind+start OK")
                                        } catch (e: Exception) {
                                            Log.e("Capture", "UVC bind/start failed", e)
                                            uvcLabel = "UVC error: ${e.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Accent)
                        }
                    }
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
                                    uvcLabel.ifBlank { "Conectando cámara USB3.0… Acepte permiso USB si aparece." },
                                    color = Paper,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        uvcStartToken++
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                ) { Text("Reintentar cámara frontal USB3.0") }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Acepte el diálogo de permiso USB. Solo la USB3.0 del analizador enciende las luces.",
                                    color = Paper.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                } else if (hasCamPermission) {
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
                                    if (!bound) Log.e("Capture", "No camera could be bound")
                                } catch (e: Exception) {
                                    Log.e("Capture", "camera provider error", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        "Preparando vista de cámara…",
                        color = Paper,
                        modifier = Modifier.align(Alignment.Center),
                    )
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
                    Text("Capturando espectros…")
                }
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            capturing = true
                            try {
                                val sessionDir = File(
                                    context.filesDir,
                                    "sessions/capture_${System.currentTimeMillis()}",
                                ).apply { mkdirs() }
                                if (useUvc && uvcSession != null) {
                                    if (!uvcSession!!.awaitReady()) {
                                        status = "Cámara del analizador no lista: ${uvcSession!!.statusLabel}. " +
                                            "Pulse Reintentar o acepte permiso USB."
                                        return@launch
                                    }
                                }
                                for ((index, mode) in LightMode.captureOrder.withIndex()) {
                                    currentIndex = index
                                    status = "Luz ${index + 1}/8: ${mode.displayName}"
                                    val oemFile = File(sessionDir, OemCaptureFiles.filenameFor(mode))
                                    val bmp = if (useUvc && uvcSession != null) {
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
                                    } else {
                                        withContext(Dispatchers.IO) {
                                            controller.applyLightMode(mode)
                                        }
                                        delay(350)
                                        takePicture(imageCapture, cameraExecutor, oemFile)
                                    }
                                    if (bmp != null || oemFile.exists()) {
                                        captured[mode.shortName] = oemFile.absolutePath to bmp
                                        previewBitmap = bmp
                                    } else {
                                        status = "No se pudo capturar ${mode.shortName}"
                                    }
                                }
                                withContext(Dispatchers.IO) {
                                    if (useUvc) {
                                        runCatching { uvcSession?.turnOff() }
                                    } else {
                                        runCatching { controller.turnOff() }
                                    }
                                }
                                if (captured.isEmpty()) {
                                    status = "Sin imágenes. Revisar cámara y permisos."
                                } else {
                                    status = "Captura completa. Analizando…"
                                    onFinished(
                                        captured.mapValues { it.value.first },
                                        moistureText.toFloatOrNull(),
                                        sessionDir.absolutePath,
                                    )
                                }
                            } catch (e: Exception) {
                                status = "Error: ${e.message}"
                                Log.e("Capture", "sequence failed", e)
                            } finally {
                                capturing = false
                            }
                        }
                    },
                    enabled = patient != null && (useUvc || hasCamPermission),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Icon(Icons.Outlined.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Iniciar análisis")
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

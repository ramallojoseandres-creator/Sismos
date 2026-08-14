package com.mlh.skinanalyzer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.mlh.skinanalyzer.data.Patient
import com.mlh.skinanalyzer.hardware.LightMode
import com.mlh.skinanalyzer.hardware.Mj008Hardware
import com.mlh.skinanalyzer.hardware.SerialLightController
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
    patient: Patient?,
    controller: SerialLightController,
    onBack: () -> Unit,
    onFinished: (Map<String, String>, Float?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val detection = remember { Mj008Hardware.detect(context) }

    var hasCamPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { hasCamPermission = it }

    LaunchedEffect(Unit) {
        if (!hasCamPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
        controller.setCameraVariant(detection.cameraVariant)
        controller.open()
        controller.setMultiMode()
        controller.applyLightMode(LightMode.WHITE)
    }
    DisposableEffect(Unit) {
        onDispose {
            try {
                controller.turnOff()
            } catch (_: Exception) {
            }
        }
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    val captured = remember { mutableStateMapOf<String, Pair<String, Bitmap?>>() }
    var currentIndex by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Coloque el mentón en el soporte, cierre los ojos y pulse Iniciar.") }
    var moistureText by remember { mutableStateOf("") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

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

        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .weight(1.4f)
                    .fillMaxHeight()
                    .border(1.dp, Ink.copy(alpha = 0.2f))
                    .background(Ink),
            ) {
                if (hasCamPermission) {
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
                                val provider = providerFuture.get()
                                val preview = Preview.Builder().build()
                                preview.setSurfaceProvider(previewView.surfaceProvider)
                                try {
                                    provider.unbindAll()
                                    // Prefer front if tablet cam; MJ-008 often exposes USB as back/external
                                    val selector = CameraSelector.DEFAULT_BACK_CAMERA
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        selector,
                                        preview,
                                        imageCapture,
                                    )
                                } catch (e: Exception) {
                                    Log.e("Capture", "bind camera", e)
                                    try {
                                        provider.unbindAll()
                                        provider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_FRONT_CAMERA,
                                            preview,
                                            imageCapture,
                                        )
                                    } catch (e2: Exception) {
                                        Log.e("Capture", "front bind failed", e2)
                                    }
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        "Se requiere permiso de cámara",
                        color = Paper,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                // Midline guide
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .align(Alignment.Center)
                        .background(Paper.copy(alpha = 0.45f)),
                )
                previewBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .size(96.dp),
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(status, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Modo actual: ${LightMode.captureOrder.getOrNull(currentIndex)?.displayName ?: "—"}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Accent,
                )
                Text(
                    if (controller.isOpen) {
                        "MJ-008 LED: activo (${detection.cameraVariant.name})"
                    } else {
                        "MJ-008 LED: no disponible — captura de cámara igual"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.55f),
                )
                OutlinedTextField(
                    value = moistureText,
                    onValueChange = { moistureText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("Humedad % (opcional, lápiz)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                )
                Spacer(Modifier.weight(1f))
                if (capturing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Accent)
                        Spacer(Modifier.width(10.dp))
                        Text("Capturando 8 espectros…")
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                capturing = true
                                try {
                                    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
                                    for ((index, mode) in LightMode.captureOrder.withIndex()) {
                                        currentIndex = index
                                        status = "Luz ${index + 1}/8: ${mode.displayName}. Mantenga los ojos cerrados."
                                        if (mode.hardwareChannel != null) {
                                            withContext(Dispatchers.IO) {
                                                controller.applyLightMode(mode)
                                            }
                                            delay(350)
                                            val file = File(dir, "${mode.shortName}_${System.currentTimeMillis()}.jpg")
                                            val bmp = takePicture(imageCapture, cameraExecutor, file)
                                            captured[mode.shortName] = file.absolutePath to bmp
                                            previewBitmap = bmp
                                        } else {
                                            // Derived maps created later in analysis from UV/Wood/White
                                            status = "Preparando mapa ${mode.displayName}…"
                                            delay(120)
                                        }
                                    }
                                    withContext(Dispatchers.IO) { controller.turnOff() }
                                    status = "Captura completa. Analizando…"
                                    val paths = captured.mapValues { it.value.first }
                                    val moisture = moistureText.toFloatOrNull()
                                    onFinished(paths, moisture)
                                } catch (e: Exception) {
                                    status = "Error: ${e.message}"
                                    Log.e("Capture", "sequence failed", e)
                                } finally {
                                    capturing = false
                                }
                            }
                        },
                        enabled = hasCamPermission && patient != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Icon(Icons.Outlined.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Iniciar análisis inteligente")
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LightMode.captureOrder) { mode ->
                val shot = captured[mode.shortName]
                Column(
                    Modifier
                        .width(88.dp)
                        .background(Cream, RoundedCornerShape(4.dp))
                        .border(
                            1.dp,
                            if (shot != null) Accent else Ink.copy(alpha = 0.15f),
                            RoundedCornerShape(4.dp),
                        )
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (shot?.second != null) {
                        Image(
                            bitmap = shot.second!!.asImageBitmap(),
                            contentDescription = mode.shortName,
                            modifier = Modifier
                                .size(70.dp)
                                .background(Ink),
                        )
                    } else {
                        Box(
                            Modifier
                                .size(70.dp)
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
    }
}

private suspend fun takePicture(
    imageCapture: ImageCapture,
    executor: ExecutorService,
    file: File,
): Bitmap? = suspendCoroutine { cont ->
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(
        options,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                cont.resume(bmp)
            }

            override fun onError(exception: ImageCaptureException) {
                cont.resume(null)
            }
        },
    )
}

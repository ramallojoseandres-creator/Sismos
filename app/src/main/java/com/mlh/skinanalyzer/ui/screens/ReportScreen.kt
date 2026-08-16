package com.mlh.skinanalyzer.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.mlh.skinanalyzer.analysis.SkinAnalysisResult
import com.mlh.skinanalyzer.analysis.oem.OemCaptureFiles
import com.mlh.skinanalyzer.analysis.oem.OemIndicatorCatalog
import com.mlh.skinanalyzer.analysis.oem.OemIndicatorResult
import com.mlh.skinanalyzer.analysis.SkinMetric
import com.mlh.skinanalyzer.data.AnalysisSession
import com.mlh.skinanalyzer.data.Patient
import com.mlh.skinanalyzer.share.ReportSharer
import com.mlh.skinanalyzer.ui.AppViewModel
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.Cream
import com.mlh.skinanalyzer.ui.theme.Ink
import com.mlh.skinanalyzer.ui.theme.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportScreen(
    sessionId: Long,
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<AnalysisSession?>(null) }
    var patient by remember { mutableStateOf<Patient?>(null) }
    var result by remember { mutableStateOf<SkinAnalysisResult?>(null) }
    var images by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var tab by remember { mutableIntStateOf(0) }
    var guides by remember { mutableStateOf<List<com.mlh.skinanalyzer.data.CareGuide>>(emptyList()) }
    var products by remember { mutableStateOf<List<com.mlh.skinanalyzer.data.ProductRec>>(emptyList()) }
    var oemIndicators by remember { mutableStateOf<List<OemIndicatorResult>>(emptyList()) }
    var selectedMapIndex by remember { mutableIntStateOf(0) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        val s = vm.getSession(sessionId)
        if (s == null) {
            loadFailed = true
            return@LaunchedEffect
        }
        session = s
        patient = vm.getPatient(s.patientId)
        result = runCatching {
            Gson().fromJson(s.metricsJson, SkinAnalysisResult::class.java)
        }.getOrNull() ?: vm.lastResult
        if (patient == null || result == null) {
            loadFailed = true
            return@LaunchedEffect
        }
        images = runCatching {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            Gson().fromJson<Map<String, String>>(s.imagePathsJson, type) ?: emptyMap()
        }.getOrDefault(emptyMap())
        oemIndicators = runCatching {
            val type = object : com.google.gson.reflect.TypeToken<List<OemIndicatorResult>>() {}.type
            Gson().fromJson<List<OemIndicatorResult>>(s.oemIndicatorsJson, type) ?: emptyList()
        }.getOrDefault(emptyList())
        // Prefer Mapas tab when OEM overlays exist (OEM-like consult flow).
        if (oemIndicators.any { !it.overlayPath.isNullOrBlank() }) {
            tab = 4
        }
        val keys = result?.priorityKeys.orEmpty().ifEmpty {
            result?.metrics?.sortedByDescending { it.score }?.take(3)?.map { it.key }.orEmpty()
        }
        guides = vm.guidesFor(keys)
        products = vm.productsFor(keys)
    }

    val p = patient
    val r = result
    val s = session
    if (loadFailed) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Informe no encontrado",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(4.dp),
                ) { Text("Volver") }
            }
        }
        return
    }
    if (p == null || r == null || s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        return
    }

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
                Text("Informe · ${p.displayName}", style = MaterialTheme.typography.headlineMedium)
                Text(
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")).format(Date(s.createdAt)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.55f),
                )
                Text(
                    "Motor: ${r.analysisEngine}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (r.isClinicalLicensed) Accent else Color(0xFFB71C1C),
                )
                if (!r.isClinicalLicensed) {
                    Text(
                        "NO CLÍNICO — simulación / Demo",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFB71C1C),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        runCatching {
                            val (text, pdf, _) = withContext(Dispatchers.IO) {
                                vm.buildSharePayload(p, r, s.moisturePercent, s.createdAt, s)
                            }
                            ReportSharer.shareEmail(
                                context,
                                subject = "Informe de piel — ${p.fullName} · Dra. MLH",
                                body = text,
                                pdf = pdf,
                                toEmail = p.email.ifBlank { null },
                            )
                        }.onFailure {
                            vm.showUserMessage("No se pudo abrir Email: ${it.message ?: "error"}")
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(4.dp),
            ) {
                Icon(Icons.Outlined.Email, null)
                Spacer(Modifier.width(4.dp))
                Text("Email")
            }
            Button(
                onClick = {
                    scope.launch {
                        runCatching {
                            val (text, pdf, _) = withContext(Dispatchers.IO) {
                                vm.buildSharePayload(p, r, s.moisturePercent, s.createdAt, s)
                            }
                            ReportSharer.shareWhatsAppBusiness(
                                context,
                                body = text.take(900),
                                pdf = pdf,
                                phoneE164 = p.phoneRaw.ifBlank { p.phone }.ifBlank { null },
                            )
                        }.onFailure {
                            vm.showUserMessage("No se pudo abrir WhatsApp: ${it.message ?: "error"}")
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(4.dp),
            ) {
                Icon(Icons.Outlined.Share, null)
                Spacer(Modifier.width(4.dp))
                Text("WhatsApp")
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Resumen", "Superficial", "Profunda", "Proporciones", "Mapas", "Cuidado").forEachIndexed { i, label ->
                val selected = tab == i
                Button(
                    onClick = { tab = i },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) Accent else Cream,
                        contentColor = if (selected) Color.White else Ink,
                    ),
                    shape = RoundedCornerShape(4.dp),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (images.isNotEmpty()) {
            Text("Fotos capturadas (8 espectros)", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(images.entries.toList()) { (name, path) ->
                    val bmp = remember(path) {
                        runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                    }
                    Column(
                        Modifier
                            .width(120.dp)
                            .background(Cream, RoundedCornerShape(4.dp))
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(108.dp)
                                    .background(Ink),
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(108.dp)
                                    .background(Ink.copy(alpha = 0.1f)),
                            )
                        }
                        Text(name, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Cream, RoundedCornerShape(4.dp))
                .padding(12.dp),
        ) {
            when (tab) {
                0 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(
                            "Motor: ${r.analysisEngine}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (r.isClinicalLicensed) Accent else Color(0xFFB71C1C),
                        )
                        if (!r.isClinicalLicensed) {
                            Text(
                                "Informe NO clínico — simulación / Demo. No usar cifras como medición real.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFB71C1C),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        val chronological = s.ageAtAnalysis.takeIf { it > 0 } ?: p.ageAt(s.createdAt)
                        val delta = r.skinAge - chronological
                        Text(
                            "Edad cutánea: ${r.skinAge} años",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Accent,
                        )
                        Text(
                            "Edad real al análisis: $chronological años · " +
                                when {
                                    delta > 0 -> "aparenta +$delta años (estimación cosmética)"
                                    delta < 0 -> "aparenta ${-delta} años menos (estimación cosmética)"
                                    else -> "alineada con la edad cronológica"
                                },
                            style = MaterialTheme.typography.bodyLarge,
                            color = when {
                                delta > 2 -> Color(0xFFB71C1C)
                                delta < -2 -> Color(0xFF2E7D32)
                                else -> Ink.copy(alpha = 0.7f)
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Tipo de piel: ${r.skinType}", style = MaterialTheme.typography.titleLarge)
                        s.moisturePercent?.let {
                            Text("Humedad: ${"%.1f".format(it)}%", style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(r.overview, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(10.dp))
                        Text("Prioridades", style = MaterialTheme.typography.titleLarge)
                        Text(s.recommendations, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Análisis cosmético offline. No sustituye diagnóstico dermatológico.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink.copy(alpha = 0.5f),
                        )
                        if (oemIndicators.any { !it.overlayPath.isNullOrBlank() }) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { tab = 4 },
                                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Ver mapas sobre la cara (deslizar)") }
                        }
                    }
                }
                1 -> {
                    val surface = r.metrics.filter {
                        it.layer.equals("superficial", true) || it.layer.equals("surface", true)
                    }
                    if (surface.isEmpty()) {
                        EmptyTabMessage("Sin datos de capa superficial en este informe.")
                    } else {
                        MetricList(surface)
                    }
                }
                2 -> {
                    val deep = r.metrics.filter {
                        it.layer.equals("profunda", true) || it.layer.equals("deep", true)
                    }
                    if (deep.isEmpty()) {
                        EmptyTabMessage("Sin datos de capa profunda en este informe.")
                    } else {
                        MetricList(deep)
                    }
                }
                3 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text("Proporciones faciales (offline)", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(r.facialRatioNote, style = MaterialTheme.typography.bodyLarge)
                        r.facial?.let { f ->
                            Spacer(Modifier.height(8.dp))
                            Text(f.summary, style = MaterialTheme.typography.bodyMedium, color = Accent)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Tercios: ${(f.upperThird * 100).toInt()}% / ${(f.middleThird * 100).toInt()}% / ${(f.lowerThird * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "Ancho ≈ ${"%.1f".format(f.eyeUnits)} ojos · simetría ${(f.symmetryScore * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        } ?: Text(
                            "Sin datos de proporciones en este informe.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink.copy(alpha = 0.55f),
                        )
                    }
                }
                4 -> OemMapViewer(
                    indicators = oemIndicators,
                    sessionDir = s.sessionDir,
                    selected = selectedMapIndex,
                    onSelect = { selectedMapIndex = it },
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text("Guías de cuidado (catálogo local)", style = MaterialTheme.typography.titleLarge)
                    }
                    items(guides) { g ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Paper, RoundedCornerShape(4.dp))
                                .padding(12.dp),
                        ) {
                            Text(g.title, style = MaterialTheme.typography.titleLarge)
                            Text(g.body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text("Productos sugeridos (catálogo local)", style = MaterialTheme.typography.titleLarge)
                    }
                    items(products) { p ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Paper, RoundedCornerShape(4.dp))
                                .padding(12.dp),
                        ) {
                            Text(p.name, style = MaterialTheme.typography.titleLarge)
                            Text("${p.category} · ${p.description}", style = MaterialTheme.typography.bodyMedium)
                            if (p.howToUse.isNotBlank()) {
                                Text("Uso: ${p.howToUse}", style = MaterialTheme.typography.bodyMedium, color = Accent)
                            }
                        }
                    }
                    if (guides.isEmpty() && products.isEmpty()) {
                        item {
                            Text(
                                "Sin guías para las prioridades actuales.",
                                color = Ink.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OemMapViewer(
    indicators: List<OemIndicatorResult>,
    sessionDir: String,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    if (indicators.isEmpty()) {
        Text(
            "Mapas faciales no disponibles. Este informe usó el motor heurístico de respaldo " +
                "(faltaron archivos OEM, landmarks o libsalon). Vuelva a capturar las 8 luces.",
            color = Ink.copy(alpha = 0.55f),
        )
        return
    }
    val withMaps = indicators.filter { !it.overlayPath.isNullOrBlank() || !it.blackOverlayPath.isNullOrBlank() }
    val list = withMaps.ifEmpty { indicators }
    var reveal by remember(selected) { mutableFloatStateOf(0.55f) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "Deslice el dedo → para marcar manchas/poros sobre la cara; ← para ver solo la foto.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list.size) { i ->
                    val ind = list[i]
                    val isSel = i == selected
                    Button(
                        onClick = {
                            onSelect(i)
                            reveal = 0.55f
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSel) Accent else Paper,
                            contentColor = if (isSel) Color.White else Ink,
                        ),
                        shape = RoundedCornerShape(4.dp),
                    ) { Text(ind.displayName, maxLines = 1) }
                }
            }
        }
        item {
            val ind = list.getOrNull(selected.coerceIn(0, (list.size - 1).coerceAtLeast(0))) ?: return@item
            Text(
                "${ind.displayName} · score ${ind.score} · nivel ${ind.levelLabel}",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(6.dp))
            val overlayPath = ind.overlayPath ?: ind.blackOverlayPath
            val baseName = OemIndicatorCatalog.baseCaptureFilename(ind.oemType)
            val basePath = when {
                sessionDir.isNotBlank() && File(sessionDir, baseName).exists() ->
                    File(sessionDir, baseName).absolutePath
                sessionDir.isNotBlank() && File(sessionDir, OemCaptureFiles.WHITE).exists() ->
                    File(sessionDir, OemCaptureFiles.WHITE).absolutePath
                else -> null
            }
            val baseBmp = remember(basePath) {
                basePath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
            }
            val overlayBmp = remember(overlayPath) {
                overlayPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
            }
            if (baseBmp != null || overlayBmp != null) {
                FaceRevealMap(
                    base = baseBmp,
                    overlay = overlayBmp,
                    reveal = reveal,
                    onRevealChange = { reveal = it },
                    label = ind.displayName,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (reveal < 0.05f) "Cara completa (sin marcas)"
                    else if (reveal > 0.95f) "Marcas al 100%"
                    else "Marcas visibles: ${(reveal * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Accent,
                )
                Slider(
                    value = reveal,
                    onValueChange = { reveal = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = Accent,
                    ),
                )
            } else {
                Text(
                    "Mapa no generado en disco para ${ind.oemType}.",
                    color = Ink.copy(alpha = 0.5f),
                )
            }
        }
    }
}

/**
 * OEM-style wipe: finger left→right reveals indicator marks on the face;
 * right→left hides them back to the clean capture.
 */
@Composable
private fun FaceRevealMap(
    base: android.graphics.Bitmap?,
    overlay: android.graphics.Bitmap?,
    reveal: Float,
    onRevealChange: (Float) -> Unit,
    label: String,
) {
    val density = LocalDensity.current
    val revealState = rememberUpdatedState(reveal)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(360.dp)
            .background(Ink)
            .border(1.dp, Ink.copy(alpha = 0.2f))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    onRevealChange((revealState.value + dragAmount / w).coerceIn(0f, 1f))
                }
            },
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val clipReveal = remember(reveal, widthPx) {
            GenericShape { size, _ ->
                addRect(Rect(0f, 0f, size.width * reveal.coerceIn(0f, 1f), size.height))
            }
        }
        if (base != null) {
            Image(
                bitmap = base.asImageBitmap(),
                contentDescription = "Foto base",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (overlay != null && reveal > 0.01f) {
            Image(
                bitmap = overlay.asImageBitmap(),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(clipReveal),
            )
        } else if (overlay != null && base == null) {
            Image(
                bitmap = overlay.asImageBitmap(),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Wipe handle
        Box(
            Modifier
                .fillMaxHeight()
                .width(2.dp)
                .offset(x = maxWidth * reveal)
                .background(Paper.copy(alpha = 0.9f))
                .align(Alignment.CenterStart),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = maxWidth * reveal - 12.dp)
                .size(24.dp)
                .background(Accent, RoundedCornerShape(12.dp)),
        )
        Text(
            "← cara  |  marcas →",
            color = Paper.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp)
                .background(Ink.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun EmptyTabMessage(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = Ink.copy(alpha = 0.55f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun MetricList(metrics: List<SkinMetric>) {
    if (metrics.isEmpty()) {
        EmptyTabMessage("Sin datos para esta capa.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(metrics) { m ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Paper, RoundedCornerShape(4.dp))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(
                        "N${m.level.value}",
                        color = Color(android.graphics.Color.parseColor(m.level.colorHex)),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                LinearProgressIndicator(
                    progress = { m.score / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    color = Color(android.graphics.Color.parseColor(m.level.colorHex)),
                )
                Text(m.description, style = MaterialTheme.typography.bodyMedium)
                Text("Recomendación: ${m.recommendation}", style = MaterialTheme.typography.bodyMedium, color = Accent)
            }
        }
    }
}

@Composable
fun SessionListScreen(
    patientId: Long,
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onCompare: () -> Unit,
    onDelete: (AnalysisSession) -> Unit,
) {
    var sessions by remember { mutableStateOf<List<AnalysisSession>>(emptyList()) }
    var patient by remember { mutableStateOf<Patient?>(null) }
    LaunchedEffect(patientId) {
        patient = vm.getPatient(patientId)
        vm.observeSessions(patientId).collectLatest { sessions = it }
    }
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
            Text(
                "Historial · ${patient?.displayName ?: ""}",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
        }
        patient?.let { p ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(4.dp))
                    .padding(14.dp),
            ) {
                Text(p.displayName, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${p.currentAge()} años · ${p.sexLabel} · nasc. ${p.birthDate}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink.copy(alpha = 0.7f),
                )
                if (p.phoneRaw.isNotBlank()) {
                    Text(p.phoneRaw, style = MaterialTheme.typography.bodyMedium)
                }
                if (p.address.isNotBlank()) {
                    Text(p.address, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.55f))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = onNew,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(4.dp),
        ) { Text("NUEVO ANÁLISIS") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCompare,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            enabled = sessions.size >= 2,
        ) { Text("Comparar dos sesiones") }
        Spacer(Modifier.height(12.dp))
        Text("Historial de análisis", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (sessions.isEmpty()) {
            Text("Sin análisis previos.", color = Ink.copy(alpha = 0.55f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions) { sessionItem ->
                    val ageLabel = sessionItem.ageAtAnalysis.takeIf { it > 0 }
                        ?: patient?.ageAt(sessionItem.createdAt)
                        ?: 0
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Cream, RoundedCornerShape(4.dp))
                            .padding(14.dp),
                    ) {
                        Text(
                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")).format(Date(sessionItem.createdAt)),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "$ageLabel años · ${sessionItem.skinType} · edad cutánea ${sessionItem.skinAge}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onOpen(sessionItem.id) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                shape = RoundedCornerShape(4.dp),
                            ) { Text("Ver informe") }
                            OutlinedButton(
                                onClick = { onDelete(sessionItem) },
                                shape = RoundedCornerShape(4.dp),
                            ) { Text("Borrar") }
                        }
                    }
                }
            }
        }
    }
}

package com.mlh.skinanalyzer.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.mlh.skinanalyzer.analysis.SkinAnalysisResult
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
    var tab by remember { mutableStateOf(0) } // 0 resumen, 1 superficial, 2 profunda

    LaunchedEffect(sessionId) {
        val s = vm.getSession(sessionId) ?: return@LaunchedEffect
        session = s
        patient = vm.getPatient(s.patientId)
        result = runCatching {
            Gson().fromJson(s.metricsJson, SkinAnalysisResult::class.java)
        }.getOrNull() ?: vm.lastResult
        images = runCatching {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            Gson().fromJson<Map<String, String>>(s.imagePathsJson, type) ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    val p = patient
    val r = result
    val s = session
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
                Text("Informe · ${p.name}", style = MaterialTheme.typography.headlineMedium)
                Text(
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")).format(Date(s.createdAt)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.55f),
                )
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val (text, pdf) = withContext(Dispatchers.IO) {
                            vm.buildSharePayload(p, r, s.moisturePercent, s.createdAt)
                        }
                        ReportSharer.shareEmail(
                            context,
                            subject = "Informe de piel — ${p.name} · Dra. MLH",
                            body = text,
                            pdf = pdf,
                            toEmail = p.email.ifBlank { null },
                        )
                    }
                },
                shape = RoundedCornerShape(4.dp),
            ) {
                Icon(Icons.Outlined.Email, null)
                Spacer(Modifier.width(6.dp))
                Text("Email")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        val (text, pdf) = withContext(Dispatchers.IO) {
                            vm.buildSharePayload(p, r, s.moisturePercent, s.createdAt)
                        }
                        ReportSharer.shareWhatsAppBusiness(
                            context,
                            body = text.take(900),
                            pdf = pdf,
                            phoneE164 = p.phone.ifBlank { null },
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(4.dp),
            ) {
                Icon(Icons.Outlined.Share, null)
                Spacer(Modifier.width(6.dp))
                Text("WhatsApp Business")
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Resumen", "Superficial", "Profunda").forEachIndexed { i, label ->
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

        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .background(Cream, RoundedCornerShape(4.dp))
                    .padding(14.dp),
            ) {
                when (tab) {
                    0 -> {
                        Text("Tipo de piel: ${r.skinType}", style = MaterialTheme.typography.titleLarge)
                        Text("Edad cutánea: ${r.skinAge} años", style = MaterialTheme.typography.titleLarge, color = Accent)
                        s.moisturePercent?.let {
                            Text("Humedad: ${"%.1f".format(it)}%", style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(r.overview, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(10.dp))
                        Text("Prioridades", style = MaterialTheme.typography.titleLarge)
                        Text(s.recommendations, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(r.facialRatioNote, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.6f))
                    }
                    1 -> MetricList(r.metrics.filter { it.layer == "superficial" })
                    else -> MetricList(r.metrics.filter { it.layer == "profunda" })
                }
            }

            Column(
                Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
            ) {
                Text("Imágenes espectrales", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(images.entries.toList()) { (name, path) ->
                        val bmp = remember(path) { BitmapFactory.decodeFile(path) }
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
                Spacer(Modifier.height(12.dp))
                Text(
                    "Niveles de cuidado: 1 verde (leve) → 5 rojo (grave).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun MetricList(metrics: List<SkinMetric>) {
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
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
            }
            Text(
                "Historial · ${patient?.name ?: ""}",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onNew,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(4.dp),
            ) { Text("Nuevo análisis") }
        }
        Spacer(Modifier.height(12.dp))
        if (sessions.isEmpty()) {
            Text("Sin análisis previos.", color = Ink.copy(alpha = 0.55f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions) { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Cream, RoundedCornerShape(4.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")).format(Date(s.createdAt)),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "${s.skinType} · edad cutánea ${s.skinAge}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Button(
                            onClick = { onOpen(s.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(4.dp),
                        ) { Text("Ver informe") }
                    }
                }
            }
        }
    }
}

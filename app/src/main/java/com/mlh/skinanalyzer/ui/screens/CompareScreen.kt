package com.mlh.skinanalyzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mlh.skinanalyzer.data.AnalysisSession
import com.mlh.skinanalyzer.ui.AppViewModel
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.Cream
import com.mlh.skinanalyzer.ui.theme.Ink
import com.mlh.skinanalyzer.ui.theme.Paper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun CompareScreen(
    patientId: Long,
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    var sessions by remember { mutableStateOf<List<AnalysisSession>>(emptyList()) }
    var leftId by remember { mutableStateOf<Long?>(null) }
    var rightId by remember { mutableStateOf<Long?>(null) }
    var compare by remember { mutableStateOf<AppViewModel.SessionCompare?>(null) }
    val df = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")) }

    LaunchedEffect(patientId) {
        sessions = vm.listSessions(patientId)
    }
    LaunchedEffect(leftId, rightId) {
        val l = leftId
        val r = rightId
        compare = if (l != null && r != null && l != r) vm.compareSessions(l, r) else null
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
                "Comparar historial",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Elige dos sesiones guardadas en esta tablet (sin nube).",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(10.dp))
        if (sessions.size < 2) {
            Text(
                "Se necesitan al menos 2 análisis del paciente para comparar.",
                color = Ink.copy(alpha = 0.55f),
            )
            return
        }
        Text("Antes (izquierda)", style = MaterialTheme.typography.titleLarge)
        SessionPicker(sessions, leftId, df) { leftId = it }
        Spacer(Modifier.height(8.dp))
        Text("Después (derecha)", style = MaterialTheme.typography.titleLarge)
        SessionPicker(sessions, rightId, df) { rightId = it }
        Spacer(Modifier.height(12.dp))

        val c = compare
        if (c == null) {
            Text("Selecciona dos fechas distintas.", color = Ink.copy(alpha = 0.5f))
        } else {
            Text(
                "${df.format(Date(c.left.createdAt))}  →  ${df.format(Date(c.right.createdAt))}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Edad cutánea: ${c.left.skinAge} → ${c.right.skinAge}  ·  ${c.left.skinType} → ${c.right.skinType}",
                style = MaterialTheme.typography.bodyMedium,
                color = Accent,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(c.deltas) { d ->
                    val improved = d.delta < -0.5f
                    val worsened = d.delta > 0.5f
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Cream, RoundedCornerShape(4.dp))
                            .padding(12.dp),
                    ) {
                        Row {
                            Text(d.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            Text(
                                when {
                                    improved -> "↓ ${"%.1f".format(abs(d.delta))}"
                                    worsened -> "↑ ${"%.1f".format(d.delta)}"
                                    else -> "≈"
                                },
                                color = when {
                                    improved -> Color(0xFF2E9E6A)
                                    worsened -> Color(0xFFC0392B)
                                    else -> Ink.copy(alpha = 0.5f)
                                },
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        Text(
                            "${"%.0f".format(d.leftScore)} → ${"%.0f".format(d.rightScore)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LinearProgressIndicator(
                            progress = { (d.rightScore / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            color = Accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionPicker(
    sessions: List<AnalysisSession>,
    selected: Long?,
    df: SimpleDateFormat,
    onSelect: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        sessions.take(12).forEach { s ->
            val isSel = s.id == selected
            Button(
                onClick = { onSelect(s.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSel) Accent else Cream,
                    contentColor = if (isSel) Color.White else Ink,
                ),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text("${df.format(Date(s.createdAt))} · ${s.skinType} · ${s.skinAge}a")
            }
        }
    }
}

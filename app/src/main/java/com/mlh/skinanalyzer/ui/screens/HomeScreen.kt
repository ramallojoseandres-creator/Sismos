package com.mlh.skinanalyzer.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mlh.skinanalyzer.R
import com.mlh.skinanalyzer.data.AnalysisSession
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.Cream
import com.mlh.skinanalyzer.ui.theme.Ink
import com.mlh.skinanalyzer.ui.theme.Paper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onNewAnalysis: () -> Unit,
    onPatients: () -> Unit,
    onSettings: () -> Unit,
    onOpenSession: (Long) -> Unit,
    recentSessions: List<AnalysisSession>,
    clinicName: String,
    hardwareStatus: String,
    onRefreshHardware: () -> Unit,
    demoMode: Boolean = false,
    patientNameFor: (Long) -> String? = { null },
    appVersion: String = "",
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )
    val df = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Paper, Cream.copy(alpha = 0.85f), Paper),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Ajustes")
                }
            }
            Image(
                painter = painterResource(R.drawable.logo_mlh),
                contentDescription = "Logo MLH",
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .alpha(0.9f + glow * 0.1f),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                clinicName.ifBlank { "Dra. María Laura Hernández" },
                style = MaterialTheme.typography.displayLarge,
                color = Ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .width(96.dp)
                    .height(2.dp)
                    .background(Ink.copy(alpha = 0.7f)),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Skin Analyzer Pro",
                style = MaterialTheme.typography.headlineMedium,
                color = Accent,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Análisis de piel en consulta",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            if (demoMode) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Modo de prueba activo. Desactívelo en Admin para la consulta real.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Cream, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onPatients,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.People, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Pacientes")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "El análisis se inicia desde el perfil del paciente.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            if (recentSessions.isNotEmpty()) {
                Text(
                    "Últimos análisis",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                recentSessions.take(5).forEach { s ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Cream, RoundedCornerShape(4.dp))
                            .clickable { onOpenSession(s.id) }
                            .padding(12.dp),
                    ) {
                        Text(
                            patientNameFor(s.patientId) ?: "Paciente #${s.patientId}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(df.format(Date(s.createdAt)), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${s.skinType} · edad cutánea ${s.skinAge}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink.copy(alpha = 0.6f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    hardwareStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefreshHardware) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Reconectar equipo")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

package com.mlh.skinanalyzer.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mlh.skinanalyzer.R
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.Cream
import com.mlh.skinanalyzer.ui.theme.Gold
import com.mlh.skinanalyzer.ui.theme.Ink
import com.mlh.skinanalyzer.ui.theme.Paper

@Composable
fun HomeScreen(
    onNewAnalysis: () -> Unit,
    onPatients: () -> Unit,
    hardwareStatus: String,
    onRefreshHardware: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )
    val slide by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "slide",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        Paper,
                        Cream.copy(alpha = 0.9f),
                        Paper,
                    ),
                ),
            ),
    ) {
        // Atmospheric soft bands
        Box(
            Modifier
                .fillMaxWidth(0.55f)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .alpha(0.35f + glow * 0.2f)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Accent.copy(alpha = 0.0f),
                            Accent.copy(alpha = 0.08f + slide * 0.05f),
                            Gold.copy(alpha = 0.10f),
                        ),
                    ),
                ),
        )

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "Dra María Laura Hernández",
                        style = MaterialTheme.typography.displayLarge,
                        color = Ink,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .width(120.dp)
                            .height(2.dp)
                            .background(Ink.copy(alpha = 0.7f)),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Skin Analyzer Pro",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Accent,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Analizador MJ-008 Maokin Miaojin · uso personal, sin inicio de sesión.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ink.copy(alpha = 0.75f),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onNewAnalysis,
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Icon(Icons.Outlined.Face, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Nuevo análisis AI")
                    }
                    OutlinedButton(
                        onClick = onPatients,
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(48.dp),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Icon(Icons.Outlined.People, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Pacientes e historial")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            hardwareStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink.copy(alpha = 0.55f),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onRefreshHardware) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Reintentar MJ-008")
                        }
                    }
                }
            }

            Box(
                Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
                    .padding(start = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxSize(0.92f)
                        .border(1.dp, Ink.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                        .background(Paper)
                        .padding(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.logo_mlh),
                        contentDescription = "Logo MLH",
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .alpha(0.92f + glow * 0.08f),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

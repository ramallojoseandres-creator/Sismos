package com.mlh.skinanalyzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.Cream
import com.mlh.skinanalyzer.ui.theme.Ink
import com.mlh.skinanalyzer.ui.theme.Paper

@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onConfig: () -> Unit,
    onAdmin: () -> Unit,
) {
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
            Text("Ajustes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        HubCard(
            title = "Configuración",
            subtitle = "Consultorio, informe e indicadores",
            icon = { Icon(Icons.Outlined.Tune, null, tint = Accent) },
            onClick = onConfig,
        )
        Spacer(Modifier.height(12.dp))
        HubCard(
            title = "Admin",
            subtitle = "Cámara, equipo, licencia y herramientas",
            icon = { Icon(Icons.Outlined.AdminPanelSettings, null, tint = Ink.copy(alpha = 0.55f)) },
            onClick = onAdmin,
            discreet = true,
        )
    }
}

@Composable
private fun HubCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    discreet: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Cream, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = if (discreet) Ink.copy(alpha = 0.65f) else Ink,
            )
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.55f))
        }
    }
}

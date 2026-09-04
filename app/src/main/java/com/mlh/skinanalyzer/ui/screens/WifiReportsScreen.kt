package com.mlh.skinanalyzer.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlh.skinanalyzer.ui.AppViewModel
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.Cream
import com.mlh.skinanalyzer.ui.theme.Ink
import com.mlh.skinanalyzer.ui.theme.Paper

@Composable
fun WifiReportsScreen(
    vm: AppViewModel,
    highlightSessionId: Long?,
    onBack: () -> Unit,
) {
    DisposableEffect(highlightSessionId) {
        vm.startWifiReportsSession()
        onDispose { vm.stopWifiReportsSession() }
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
            Text("Editar informes en PC", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(12.dp))
        val url = vm.wifiReportsUrl
        val pin = vm.wifiReportsPin
        if (url == null || pin == null) {
            Text(
                vm.wifiReportsServerError ?: "Conecta la tablet a una red WiFi",
                color = androidx.compose.ui.graphics.Color(0xFFB71C1C),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text("En su PC (misma red WiFi), abra:", style = MaterialTheme.typography.bodyMedium)
            val openUrl = if (highlightSessionId != null && highlightSessionId > 0) {
                "$url?sessionId=$highlightSessionId"
            } else {
                url
            }
            Text(
                openUrl,
                style = MaterialTheme.typography.headlineLarge,
                color = Accent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(4.dp))
                    .padding(16.dp),
                fontSize = 22.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text("PIN de esta sesión", style = MaterialTheme.typography.labelLarge)
            Text(
                pin,
                style = MaterialTheme.typography.displayLarge,
                color = Ink,
                letterSpacing = 6.sp,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "La IA genera el análisis automático. Usted escribe medicamentos y rutinas " +
                    "con su criterio. Guarde en la tablet y descargue copia HTML o PDF a su PC.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.65f),
            )
            if (highlightSessionId != null && highlightSessionId > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Este enlace abre directamente el informe actual.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Accent,
                )
            }
            vm.wifiReportsServerError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = androidx.compose.ui.graphics.Color(0xFFB71C1C))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "El servidor solo está activo en esta pantalla. Datos de salud: red local únicamente.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.copy(alpha = 0.55f),
        )
    }
}

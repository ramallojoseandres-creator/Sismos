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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlh.skinanalyzer.data.PendingPatientImport
import com.mlh.skinanalyzer.ui.AppViewModel
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.Cream
import com.mlh.skinanalyzer.ui.theme.Ink
import com.mlh.skinanalyzer.ui.theme.Paper

data class WifiImportPreview(
    val item: PendingPatientImport,
    val existingName: String?,
)

@Composable
fun WifiImportScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    var pending by remember { mutableStateOf<List<PendingPatientImport>>(emptyList()) }
    var preview by remember { mutableStateOf<List<WifiImportPreview>?>(null) }
    var status by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        vm.startWifiImportSession()
        onDispose { vm.stopWifiImportSession() }
    }

    LaunchedEffect(Unit) {
        vm.observePendingImports().collect { pending = it }
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
            Text("Cargar fichas por WiFi", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(12.dp))
        val url = vm.wifiUrl
        val pin = vm.wifiPin
        if (url == null || pin == null) {
            Text(
                vm.wifiServerError ?: "Conecta la tablet a una red WiFi",
                color = androidx.compose.ui.graphics.Color(0xFFB71C1C),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text("En el iPhone o PC, abra:", style = MaterialTheme.typography.bodyMedium)
            Text(
                url,
                style = MaterialTheme.typography.headlineLarge,
                color = Accent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(4.dp))
                    .padding(16.dp),
                fontSize = 26.sp,
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
            Text(
                "El servidor solo está activo en esta pantalla. Datos de salud: red local únicamente.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.55f),
            )
            vm.wifiServerError?.let {
                Text(it, color = androidx.compose.ui.graphics.Color(0xFFB71C1C))
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { preview = vm.previewPendingImports(pending) },
            enabled = pending.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(4.dp),
        ) { Text("Traer fichas (${pending.size} pendientes)") }

        val review = preview
        if (review != null) {
            Spacer(Modifier.height(12.dp))
            Text("Revisión antes de confirmar", style = MaterialTheme.typography.titleLarge)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(review) { row ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Cream, RoundedCornerShape(4.dp))
                            .padding(12.dp),
                    ) {
                        Text(
                            "${row.item.lastName}, ${row.item.firstName}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "${row.item.birthDate} · ${row.item.phoneRaw}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            if (row.existingName != null) {
                                "Actualizar ficha existente: ${row.existingName}"
                            } else {
                                "Nueva ficha"
                            },
                            color = if (row.existingName != null) Accent else Ink.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Button(
                onClick = {
                    vm.confirmPendingImports(review) { n ->
                        status = "Importadas $n fichas"
                        preview = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(4.dp),
            ) { Text("Confirmar e importar a la base local") }
            OutlinedButton(
                onClick = { preview = null },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
            ) { Text("Cancelar revisión") }
        } else {
            Spacer(Modifier.weight(1f))
            if (pending.isNotEmpty()) {
                Text(
                    "${pending.size} fichas en cola (aún no confirmadas).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (status.isNotBlank()) {
            Text(status, color = Accent)
        }
    }
}

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mlh.skinanalyzer.data.ClinicProfile
import com.mlh.skinanalyzer.data.IndicatorPref
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.Cream
import com.mlh.skinanalyzer.ui.theme.Ink
import com.mlh.skinanalyzer.ui.theme.Paper

private val indicatorLabels = mapOf(
    "sebum" to "Sebo / grasa",
    "pores" to "Poros dilatados",
    "pigmentation" to "Manchas",
    "wrinkles" to "Arrugas",
    "acne" to "Acné",
    "blackheads" to "Puntos negros",
    "dark_circles" to "Ojeras",
    "moisture" to "Hidratación",
    "texture" to "Textura",
    "sensitivity" to "Sensibilidad",
    "uv_spots" to "Manchas UV",
    "deep_pigment" to "Pigmento profundo",
    "deep_acne" to "Acné profundo",
    "collagen" to "Colágeno",
)

@Composable
fun SettingsScreen(
    clinic: ClinicProfile,
    indicators: List<IndicatorPref>,
    hardwareStatus: String,
    hardwareDiagnostics: String = "",
    onBack: () -> Unit,
    onSaveClinic: (ClinicProfile) -> Unit,
    onToggleIndicator: (String, Boolean) -> Unit,
    onRefreshHardware: () -> Unit,
) {
    var name by remember(clinic) { mutableStateOf(clinic.clinicName) }
    var doctor by remember(clinic) { mutableStateOf(clinic.doctorName) }
    var specialty by remember(clinic) { mutableStateOf(clinic.specialty) }
    var phone by remember(clinic) { mutableStateOf(clinic.phone) }
    var email by remember(clinic) { mutableStateOf(clinic.email) }
    var whatsapp by remember(clinic) { mutableStateOf(clinic.whatsapp) }
    var address by remember(clinic) { mutableStateOf(clinic.address) }
    var footer by remember(clinic) { mutableStateOf(clinic.footerNote) }
    var saved by remember { mutableStateOf(false) }

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
            Text("Ajustes · offline", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Sin login ni servidores chinos. Pacientes, informes y catálogo viven en esta tablet (Room).",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(4.dp))
            Text("Consultorio", style = MaterialTheme.typography.titleLarge, color = Accent)
            Field("Nombre del consultorio", name) { name = it }
            Field("Doctora / profesional", doctor) { doctor = it }
            Field("Especialidad", specialty) { specialty = it }
            Field("Teléfono", phone) { phone = it }
            Field("WhatsApp", whatsapp) { whatsapp = it }
            Field("Email", email) { email = it }
            Field("Dirección", address) { address = it }
            Field("Pie de informe", footer) { footer = it }
            Button(
                onClick = {
                    onSaveClinic(
                        clinic.copy(
                            clinicName = name.trim(),
                            doctorName = doctor.trim(),
                            specialty = specialty.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            whatsapp = whatsapp.trim(),
                            address = address.trim(),
                            footerNote = footer.trim(),
                        ),
                    )
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(4.dp),
            ) { Text(if (saved) "Guardado" else "Guardar consultorio") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Indicadores visibles en informes", style = MaterialTheme.typography.titleLarge, color = Accent)
            Text(
                "Equivalente a la configuración de indicadores del software OEM, sin nube.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.55f),
            )
            indicators.forEach { pref ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Cream, RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        indicatorLabels[pref.key] ?: pref.key,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = pref.enabled,
                        onCheckedChange = { onToggleIndicator(pref.key, it) },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Hardware MJ-008", style = MaterialTheme.typography.titleLarge, color = Accent)
            Text(hardwareStatus, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.65f))
            Button(
                onClick = onRefreshHardware,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) { Text("Reconectar luces / USB") }
            if (hardwareDiagnostics.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Diagnóstico USB / serie", style = MaterialTheme.typography.titleLarge)
                Text(
                    hardwareDiagnostics,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.7f),
                )
                Text(
                    "Si hay 2 USB (Dual USB camera ON): la app elige la del analizador (USB3.0 / USB Camera). " +
                        "Acepte el permiso USB al capturar. Menú eng: Screen Rotation 270, Camera Rotation 0.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.5f),
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Sustituye: login, tienda en la nube, Aliyun OSS, ai.aiskin.vip y device.aiskin.vip.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.45f),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = label != "Pie de informe" && label != "Dirección",
        minLines = if (label == "Pie de informe") 2 else 1,
        shape = RoundedCornerShape(4.dp),
    )
}

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
import com.mlh.skinanalyzer.ui.theme.RaisedButton

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
fun ConfigScreen(
    clinic: ClinicProfile,
    indicators: List<IndicatorPref>,
    onBack: () -> Unit,
    onSaveClinic: (ClinicProfile) -> Unit,
    onToggleIndicator: (String, Boolean) -> Unit,
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
            Text("Configuración", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Consultorio", style = MaterialTheme.typography.titleLarge, color = Accent)
            Text(
                "Datos que aparecen en el informe del paciente.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.55f),
            )
            Field("Nombre del consultorio", name) { name = it; saved = false }
            Field("Doctora / profesional", doctor) { doctor = it; saved = false }
            Field("Especialidad", specialty) { specialty = it; saved = false }
            Field("Teléfono", phone) { phone = it; saved = false }
            Field("WhatsApp", whatsapp) { whatsapp = it; saved = false }
            Field("Email", email) { email = it; saved = false }
            Field("Dirección", address) { address = it; saved = false }
            Field("Pie de informe", footer) { footer = it; saved = false }
            RaisedButton(
                text = if (saved) "Guardado" else "Guardar consultorio",
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
            )

            Spacer(Modifier.height(12.dp))
            Text("Informe · indicadores", style = MaterialTheme.typography.titleLarge, color = Accent)
            Text(
                "Elija qué mediciones mostrar en el informe.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.55f),
            )
            indicators.forEach { pref ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Cream, RoundedCornerShape(10.dp))
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
        shape = RoundedCornerShape(10.dp),
    )
}

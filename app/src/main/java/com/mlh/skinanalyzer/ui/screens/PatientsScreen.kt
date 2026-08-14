package com.mlh.skinanalyzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mlh.skinanalyzer.data.Patient
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.Cream
import com.mlh.skinanalyzer.ui.theme.Ink
import com.mlh.skinanalyzer.ui.theme.Paper

@Composable
fun PatientsScreen(
    patients: List<Patient>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onAnalyze: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Patient) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
            }
            Text("Pacientes", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(4.dp),
            ) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Nuevo")
            }
        }
        Spacer(Modifier.height(12.dp))
        if (patients.isEmpty()) {
            Text(
                "Aún no hay pacientes. Crea uno para iniciar el análisis.",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink.copy(alpha = 0.6f),
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(patients, key = { it.id }) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Cream, RoundedCornerShape(4.dp))
                            .clickable { onOpen(p.id) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${p.gender} · ${p.age} años" +
                                    if (p.phone.isNotBlank()) " · ${p.phone}" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ink.copy(alpha = 0.65f),
                            )
                        }
                        IconButton(onClick = { onEdit(p.id) }) {
                            Icon(Icons.Outlined.Edit, "Editar")
                        }
                        IconButton(onClick = { onOpen(p.id) }) {
                            Icon(Icons.Outlined.History, "Historial")
                        }
                        Button(
                            onClick = { onAnalyze(p.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Icon(Icons.Outlined.Face, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Analizar")
                        }
                        IconButton(onClick = { onDelete(p) }) {
                            Icon(Icons.Outlined.Delete, "Eliminar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PatientFormScreen(
    existing: Patient?,
    onBack: () -> Unit,
    onSave: (Patient, startCapture: Boolean) -> Unit,
) {
    var name = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(existing?.name ?: "") }
    var gender = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(existing?.gender ?: "Femenino") }
    var age = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(existing?.age?.toString() ?: "") }
    var phone = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(existing?.phone ?: "") }
    var email = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(existing?.email ?: "") }
    var notes = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(existing?.notes ?: "") }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
            }
            Text(
                if (existing == null) "Nueva ficha de paciente" else "Editar paciente",
                style = MaterialTheme.typography.headlineLarge,
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Nombre completo", name.value) { name.value = it }
                Field("Edad (obligatoria para mejor precisión)", age.value) { age.value = it.filter { c -> c.isDigit() }.take(3) }
                Field("Teléfono / WhatsApp", phone.value) { phone.value = it }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Sexo", gender.value) { gender.value = it }
                Field("Email", email.value) { email.value = it }
                Field("Notas", notes.value) { notes.value = it }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    val a = age.value.toIntOrNull() ?: return@OutlinedButton
                    if (name.value.isBlank()) return@OutlinedButton
                    onSave(
                        Patient(
                            id = existing?.id ?: 0,
                            name = name.value.trim(),
                            gender = gender.value.trim().ifBlank { "Femenino" },
                            age = a,
                            phone = phone.value.trim(),
                            email = email.value.trim(),
                            notes = notes.value.trim(),
                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        ),
                        false,
                    )
                },
                shape = RoundedCornerShape(4.dp),
            ) { Text("Guardar ficha") }
            Button(
                onClick = {
                    val a = age.value.toIntOrNull() ?: return@Button
                    if (name.value.isBlank()) return@Button
                    onSave(
                        Patient(
                            id = existing?.id ?: 0,
                            name = name.value.trim(),
                            gender = gender.value.trim().ifBlank { "Femenino" },
                            age = a,
                            phone = phone.value.trim(),
                            email = email.value.trim(),
                            notes = notes.value.trim(),
                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        ),
                        true,
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(4.dp),
            ) { Text("Guardar y capturar") }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Ink.copy(alpha = 0.6f))
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
        )
    }
}

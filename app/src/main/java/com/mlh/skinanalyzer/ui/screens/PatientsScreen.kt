package com.mlh.skinanalyzer.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    searchQuery: String,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onAnalyze: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Patient) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Patient?>(null) }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar paciente") },
            text = {
                Text("¿Eliminar a ${target.name} y todo su historial? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target)
                        pendingDelete = null
                    },
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            },
        )
    }

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
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar nombre, teléfono o email") },
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
        )
        Spacer(Modifier.height(12.dp))
        if (patients.isEmpty()) {
            Text(
                if (searchQuery.isBlank()) {
                    "Aún no hay pacientes. Crea uno para iniciar el análisis."
                } else {
                    "Sin resultados para “$searchQuery”."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Ink.copy(alpha = 0.6f),
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(patients, key = { it.id }) { p ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Cream, RoundedCornerShape(4.dp))
                            .padding(14.dp),
                    ) {
                        Text(p.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${p.gender} · ${p.age} años" +
                                if (p.phone.isNotBlank()) " · ${p.phone}" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink.copy(alpha = 0.65f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { onAnalyze(p.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Icon(Icons.Outlined.Face, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Analizar")
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { onOpen(p.id) },
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Outlined.History, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Historial")
                            }
                            OutlinedButton(
                                onClick = { onEdit(p.id) },
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Outlined.Edit, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Editar")
                            }
                            IconButton(onClick = { pendingDelete = p }) {
                                Icon(Icons.Outlined.Delete, "Eliminar")
                            }
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
    val name = remember { mutableStateOf(existing?.name ?: "") }
    val gender = remember { mutableStateOf(existing?.gender ?: "Femenino") }
    val age = remember { mutableStateOf(existing?.age?.toString() ?: "") }
    val phone = remember { mutableStateOf(existing?.phone ?: "") }
    val email = remember { mutableStateOf(existing?.email ?: "") }
    val notes = remember { mutableStateOf(existing?.notes ?: "") }

    fun buildPatient(): Patient? {
        val a = age.value.toIntOrNull() ?: return null
        if (name.value.isBlank()) return null
        return Patient(
            id = existing?.id ?: 0,
            name = name.value.trim(),
            gender = gender.value.trim().ifBlank { "Femenino" },
            age = a,
            phone = phone.value.trim(),
            email = email.value.trim(),
            notes = notes.value.trim(),
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
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
                if (existing == null) "Nueva ficha" else "Editar paciente",
                style = MaterialTheme.typography.headlineLarge,
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Field("Nombre completo", name.value) { name.value = it }
            Field("Edad (obligatoria)", age.value) { age.value = it.filter { c -> c.isDigit() }.take(3) }
            Field("Sexo", gender.value) { gender.value = it }
            Field("Teléfono / WhatsApp", phone.value) { phone.value = it }
            Field("Email", email.value) { email.value = it }
            Field("Notas", notes.value) { notes.value = it }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { buildPatient()?.let { onSave(it, false) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
        ) { Text("Guardar ficha") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { buildPatient()?.let { onSave(it, true) } },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(4.dp),
        ) { Text("Guardar y capturar") }
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

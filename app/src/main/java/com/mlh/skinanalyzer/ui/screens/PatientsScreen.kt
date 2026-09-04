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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mlh.skinanalyzer.data.Patient
import com.mlh.skinanalyzer.data.PatientAge
import com.mlh.skinanalyzer.data.PatientSex
import com.mlh.skinanalyzer.data.PhoneNormalizer
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.Cream
import com.mlh.skinanalyzer.ui.theme.Ink
import com.mlh.skinanalyzer.ui.theme.Paper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PatientListRow(
    val patient: Patient,
    val sessionCount: Int,
    val lastSessionAt: Long?,
)

@Composable
fun PatientsScreen(
    rows: List<PatientListRow>,
    searchQuery: String,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onWifiImport: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Patient) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Patient?>(null) }
    val df = remember { SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")) }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar paciente") },
            text = {
                Text("¿Eliminar a ${target.displayName} y todo su historial? Esta acción no se puede deshacer.")
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
            IconButton(onClick = onWifiImport) {
                Icon(Icons.Outlined.Wifi, contentDescription = "Cargar por WiFi")
            }
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
            label = { Text("Buscar apellido, nombre o teléfono") },
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
        )
        Spacer(Modifier.height(12.dp))
        if (rows.isEmpty()) {
            Text(
                if (searchQuery.isBlank()) {
                    "Aún no hay pacientes. Crea una ficha o cárgalas por WiFi."
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
                items(rows, key = { it.patient.id }) { row ->
                    val p = row.patient
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Cream, RoundedCornerShape(4.dp))
                            .clickable { onOpenProfile(p.id) }
                            .padding(14.dp),
                    ) {
                        Text(p.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${p.currentAge()} años · ${p.sexLabel}" +
                                if (p.phoneRaw.isNotBlank()) " · ${p.phoneRaw}" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink.copy(alpha = 0.65f),
                        )
                        Text(
                            buildString {
                                append(
                                    if (row.lastSessionAt != null) {
                                        "Último análisis: ${df.format(Date(row.lastSessionAt))}"
                                    } else {
                                        "Sin análisis aún"
                                    },
                                )
                                append(" · ${row.sessionCount} análisis")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { onOpenProfile(p.id) },
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f),
                            ) { Text("Abrir perfil") }
                            OutlinedButton(
                                onClick = { onEdit(p.id) },
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Icon(Icons.Outlined.Edit, null)
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
    duplicateHint: Patient? = null,
    onBack: () -> Unit,
    onOpenExisting: (Long) -> Unit = {},
    onClearDuplicate: () -> Unit = {},
    onCheckPhone: (String) -> Unit = {},
    onSave: (Patient) -> Unit,
) {
    var firstName by remember(existing?.id) { mutableStateOf(existing?.firstName ?: "") }
    var lastName by remember(existing?.id) { mutableStateOf(existing?.lastName ?: "") }
    var birthDate by remember(existing?.id) { mutableStateOf(existing?.birthDate ?: "") }
    var phoneRaw by remember(existing?.id) { mutableStateOf(existing?.phoneRaw?.ifBlank { existing.phone } ?: "") }
    var sex by remember(existing?.id) {
        mutableStateOf(PatientSex.fromCode(existing?.sex ?: "F"))
    }
    var address by remember(existing?.id) { mutableStateOf(existing?.address ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(phoneRaw) {
        if (phoneRaw.length >= 7) onCheckPhone(phoneRaw)
    }

    fun buildPatient(): Patient? {
        if (firstName.isBlank() || lastName.isBlank()) {
            error = "Nombre y apellido son obligatorios."
            return null
        }
        if (!PatientAge.isValidBirthDate(birthDate)) {
            error = "Fecha de nacimiento inválida (use AAAA-MM-DD, no futura)."
            return null
        }
        val normalized = PhoneNormalizer.normalize(phoneRaw)
        if (normalized.length < 7) {
            error = "Teléfono obligatorio (mínimo 7 dígitos)."
            return null
        }
        error = null
        return Patient(
            id = existing?.id ?: 0,
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            birthDate = birthDate.trim(),
            phone = normalized,
            phoneRaw = phoneRaw.trim(),
            sex = sex.code,
            address = address.trim(),
            email = existing?.email.orEmpty(),
            notes = existing?.notes.orEmpty(),
            photoPath = existing?.photoPath.orEmpty(),
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
            Field("Nombre", firstName) { firstName = it }
            Field("Apellido", lastName) { lastName = it }
            Field("Fecha de nacimiento (AAAA-MM-DD)", birthDate) { birthDate = it }
            if (PatientAge.isValidBirthDate(birthDate)) {
                Text(
                    "Edad actual: ${PatientAge.yearsAt(birthDate)} años",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Accent,
                )
            }
            Text("Sexo", style = MaterialTheme.typography.labelLarge, color = Ink.copy(alpha = 0.6f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PatientSex.entries.forEach { option ->
                    FilterChip(
                        selected = sex == option,
                        onClick = { sex = option },
                        label = { Text(option.label) },
                    )
                }
            }
            Field("Teléfono", phoneRaw) { phoneRaw = it }
            Field("Dirección (opcional)", address) { address = it }
            error?.let {
                Text(it, color = androidx.compose.ui.graphics.Color(0xFFB71C1C))
            }
            duplicateHint?.let { dup ->
                if (existing == null || dup.id != existing.id) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Cream, RoundedCornerShape(4.dp))
                            .padding(12.dp),
                    ) {
                        Text(
                            "Ya existe una ficha con este teléfono: ${dup.displayName}.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onOpenExisting(dup.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Abrir ficha existente") }
                        TextButton(onClick = onClearDuplicate) { Text("Seguir editando") }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (duplicateHint != null && (existing == null || duplicateHint.id != existing.id)) {
                    error = "Use la ficha existente o cambie el teléfono."
                    return@Button
                }
                buildPatient()?.let(onSave)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(4.dp),
        ) { Text("Guardar ficha") }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Ink.copy(alpha = 0.6f))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
        )
    }
}
